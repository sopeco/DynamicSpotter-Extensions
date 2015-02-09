package org.spotter.ext.detection.edc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.aim.api.exceptions.InstrumentationException;
import org.aim.api.exceptions.MeasurementException;
import org.aim.api.measurement.AbstractRecord;
import org.aim.api.measurement.dataset.Dataset;
import org.aim.api.measurement.dataset.DatasetCollection;
import org.aim.api.measurement.dataset.Parameter;
import org.aim.api.measurement.dataset.ParameterSelection;
import org.aim.artifacts.probes.ResponsetimeProbe;
import org.aim.artifacts.probes.SQLQueryProbe;
import org.aim.artifacts.probes.StackTraceProbe;
import org.aim.artifacts.probes.ThreadTracingProbe;
import org.aim.artifacts.records.ResponseTimeRecord;
import org.aim.artifacts.records.SQLQueryRecord;
import org.aim.artifacts.records.StackTraceRecord;
import org.aim.artifacts.records.ThreadTracingRecord;
import org.aim.artifacts.scopes.EntryPointScope;
import org.aim.artifacts.scopes.JDBCScope;
import org.aim.description.InstrumentationDescription;
import org.aim.description.builder.InstrumentationDescriptionBuilder;
import org.lpe.common.config.GlobalConfiguration;
import org.lpe.common.extension.IExtension;
import org.lpe.common.util.LpeNumericUtils;
import org.lpe.common.util.LpeStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spotter.core.ProgressManager;
import org.spotter.core.detection.AbstractDetectionController;
import org.spotter.core.detection.AbstractDetectionExtension;
import org.spotter.core.detection.IDetectionController;
import org.spotter.core.detection.IExperimentReuser;
import org.spotter.core.workload.LoadConfig;
import org.spotter.exceptions.WorkloadException;
import org.spotter.ext.detection.edc.utils.MethodCall;
import org.spotter.ext.detection.edc.utils.MethodCallSet;
import org.spotter.shared.configuration.ConfigKeys;
import org.spotter.shared.result.model.SpotterResult;
import org.spotter.shared.status.DiagnosisStatus;

public class EDCDetectionController extends AbstractDetectionController implements IExperimentReuser {

	private static final Logger LOGGER = LoggerFactory.getLogger(EDCDetectionController.class);

	private static final String KEY_EXPERIMENT_NAME = "experimentName";

	private static final String NAME_SINGLE_USER_EXP = "singleUserExp";
	private static final String NAME_MAIN_EXP = "mainExp";
	private static final String NAME_STACK_TRACE_EXP = "stackTraceExp";
	private static final String NAME_HIERARCHY_EXP = "hierarchyExp";

	private boolean reuser = false;
	private double instrumentationGranularity;
	private double perfReqThreshold;
	private double perfReqConfidence;
	private double perfReqRelativeQueryRT;
	private double perfReqRelativeQueryRTDiff;

	public EDCDetectionController(IExtension<IDetectionController> provider) {
		super(provider);
	}

	@Override
	public void loadProperties() {
		reuser = Boolean.parseBoolean(this.getProblemDetectionConfiguration().getProperty(
				AbstractDetectionExtension.REUSE_EXPERIMENTS_FROM_PARENT, "false"));

		String sGranularity = getProblemDetectionConfiguration().getProperty(
				EDCExtension.INSTRUMENTATION_GRANULARITY_KEY,
				String.valueOf(EDCExtension.INSTRUMENTATION_GRANULARITY_DEFAULT));

		if (!sGranularity.matches("0|1|0.[0-9]+")) {
			instrumentationGranularity = EDCExtension.INSTRUMENTATION_GRANULARITY_DEFAULT;
		} else {
			instrumentationGranularity = Double.parseDouble(sGranularity);
		}

		perfReqThreshold = GlobalConfiguration.getInstance().getPropertyAsDouble(
				ConfigKeys.PERFORMANCE_REQUIREMENT_THRESHOLD, ConfigKeys.DEFAULT_PERFORMANCE_REQUIREMENT_THRESHOLD);
		perfReqConfidence = GlobalConfiguration.getInstance().getPropertyAsDouble(
				ConfigKeys.PERFORMANCE_REQUIREMENT_CONFIDENCE, ConfigKeys.DEFAULT_PERFORMANCE_REQUIREMENT_CONFIDENCE);

	}

