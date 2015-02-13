/**
 * Copyright 2014 SAP AG
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.spotter.ext.detection.edc.strategies;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.aim.api.measurement.AbstractRecord;
import org.aim.api.measurement.dataset.Dataset;
import org.aim.api.measurement.dataset.DatasetCollection;
import org.aim.api.measurement.dataset.ParameterSelection;
import org.aim.artifacts.probes.StackTraceProbe;
import org.aim.artifacts.records.ResponseTimeRecord;
import org.aim.artifacts.records.SQLQueryRecord;
import org.aim.artifacts.records.StackTraceRecord;
import org.aim.artifacts.records.ThreadTracingRecord;
import org.lpe.common.config.GlobalConfiguration;
import org.lpe.common.util.LpeNumericUtils;
import org.lpe.common.util.LpeStringUtils;
import org.lpe.common.util.NumericPair;
import org.lpe.common.util.NumericPairList;
import org.spotter.core.chartbuilder.AnalysisChartBuilder;
import org.spotter.ext.detection.edc.EDCDetectionController;
import org.spotter.ext.detection.edc.IEDCAnalysisStrategy;
import org.spotter.ext.detection.edc.utils.MethodCall;
import org.spotter.ext.detection.edc.utils.MethodCallSet;
import org.spotter.shared.configuration.ConfigKeys;
import org.spotter.shared.result.model.SpotterResult;

public class RelativeQueryRTStrategy implements IEDCAnalysisStrategy {

	private EDCDetectionController controller;

	private double perfReqThreshold;
	private double perfReqConfidence;
	private double perfReqRelativeQueryRT;
	private double perfReqRelativeQueryRTDiff;
	private long numUsers;

	private Dataset hierarchyResponseTimes;
	private Dataset singleUserResponseTimes;
	private Dataset multiUserResponseTimes;
	private Dataset singleUserQueries;
	private Dataset multiUserQueries;
	private Dataset stackTraceQueries;
	private Dataset singleUserThreadTracing;
	private Dataset multiUserThreadTracing;
	private Dataset stackTraces;

	boolean validData = false;
	String invalidDataMessage = "";

	@Override
	public void init(Properties problemDetectionConfiguration, EDCDetectionController controller) {
		this.controller = controller;

		perfReqThreshold = GlobalConfiguration.getInstance().getPropertyAsDouble(
				ConfigKeys.PERFORMANCE_REQUIREMENT_THRESHOLD, ConfigKeys.DEFAULT_PERFORMANCE_REQUIREMENT_THRESHOLD);
		perfReqConfidence = GlobalConfiguration.getInstance().getPropertyAsDouble(
				ConfigKeys.PERFORMANCE_REQUIREMENT_CONFIDENCE, ConfigKeys.DEFAULT_PERFORMANCE_REQUIREMENT_CONFIDENCE);

		numUsers = GlobalConfiguration.getInstance().getPropertyAsLong(ConfigKeys.WORKLOAD_MAXUSERS);

	}

	@Override
	public void setMeasurementData(DatasetCollection data) {
		validData = false;

		ParameterSelection selectHierarchyExp = new ParameterSelection().select(
				EDCDetectionController.KEY_EXPERIMENT_NAME, EDCDetectionController.NAME_HIERARCHY_EXP);
		ParameterSelection selectSingleUserExp = new ParameterSelection().select(
				EDCDetectionController.KEY_EXPERIMENT_NAME, EDCDetectionController.NAME_SINGLE_USER_EXP);
		ParameterSelection selectMultiUserExp = new ParameterSelection().select(
				EDCDetectionController.KEY_EXPERIMENT_NAME, EDCDetectionController.NAME_MAIN_EXP);
		ParameterSelection selectStackTraceExp = new ParameterSelection().select(
				EDCDetectionController.KEY_EXPERIMENT_NAME, EDCDetectionController.NAME_STACK_TRACE_EXP);
		Dataset rtDataset = data.getDataSet(ResponseTimeRecord.class);

		if (rtDataset == null || rtDataset.size() == 0) {
			invalidDataMessage = "Instrumentation achieved no response time results for the given scope!";
			return;
		}

		hierarchyResponseTimes = selectHierarchyExp.applyTo(rtDataset);
		singleUserResponseTimes = selectSingleUserExp.applyTo(rtDataset);
		multiUserResponseTimes = selectMultiUserExp.applyTo(rtDataset);

		Dataset sqlDataset = data.getDataSet(SQLQueryRecord.class);
		for (SQLQueryRecord record : sqlDataset.getRecords(SQLQueryRecord.class)) {
			record.setQueryString(LpeStringUtils.getGeneralizedQuery(record.getQueryString()));
		}

		if (sqlDataset == null || sqlDataset.size() == 0) {
			invalidDataMessage = "Instrumentation achieved no query results for the given scope!";
			return;
		}

		singleUserQueries = selectSingleUserExp.applyTo(sqlDataset);
		multiUserQueries = selectMultiUserExp.applyTo(sqlDataset);
		stackTraceQueries = selectStackTraceExp.applyTo(sqlDataset);

		Dataset ttDataset = data.getDataSet(ThreadTracingRecord.class);

		if (ttDataset == null || ttDataset.size() == 0) {
			invalidDataMessage = "Instrumentation achieved no thread tracing results for the given scope!";
			return;
		}

		singleUserThreadTracing = selectSingleUserExp.applyTo(ttDataset);
		multiUserThreadTracing = selectMultiUserExp.applyTo(ttDataset);

		Dataset stDataset = data.getDataSet(StackTraceRecord.class);

		if (stDataset == null || stDataset.size() == 0) {
			invalidDataMessage = "Instrumentation achieved no stack trace results for the given scope!";
			return;
		}

		stackTraces = selectStackTraceExp.applyTo(stDataset);

		validData = true;
	}

	@Override
	public SpotterResult analyze() {
		SpotterResult result = new SpotterResult();

		if (!validData) {
			result.setDetected(false);
			result.addMessage(invalidDataMessage);
			return result;
		}

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
		for (String servletMethod : servletQueryHierarchy.getUniqueMethodsOfLayer(0)) {
			result.setDetected(true);
			StringBuilder messageBuilder = new StringBuilder();
			messageBuilder.append("EDC detected in service: ");
			messageBuilder.append(servletMethod);
			messageBuilder.append("\nQueries are:");

			for (String query : violatingReqQueriesART.keySet()) {

				createCharts(servletMethod, query, numUsers,
						getServletResponseTimesOverTime(servletMethod, multiUserResponseTimes),
						getQueryResponseTimesOverTime(query, multiUserResponseTimes, multiUserQueries),
						getServletResponseTimesOverTime(servletMethod, singleUserResponseTimes),
						getQueryResponseTimesOverTime(query, singleUserResponseTimes, singleUserQueries), result);

				double relativeRT = violatingReqQueriesART.get(query);
				List<Double> singleUserRRTs = getRelativeQueryResponseTimes(query, singleUserServletQueryHierarchy);
				double singleUserART = LpeNumericUtils.average(singleUserRRTs);

				messageBuilder.append("\n");
				messageBuilder.append(query);
				messageBuilder.append("\n\tAverage relative response time with ");
				messageBuilder.append(numUsers);
				messageBuilder.append(": ");
				messageBuilder.append(relativeRT);
				messageBuilder.append("\n\tAverage relative response time with 1 user: ");
				messageBuilder.append(singleUserART);
				messageBuilder.append(" ms)");
				messageBuilder.append("\n\tStack trace:");

				String formattedServlet = LpeStringUtils.extractClassName(servletMethod) + "."
						+ LpeStringUtils.getSimpleMethodName(servletMethod);

				for (String stackTraceString : getStackTracesOfQuery(query, stackTraceQueries, stackTraces)) {
					if (stackTraceString.contains(formattedServlet)) {
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

	private void createCharts(String servlet, String query, long numMaxUsers,
			NumericPairList<Long, Long> multiUserServletRTs, NumericPairList<Long, Long> multiUserQueryRTs,
			NumericPairList<Long, Long> singleUserServletRTs, NumericPairList<Long, Long> singleUserQueryRTs,
			SpotterResult result) {
		AnalysisChartBuilder chartBuilder = AnalysisChartBuilder.getChartBuilder();

		chartBuilder.startChart("Response Times", "Experiment Time [ms]", "Response Time [ms]");
		chartBuilder.addScatterSeries(multiUserServletRTs, servlet + " with " + numMaxUsers + " users");
		chartBuilder.addScatterSeries(multiUserQueryRTs, query + " with " + numMaxUsers + " users");
		chartBuilder.addScatterSeries(singleUserServletRTs, servlet + " with 1 user");
		chartBuilder.addScatterSeries(singleUserQueryRTs, query + " with 1 user");

		chartBuilder.addHorizontalLine(perfReqThreshold, "Performance Requirement");

		controller.getResultManager().storeImageChartResource(chartBuilder, "Response Times", result);
	}

	private NumericPairList<Long, Long> getServletResponseTimesOverTime(String servlet, Dataset responseTimes) {
		NumericPairList<Long, Long> rtList = new NumericPairList<>();

		Dataset thisServletsRTs = selectRTOperation(servlet).applyTo(responseTimes);

		if (thisServletsRTs == null) {
			return rtList;
		}

		for (ResponseTimeRecord rtRec : thisServletsRTs.getRecords(ResponseTimeRecord.class)) {
			rtList.add(new NumericPair<Long, Long>(rtRec.getTimeStamp(), rtRec.getResponseTime()));
		}

		return rtList;
	}

	private NumericPairList<Long, Long> getQueryResponseTimesOverTime(String query, Dataset responseTimes,
			Dataset queries) {
		NumericPairList<Long, Long> rtList = new NumericPairList<>();

		Dataset thisQueryRecords = new ParameterSelection().select(SQLQueryRecord.PAR_QUERY_STRING, query).applyTo(
				queries);
		if (thisQueryRecords == null) {
			return rtList;
		}

		for (SQLQueryRecord sqlRec : thisQueryRecords.getRecords(SQLQueryRecord.class)) {
			Dataset rtDs = selectCallID(sqlRec.getCallId()).applyTo(responseTimes);
			if (rtDs == null) {
				return rtList;
			}

			for (ResponseTimeRecord rtRec : rtDs.getRecords(ResponseTimeRecord.class)) {
				rtList.add(new NumericPair<Long, Long>(rtRec.getTimeStamp(), rtRec.getResponseTime()));
			}
		}

		return rtList;
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

	private ParameterSelection selectRTOperation(String operation) {
		return new ParameterSelection().select(ResponseTimeRecord.PAR_OPERATION, operation);
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

}
