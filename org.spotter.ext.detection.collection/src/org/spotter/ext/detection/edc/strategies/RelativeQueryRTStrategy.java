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

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spotter.core.chartbuilder.AnalysisChartBuilder;
import org.spotter.ext.detection.edc.EDCDetectionController;
import org.spotter.ext.detection.edc.EDCExtension;
import org.spotter.ext.detection.edc.IEDCAnalysisStrategy;
import org.spotter.ext.detection.edc.utils.DataAnalyzationUtils;
import org.spotter.ext.detection.edc.utils.MethodCall;
import org.spotter.ext.detection.edc.utils.MethodCallSet;
import org.spotter.shared.configuration.ConfigKeys;
import org.spotter.shared.result.model.SpotterResult;

public class RelativeQueryRTStrategy implements IEDCAnalysisStrategy {

	private static final Logger LOGGER = LoggerFactory.getLogger(RelativeQueryRTStrategy.class);

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

		String sRelativeQRT = controller.getProblemDetectionConfiguration().getProperty(
				EDCExtension.PERF_REQ_RELATIVE_QUERY_RT_KEY,
				String.valueOf(EDCExtension.PERF_REQ_RELATIVE_QUERY_RT_DEFAULT));
		perfReqRelativeQueryRT = Double.valueOf(sRelativeQRT);
		String sRelativeQRTDiff = controller.getProblemDetectionConfiguration().getProperty(
				EDCExtension.PERF_REQ_RELATIVE_QUERY_RT_DIFF_KEY,
				String.valueOf(EDCExtension.PERF_REQ_RELATIVE_QUERY_RT_DIFF_DEFAULT));
		perfReqRelativeQueryRTDiff = Double.valueOf(sRelativeQRTDiff);