	@Override
	public long getExperimentSeriesDuration() {
		if (reuser) {
			return 0;
		} else {
			return Integer.parseInt(LpeStringUtils.getPropertyOrFail(GlobalConfiguration.getInstance().getProperties(),
					ConfigKeys.WORKLOAD_MAXUSERS, null)) * 4;
		}
	}

	@Override
	public void executeExperiments() throws InstrumentationException, MeasurementException, WorkloadException {
		if (!reuser) {
			int maxUsers = Integer.parseInt(LpeStringUtils.getPropertyOrFail(GlobalConfiguration.getInstance()
					.getProperties(), ConfigKeys.WORKLOAD_MAXUSERS, null));

			instrumentApplication(getServletHierarchyDescription());
			runExperiment(this, 1, NAME_HIERARCHY_EXP);
			uninstrumentApplication();

			instrumentApplication(getMainInstrumentationDescription(false));
			runExperiment(this, 1, NAME_SINGLE_USER_EXP);
			uninstrumentApplication();

			instrumentApplication(getMainInstrumentationDescription(true));
			runExperiment(this, maxUsers, NAME_MAIN_EXP);
			uninstrumentApplication();

			instrumentApplication(getStackTraceInstrDescription());
			runExperiment(this, 1, NAME_STACK_TRACE_EXP);
			uninstrumentApplication();
		}
	}

	@Override
	protected SpotterResult analyze(DatasetCollection data) {
		SpotterResult result = new SpotterResult();

		ParameterSelection selectHierarchyExp = new ParameterSelection()
				.select(KEY_EXPERIMENT_NAME, NAME_HIERARCHY_EXP);
		ParameterSelection selectSingleUserExp = new ParameterSelection().select(KEY_EXPERIMENT_NAME,
				NAME_SINGLE_USER_EXP);
		ParameterSelection selectMultiUserExp = new ParameterSelection().select(KEY_EXPERIMENT_NAME, NAME_MAIN_EXP);
		ParameterSelection selectStackTraceExp = new ParameterSelection().select(KEY_EXPERIMENT_NAME,
				NAME_STACK_TRACE_EXP);
		Dataset rtDataset = data.getDataSet(ResponseTimeRecord.class);

		if (rtDataset == null || rtDataset.size() == 0) {
			result.setDetected(false);
			result.addMessage("Instrumentation achieved no response time results for the given scope!");
			return result;
		}

		Dataset hierarchyResponseTimes = selectHierarchyExp.applyTo(rtDataset);
		Dataset singleUserResponseTimes = selectSingleUserExp.applyTo(rtDataset);
		Dataset multiUserResponseTimes = selectMultiUserExp.applyTo(rtDataset);
		rtDataset = null;

		Dataset sqlDataset = data.getDataSet(SQLQueryRecord.class);
		preprocessQueries(sqlDataset);

		if (sqlDataset == null || sqlDataset.size() == 0) {
			result.setDetected(false);
			result.addMessage("Instrumentation achieved no query results for the given scope!");
			return result;
		}

		Dataset singleUserQueries = selectSingleUserExp.applyTo(sqlDataset);
		Dataset multiUserQueries = selectMultiUserExp.applyTo(sqlDataset);
		Dataset stackTraceQueries = selectStackTraceExp.applyTo(sqlDataset);
		sqlDataset = null;

		Dataset ttDataset = data.getDataSet(ThreadTracingRecord.class);

		if (ttDataset == null || ttDataset.size() == 0) {
			result.setDetected(false);
			result.addMessage("Instrumentation achieved no thread tracing results for the given scope!");
			return result;
		}

		Dataset singleUserThreadTracing = selectSingleUserExp.applyTo(ttDataset);
		Dataset multiUserThreadTracing = selectMultiUserExp.applyTo(ttDataset);
		ttDataset = null;

		Dataset stDataset = data.getDataSet(StackTraceRecord.class);

		if (stDataset == null || stDataset.size() == 0) {
			result.setDetected(false);
			result.addMessage("Instrumentation achieved no stack trace results for the given scope!");
			return result;
		}

		Dataset stackTraces = selectStackTraceExp.applyTo(stDataset);
		stDataset = null;

		// Select servlets with requirements violating response times

		MethodCallSet servletHierarchy = getMethodCallSetOfMethods(extractUniqueMethodNames(hierarchyResponseTimes),
				multiUserResponseTimes, multiUserThreadTracing);
		MethodCallSet servletQueryHierarchy = servletHierarchy.getSubsetOfLowestLayer();
		addQueriesToMethodCallSet(servletQueryHierarchy, multiUserResponseTimes, multiUserQueries,
				multiUserThreadTracing);

		Set<String> criticalServlets = new TreeSet<>();

		for (String methodName : servletQueryHierarchy.getUniqueMethods()) {
			Set<MethodCall> callsOfMethod = servletQueryHierarchy.getCallsOfMethodAtLayer(methodName, 0);
			int numberOfReqViolatingCalls = 0;

			for (MethodCall call : callsOfMethod) {
				if (call.getResponseTime() >= perfReqThreshold) {
					numberOfReqViolatingCalls++;
				}
			}

			if ((double) numberOfReqViolatingCalls / (double) callsOfMethod.size() > 1.0 - perfReqConfidence) {
				criticalServlets.add(methodName);
			}
		}

		// Select queries with requirements violating relative response time
		Set<String> possiblyCriticalQueries = servletQueryHierarchy.getUniqueMethodsOfLayer(1);
		Map<String, Double> violatingReqQueriesART = new TreeMap<>();

		for (String query : possiblyCriticalQueries) {
			List<Double> relativeRTs = getRelativeQueryResponseTimes(query, servletQueryHierarchy);

			int numberOfReqViolatingCalls = 0;

			for (double responseTime : relativeRTs) {
				if (responseTime > perfReqRelativeQueryRT) {
					numberOfReqViolatingCalls++;
				}
			}

			if (numberOfReqViolatingCalls / relativeRTs.size() > 1.0 - perfReqConfidence) {
				violatingReqQueriesART.put(query, LpeNumericUtils.average(relativeRTs));
			} else {
				for (MethodCall queryCall : servletQueryHierarchy.getCallsOfMethodAtLayer(query, 1)) {
					servletQueryHierarchy.removeCall(queryCall);
				}
			}
		}

		// Drop queries having a bigger relative response time for one user than
		// for many users

		MethodCallSet singleUserServletQueryHierarchy = servletHierarchy.getSubsetOfLowestLayer();
		addQueriesToMethodCallSet(singleUserServletQueryHierarchy, singleUserResponseTimes, singleUserQueries,
				singleUserThreadTracing);

		for (String query : violatingReqQueriesART.keySet()) {
			List<Double> singleUserRRTs = getRelativeQueryResponseTimes(query, singleUserServletQueryHierarchy);
			double avgRRTDiff = violatingReqQueriesART.get(query) - LpeNumericUtils.average(singleUserRRTs);

			if (avgRRTDiff < perfReqRelativeQueryRTDiff) {
				violatingReqQueriesART.remove(query);

				for (MethodCall queryCall : servletQueryHierarchy.getCallsOfMethodAtLayer(query, 1)) {
					servletQueryHierarchy.removeCall(queryCall);
				}
			}
		}

		for (MethodCall call : servletQueryHierarchy.getMethodCalls()) {
			if (call.getCalledOperations().size() == 0) {
				servletQueryHierarchy.removeCall(call);
			}
		}

		// Create SpotterResult and add stack traces

		result.setDetected(false);
		for (String servlet : servletQueryHierarchy.getUniqueMethodsOfLayer(0)) {
			result.setDetected(true);
			StringBuilder messageBuilder = new StringBuilder();
			messageBuilder.append("EDC detected in service: ");
			messageBuilder.append(servlet);
			messageBuilder.append("\nQueries are:");

			for (String query : violatingReqQueriesART.keySet()) {
				double relativeRT = violatingReqQueriesART.get(query);

				messageBuilder.append("\n");
				messageBuilder.append(query);
				messageBuilder.append("\n\tAverage relative response time: ");
				messageBuilder.append(relativeRT);
				messageBuilder.append(" ms)");
				messageBuilder.append("\n\tStack trace:");

				for (String stackTraceString : getStackTracesOfQuery(query, stackTraceQueries, stackTraces)) {
					if (stackTraceString.contains(servlet)) {
						for (String stackTraceElement : stackTraceString
								.split(StackTraceProbe.REGEX_DELIM_STACK_TRACE_ELEMENT)) {
							messageBuilder.append("\n\t\t");
							messageBuilder.append(stackTraceElement);
						}

						break;
					}
				}
			}

			result.addMessage(messageBuilder.toString());
		}

		return result;
	}