		numUsers = GlobalConfiguration.getInstance().getPropertyAsLong(ConfigKeys.WORKLOAD_MAXUSERS);

	}

	@Override
	public void setMeasurementData(DatasetCollection data) {
		LOGGER.info("Setting measurement data...");

		validData = false;

		ParameterSelection selectHierarchyExp = new ParameterSelection().select(
				EDCDetectionController.KEY_EXPERIMENT_NAME, EDCDetectionController.NAME_HIERARCHY_EXP);
		ParameterSelection selectSingleUserExp = new ParameterSelection().select(
				EDCDetectionController.KEY_EXPERIMENT_NAME, EDCDetectionController.NAME_SINGLE_USER_EXP);
		ParameterSelection selectMultiUserExp = new ParameterSelection().select(
				EDCDetectionController.KEY_EXPERIMENT_NAME, EDCDetectionController.NAME_MAIN_EXP);
		ParameterSelection selectStackTraceExp = new ParameterSelection().select(
				EDCDetectionController.KEY_EXPERIMENT_NAME, EDCDetectionController.NAME_STACK_TRACE_EXP);

		LOGGER.debug("Setting response time datasets...");

		Dataset rtDataset = data.getDataSet(ResponseTimeRecord.class);

		if (rtDataset == null || rtDataset.size() == 0) {
			invalidDataMessage = "Instrumentation achieved no response time results for the given scope!";
			return;
		}

		hierarchyResponseTimes = selectHierarchyExp.applyTo(rtDataset);
		singleUserResponseTimes = selectSingleUserExp.applyTo(rtDataset);
		multiUserResponseTimes = selectMultiUserExp.applyTo(rtDataset);

		LOGGER.debug("Response times set.");
		LOGGER.debug("Setting SQL query datasets...");

		Dataset sqlDataset = data.getDataSet(SQLQueryRecord.class);

		if (sqlDataset == null || sqlDataset.size() == 0) {
			invalidDataMessage = "Instrumentation achieved no query results for the given scope!";
			return;
		}
		
		for (SQLQueryRecord record : sqlDataset.getRecords(SQLQueryRecord.class)) {
			String sql = record.getQueryString().replace("#sc#", ";");
			String generalizedSql = LpeStringUtils.getGeneralizedQuery(sql);
			if (generalizedSql == null) {
				if (sql.contains("$")) {
					int idx_1 = sql.indexOf(",", sql.indexOf("$"));
					int idx_2 = sql.indexOf(" ", sql.indexOf("$"));
					if (idx_1 < 0 && idx_2 < 0) {
						idx_1 = sql.length();
					}
					idx_1 = idx_1 < 0 ? Integer.MAX_VALUE : idx_1;
					idx_2 = idx_2 < 0 ? Integer.MAX_VALUE : idx_2;
					int endIndex = Math.min(idx_1, idx_2);
					String name = sql.substring(sql.indexOf("$"), endIndex);
					generalizedSql = sql.replace(name, "tmp");
				} else {
					generalizedSql = sql;
				}
			}

			record.setQueryString(generalizedSql);
		}

		singleUserQueries = selectSingleUserExp.applyTo(sqlDataset);
		multiUserQueries = selectMultiUserExp.applyTo(sqlDataset);
		stackTraceQueries = selectStackTraceExp.applyTo(sqlDataset);

		LOGGER.debug("SQL queries set.");
		LOGGER.debug("Setting thread tracing datasets...");

		Dataset ttDataset = data.getDataSet(ThreadTracingRecord.class);

		if (ttDataset == null || ttDataset.size() == 0) {
			invalidDataMessage = "Instrumentation achieved no thread tracing results for the given scope!";
			return;
		}

		singleUserThreadTracing = selectSingleUserExp.applyTo(ttDataset);
		multiUserThreadTracing = selectMultiUserExp.applyTo(ttDataset);

		LOGGER.debug("Thread tracing set.");
		LOGGER.debug("Setting stack trace datasets...");

		Dataset stDataset = data.getDataSet(StackTraceRecord.class);

		if (stDataset == null || stDataset.size() == 0) {
			invalidDataMessage = "Instrumentation achieved no stack trace results for the given scope!";
			return;
		}

		stackTraces = selectStackTraceExp.applyTo(stDataset);

		LOGGER.debug("Stack traces set.");

		validData = true;

		LOGGER.info("Data successfully set.");
	}

	@Override
	public SpotterResult analyze() {
		LOGGER.info("Starting analysis...");

		SpotterResult result = new SpotterResult();

		if (!validData) {
			result.setDetected(false);
			result.addMessage(invalidDataMessage);
			LOGGER.warn("Analysis could not be run due to invalid data: {}", invalidDataMessage);
			return result;
		}

		LOGGER.debug("Deriving servlet hierarchy...");
		// Select servlets with requirements violating response times
		Set<String> servletNames = DataAnalyzationUtils.extractUniqueMethodNames(hierarchyResponseTimes);
		MethodCallSet servletHierarchy = DataAnalyzationUtils.getMethodCallSetOfMethods(servletNames,
				multiUserResponseTimes, multiUserThreadTracing);
		LOGGER.debug("Servlet hierarchy created.");
		LOGGER.debug("Deriving lowest servlet layer...");
		MethodCallSet servletQueryHierarchy = servletHierarchy.getSubsetOfLowestLayer();
		LOGGER.debug("Lowest layer derived.");
		LOGGER.debug("Deriving servlet-query hierarchy...");
		DataAnalyzationUtils.addQueriesToMethodCallSet(servletQueryHierarchy, multiUserResponseTimes, multiUserQueries,
				multiUserThreadTracing);
		LOGGER.debug("Servlet-query hierarchy created.");

		LOGGER.debug("Locating critical servlets...");

		Set<String> criticalServlets = getCriticalServlets(servletQueryHierarchy);
		Set<String> nonCriticalServlets = new TreeSet<>();
		nonCriticalServlets.addAll(servletQueryHierarchy.getUniqueMethods());
		nonCriticalServlets.removeAll(criticalServlets);

		for (String nonCritServlet : nonCriticalServlets) {
			servletQueryHierarchy.removeAllCallsWithName(nonCritServlet);
		}

		LOGGER.debug("Critical servlets located.");

		// Select queries with requirements violating relative response time
		LOGGER.debug("Locate requirements violating queries...");
		Map<String, Double> violatingReqQueriesART = getReqViolatingQueries(servletQueryHierarchy);

		for (MethodCall servletCall : servletQueryHierarchy.getMethodCalls()) {
			for (MethodCall queryCall : servletCall.getCalledOperations()) {
				if (!violatingReqQueriesART.containsKey(queryCall.getOperation())) {
					servletCall.removeCall(queryCall);
				}
			}

			if (servletCall.getCalledOperations().size() == 0) {
				servletQueryHierarchy.removeCall(servletCall);
			}
		}

		LOGGER.debug("Queries located.");

		// Drop queries having a bigger relative response time for one user than
		// for many users

		LOGGER.debug("Drop false positives (single-user test)...");

		MethodCallSet singleUserServletHierarchy = DataAnalyzationUtils.getMethodCallSetOfMethods(criticalServlets,
				singleUserResponseTimes, singleUserThreadTracing);
		MethodCallSet singleUserServletQueryHierarchy = singleUserServletHierarchy.getSubsetOfLowestLayer();
		DataAnalyzationUtils.addQueriesToMethodCallSet(singleUserServletQueryHierarchy, singleUserResponseTimes,
				singleUserQueries, singleUserThreadTracing);

		filterViolatingReqQueriesBySingleUserTest(violatingReqQueriesART, servletQueryHierarchy,
				singleUserServletQueryHierarchy);

		for (MethodCall servletCall : servletQueryHierarchy.getMethodCalls()) {
			if (servletCall.getCalledOperations().size() == 0) {
				servletQueryHierarchy.removeCall(servletCall);
			}
		}

		LOGGER.debug("False positives dropped.");

		LOGGER.debug("Generate Spotter result...");
		result = generateResult(servletQueryHierarchy, singleUserServletQueryHierarchy, violatingReqQueriesART);
		LOGGER.debug("Result generated.");

		LOGGER.info("Analysis finished!");

		return result;
	}

	private Set<String> getCriticalServlets(MethodCallSet servletQueryHierarchy) {
		Set<String> criticalServlets = new TreeSet<>();

		for (String methodName : servletQueryHierarchy.getUniqueMethods()) {
			Set<MethodCall> callsOfMethod = servletQueryHierarchy.getCallsOfMethodAtLayer(methodName, 0);
			int numberOfReqViolatingCalls = 0;

			for (MethodCall call : callsOfMethod) {
				if (call.getResponseTime() > perfReqThreshold) {
					numberOfReqViolatingCalls++;
				}
			}

			if ((double) numberOfReqViolatingCalls / (double) callsOfMethod.size() > 1.0 - perfReqConfidence) {
				criticalServlets.add(methodName);
			}
		}

		return criticalServlets;
	}

	private Map<String, Double> getReqViolatingQueries(MethodCallSet servletQueryHierarchy) {

		Set<String> possiblyCriticalQueries = servletQueryHierarchy.getUniqueMethodsOfLayer(1);
		Map<String, Double> violatingReqQueriesART = new TreeMap<>();

		for (String query : possiblyCriticalQueries) {
			List<Double> relativeRTs = DataAnalyzationUtils.getRelativeQueryResponseTimes(query, servletQueryHierarchy);

			if (LpeNumericUtils.average(relativeRTs) > perfReqRelativeQueryRT) {
				violatingReqQueriesART.put(query, LpeNumericUtils.average(relativeRTs));
			} else {
				for (MethodCall queryCall : servletQueryHierarchy.getCallsOfMethodAtLayer(query, 1)) {
					servletQueryHierarchy.removeCall(queryCall);
				}
			}
		}

		return violatingReqQueriesART;
	}

	private void filterViolatingReqQueriesBySingleUserTest(Map<String, Double> violatingReqQueriesART,
			MethodCallSet servletQueryHierarchy, MethodCallSet singleUserServletQueryHierarchy) {
		Set<String> queriesToRemove = new TreeSet<>();

		for (String query : violatingReqQueriesART.keySet()) {
			List<Double> singleUserRRTs = DataAnalyzationUtils.getRelativeQueryResponseTimes(query,
					singleUserServletQueryHierarchy);
			double avgRRTDiff = violatingReqQueriesART.get(query) - LpeNumericUtils.average(singleUserRRTs);

			if (avgRRTDiff < perfReqRelativeQueryRTDiff) {
				queriesToRemove.add(query);
				servletQueryHierarchy.removeAllCallsWithName(query);
			}
		}

		for (String query : queriesToRemove) {
			violatingReqQueriesART.remove(query);
		}
	}

	private SpotterResult generateResult(MethodCallSet servletQueryHierarchy,
			MethodCallSet singleUserServletQueryHierarchy, Map<String, Double> violatingReqQueriesART) {
		SpotterResult result = new SpotterResult();
		result.setDetected(false);
		for (String servletMethod : servletQueryHierarchy.getUniqueMethodsOfLayer(0)) {
			StringBuilder messageBuilder = new StringBuilder();
			messageBuilder.append("EDC detected in service: ");
			messageBuilder.append(servletMethod);
			messageBuilder.append("\nQueries are:");

			for (String query : violatingReqQueriesART.keySet()) {
				String formattedServlet = LpeStringUtils.extractClassName(servletMethod) + "."
						+ LpeStringUtils.getSimpleMethodName(servletMethod);
				List<String> stackTrace = null;
				for (String stackTraceString : DataAnalyzationUtils.getStackTracesOfQuery(query, stackTraceQueries,
						stackTraces)) {
					if (stackTraceString.contains(formattedServlet)) {
						stackTrace = new ArrayList<>();
						for (String stackTraceElement : stackTraceString
								.split(StackTraceProbe.REGEX_DELIM_STACK_TRACE_ELEMENT)) {
							stackTrace.add(stackTraceElement);
						}

						break;
					}
				}

				if (stackTrace == null) {
					continue;
				} else {
					result.setDetected(true);
				}

				NumericPairList<Long, Long> multiUserServletRts = DataAnalyzationUtils.getServletResponseTimesOverTime(
						servletMethod, multiUserResponseTimes);
				NumericPairList<Long, Long> singleUserServletRts = DataAnalyzationUtils
						.getServletResponseTimesOverTime(servletMethod, singleUserResponseTimes);

				double relativeRT = violatingReqQueriesART.get(query);
				List<Double> singleUserRRTs = DataAnalyzationUtils.getRelativeQueryResponseTimes(query,
						singleUserServletQueryHierarchy);
				double singleUserART = LpeNumericUtils.average(singleUserRRTs);

				createTimeSeriesChart(servletMethod, query, numUsers, multiUserServletRts,
						DataAnalyzationUtils.getQueryResponseTimesOverTime(query, multiUserResponseTimes,
								multiUserQueries), singleUserServletRts,
						DataAnalyzationUtils.getQueryResponseTimesOverTime(query, singleUserResponseTimes,
								singleUserQueries), result);
				createRelativeChart(servletMethod, query, numUsers, relativeRT, singleUserART, result);

				DecimalFormat df = new DecimalFormat("#.##");

				messageBuilder.append("\n");
				messageBuilder.append(query);
				messageBuilder.append("\n\tAverage relative response time with ");
				messageBuilder.append(numUsers);
				messageBuilder.append(" users: ");
				messageBuilder.append(df.format(relativeRT * 100));
				messageBuilder.append("%");
				messageBuilder.append("\n\tAverage relative response time with 1 user: ");
				messageBuilder.append(df.format(singleUserART * 100));
				messageBuilder.append("%");
				messageBuilder.append("\n\tStack trace:");

				for (String stackTraceElement : stackTrace) {
					messageBuilder.append("\n\t\t");
					messageBuilder.append(stackTraceElement);
				}
			}

			if (result.isDetected()) {
				result.addMessage(messageBuilder.toString());
			}
		}

		return result;
	}

	private void createRelativeChart(String servlet, String query, long numMaxUsers, double multiUserQueryRRT,
			double singleUserQueryRRT, SpotterResult result) {
		NumericPairList<Integer, Double> muRRTSeries = new NumericPairList<>();
		for (int i = 0; i < 50; i += 2) {
			muRRTSeries.add(new NumericPair<Integer, Double>(i, 100.0));
		}

		NumericPairList<Integer, Double> muQRRTSeries = new NumericPairList<>();
		for (int i = 1; i < 50; i += 2) {
			muQRRTSeries.add(new NumericPair<Integer, Double>(i, multiUserQueryRRT * 100.0));
		}

		NumericPairList<Integer, Double> suRRTSeries = new NumericPairList<>();
		for (int i = 50; i <= 100; i += 2) {
			suRRTSeries.add(new NumericPair<Integer, Double>(i, 100.0));
		}

		NumericPairList<Integer, Double> suQRRTSeries = new NumericPairList<>();
		for (int i = 51; i <= 100; i += 2) {
			suQRRTSeries.add(new NumericPair<Integer, Double>(i, singleUserQueryRRT * 100.0));
		}

		NumericPairList<Integer, Double> nullSeries = new NumericPairList<>();
		nullSeries.add(new NumericPair<Integer, Double>(100, 0.0));

		String servletName = LpeStringUtils.shortenOperationName(servlet);
		String queryName = query;

		if (queryName.length() > 15) {
			queryName = queryName.substring(0, 13) + "...";
		}

		AnalysisChartBuilder chartBuilder = AnalysisChartBuilder.getChartBuilder();

		chartBuilder.startChart("Relation between " + servletName + " and " + queryName, "",
				"Relative Response Time [%]");
		chartBuilder.addScatterSeries(muRRTSeries, "AVG servlet Response Time with " + numMaxUsers + " users");
		chartBuilder.addScatterSeries(muQRRTSeries, "AVG query Response Time with " + numMaxUsers + " users");
		chartBuilder.addScatterSeries(suRRTSeries, "AVG servlet Response Time with 1 user");
		chartBuilder.addScatterSeries(suQRRTSeries, "AVG query Response Time with 1 user");
		chartBuilder.addScatterSeries(nullSeries, "0");

		controller.getResultManager().storeImageChartResource(chartBuilder, "Relative Response Times", result);
	}

	private void createTimeSeriesChart(String servlet, String query, long numMaxUsers,
			NumericPairList<Long, Long> multiUserServletRTs, NumericPairList<Long, Long> multiUserQueryRTs,
			NumericPairList<Long, Long> singleUserServletRTs, NumericPairList<Long, Long> singleUserQueryRTs,
			SpotterResult result) {
		AnalysisChartBuilder chartBuilder = AnalysisChartBuilder.getChartBuilder();

		String servletName = LpeStringUtils.shortenOperationName(servlet);
		String queryName = query;

		if (queryName.length() > 15) {
			queryName = queryName.substring(0, 13) + "...";
		}

		chartBuilder.startChart("Response Times", "Experiment Time [ms]", "Response Time [ms]");
		chartBuilder.addScatterSeries(multiUserServletRTs, servletName + " with " + numMaxUsers + " users");
		chartBuilder.addScatterSeries(multiUserQueryRTs, queryName + " with " + numMaxUsers + " users");
		chartBuilder.addScatterSeries(singleUserServletRTs, servletName + " with 1 user");
		chartBuilder.addScatterSeries(singleUserQueryRTs, queryName + " with 1 user");

		chartBuilder.addHorizontalLine(perfReqThreshold, "Performance Requirement");

		controller.getResultManager().storeImageChartResource(chartBuilder, "Response Times", result);
	}

}