	private void preprocessQueries(Dataset queries) {
		for (SQLQueryRecord record : queries.getRecords(SQLQueryRecord.class)) {
			record.setQueryString(LpeStringUtils.getGeneralizedQuery(record.getQueryString()));
		}
	}

	private Set<String> extractUniqueMethodNames(Dataset responseTimes) {
		Set<String> uniqueNames = new TreeSet<>();

		for (ResponseTimeRecord rtRecord : responseTimes.getRecords(ResponseTimeRecord.class)) {
			uniqueNames.add(rtRecord.getOperation());
		}

		return uniqueNames;
	}

	private MethodCallSet getMethodCallSetOfMethods(Set<String> methodNames, Dataset responseTimes,
			Dataset threadTracing) {
		MethodCallSet servletCallSet = new MethodCallSet();

		for (ResponseTimeRecord rtRec : responseTimes.getRecords(ResponseTimeRecord.class)) {
			if (methodNames.contains(rtRec.getOperation())) {
				Dataset ttSet = selectCallID(rtRec.getCallId()).applyTo(threadTracing);
				if (ttSet == null) {
					continue;
				}
				long threadId = ttSet.getRecords(ThreadTracingRecord.class).get(0).getThreadId();
				servletCallSet.addCall(new MethodCall(rtRec.getOperation(), rtRec.getTimeStamp(), rtRec.getTimeStamp()
						+ rtRec.getResponseTime(), threadId));
			}
		}

		return servletCallSet;
	}

	private void addQueriesToMethodCallSet(MethodCallSet set, Dataset responseTimes, Dataset queries,
			Dataset threadTracing) {
		for (SQLQueryRecord sqlRecord : queries.getRecords(SQLQueryRecord.class)) {
			Dataset querySet = selectCallID(sqlRecord.getCallId()).applyTo(responseTimes);
			if (querySet == null) {
				continue;
			}
			ResponseTimeRecord rtRecord = querySet.getRecords(ResponseTimeRecord.class).get(0);

			Dataset ttSet = selectCallID(sqlRecord.getCallId()).applyTo(threadTracing);
			if (ttSet == null) {
				continue;
			}
			ThreadTracingRecord ttRecord = ttSet.getRecords(ThreadTracingRecord.class).get(0);

			set.addCallIfNested(new MethodCall(sqlRecord.getQueryString(), rtRecord.getTimeStamp(), rtRecord
					.getTimeStamp() + rtRecord.getResponseTime(), ttRecord.getThreadId()));
		}
	}

	private ParameterSelection selectCallID(long callId) {
		return new ParameterSelection().select(AbstractRecord.PAR_CALL_ID, callId);
	}

	private Set<String> getStackTracesOfQuery(String query, Dataset queries, Dataset stackTraces) {
		Set<String> stackTraceSet = new TreeSet<>();

		ParameterSelection selectQuery = new ParameterSelection().select(SQLQueryRecord.PAR_QUERY_STRING, query);
		List<SQLQueryRecord> queryRecords = selectQuery.applyTo(queries).getRecords(SQLQueryRecord.class);

		for (SQLQueryRecord queryRecord : queryRecords) {
			Dataset stSet = selectCallID(queryRecord.getCallId()).applyTo(stackTraces);
			if (stSet == null) {
				continue;
			}
			StackTraceRecord stackTraceRecord = stSet.getRecords(StackTraceRecord.class).get(0);
			stackTraceSet.add(stackTraceRecord.getStackTrace());
		}

		return stackTraceSet;
	}

	private List<Double> getRelativeQueryResponseTimes(String query, MethodCallSet servletQueryHierarchy) {
		List<Double> relativeResponseTimes = new ArrayList<>();

		for (MethodCall servletCall : servletQueryHierarchy.getMethodCalls()) {
			for (MethodCall queryCall : servletCall.getCalledOperations()) {
				if (queryCall.getOperation().equals(query)) {
					relativeResponseTimes.add((double) queryCall.getResponseTime()
							/ (double) servletCall.getResponseTime());
				}
			}
		}

		return relativeResponseTimes;
	}

	@Override
	public InstrumentationDescription getInstrumentationDescription() {
		return getMainInstrumentationDescription(true);
	}

	private InstrumentationDescription getMainInstrumentationDescription(boolean useGranularity) {
		InstrumentationDescriptionBuilder idBuilder = new InstrumentationDescriptionBuilder();
		idBuilder.newAPIScopeEntity(EntryPointScope.class.getName()).addProbe(ResponsetimeProbe.MODEL_PROBE)
				.addProbe(ThreadTracingProbe.MODEL_PROBE).entityDone();
		idBuilder.newAPIScopeEntity(JDBCScope.class.getName()).addProbe(SQLQueryProbe.MODEL_PROBE)
				.addProbe(ResponsetimeProbe.MODEL_PROBE).addProbe(ThreadTracingProbe.MODEL_PROBE).entityDone();

		if (useGranularity) {
			idBuilder.newGlobalRestriction().setGranularity(instrumentationGranularity).restrictionDone();
		}

		return idBuilder.build();
	}

	private InstrumentationDescription getStackTraceInstrDescription() {
		InstrumentationDescriptionBuilder idBuilder = new InstrumentationDescriptionBuilder();
		idBuilder.newAPIScopeEntity(JDBCScope.class.getName()).addProbe(StackTraceProbe.MODEL_PROBE)
				.addProbe(SQLQueryProbe.MODEL_PROBE).entityDone();

		return idBuilder.build();
	}

	private InstrumentationDescription getServletHierarchyDescription() {
		InstrumentationDescriptionBuilder idBuilder = new InstrumentationDescriptionBuilder();
		idBuilder.newAPIScopeEntity(EntryPointScope.class.getName()).addProbe(ResponsetimeProbe.MODEL_PROBE)
				.entityDone();

		return idBuilder.build();
	}

	private void runExperiment(IDetectionController detectionController, int numUsers, String experimentName)
			throws WorkloadException, MeasurementException {
		LOGGER.info("{} detection controller started experiment with {} users ...", detectionController.getProvider()
				.getName(), numUsers);
		ProgressManager.getInstance().updateProgressStatus(getProblemId(), DiagnosisStatus.EXPERIMENTING_RAMP_UP);
		LoadConfig lConfig = new LoadConfig();
		lConfig.setNumUsers(numUsers);
		lConfig.setRampUpIntervalLength(GlobalConfiguration.getInstance().getPropertyAsInteger(
				ConfigKeys.EXPERIMENT_RAMP_UP_INTERVAL_LENGTH));
		lConfig.setRampUpUsersPerInterval(GlobalConfiguration.getInstance().getPropertyAsInteger(
				ConfigKeys.EXPERIMENT_RAMP_UP_NUM_USERS_PER_INTERVAL));
		lConfig.setCoolDownIntervalLength(GlobalConfiguration.getInstance().getPropertyAsInteger(
				ConfigKeys.EXPERIMENT_COOL_DOWN_INTERVAL_LENGTH));
		lConfig.setCoolDownUsersPerInterval(GlobalConfiguration.getInstance().getPropertyAsInteger(
				ConfigKeys.EXPERIMENT_COOL_DOWN_NUM_USERS_PER_INTERVAL));
		lConfig.setExperimentDuration(GlobalConfiguration.getInstance().getPropertyAsInteger(
				ConfigKeys.EXPERIMENT_DURATION));
		getWorkloadAdapter().startLoad(lConfig);

		getWorkloadAdapter().waitForWarmupPhaseTermination();

		ProgressManager.getInstance().updateProgressStatus(getProblemId(), DiagnosisStatus.EXPERIMENTING_STABLE_PHASE);
		getMeasurementController().enableMonitoring();

		getWorkloadAdapter().waitForExperimentPhaseTermination();

		ProgressManager.getInstance().updateProgressStatus(getProblemId(), DiagnosisStatus.EXPERIMENTING_COOL_DOWN);
		getMeasurementController().disableMonitoring();

		getWorkloadAdapter().waitForFinishedLoad();

		ProgressManager.getInstance().updateProgressStatus(getProblemId(), DiagnosisStatus.COLLECTING_DATA);
		LOGGER.info("Storing data ...");
		long dataCollectionStart = System.currentTimeMillis();
		Parameter experimentNameParameter = new Parameter(KEY_EXPERIMENT_NAME, experimentName);
		Set<Parameter> parameters = new TreeSet<>();
		parameters.add(experimentNameParameter);
		getResultManager().storeResults(parameters, getMeasurementController());
		ProgressManager.getInstance()
				.addAdditionalDuration((System.currentTimeMillis() - dataCollectionStart) / SECOND);
		LOGGER.info("Data stored!");
	}

}
