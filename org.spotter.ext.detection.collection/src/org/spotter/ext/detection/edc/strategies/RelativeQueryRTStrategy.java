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
			record.setQueryString(LpeStringUtils.getGeneralizedQuery(record.getQueryString().replace("#sc#", ";")));
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
		MethodCallSet servletHierarchy = DataAnalyzationUtils.getMethodCallSetOfMethods(
				DataAnalyzationUtils.extractUniqueMethodNames(hierarchyResponseTimes), multiUserResponseTimes,
				multiUserThreadTracing);
		MethodCallSet servletQueryHierarchy = servletHierarchy.getSubsetOfLowestLayer();
		DataAnalyzationUtils.addQueriesToMethodCallSet(servletQueryHierarchy, multiUserResponseTimes, multiUserQueries,
				multiUserThreadTracing);

		Set<String> criticalServlets = getCriticalServlets(servletQueryHierarchy);
		Set<String> nonCriticalServlets = new TreeSet<>();
		nonCriticalServlets.addAll(servletQueryHierarchy.getUniqueMethods());
		nonCriticalServlets.removeAll(criticalServlets);

		for (String nonCritServlet : nonCriticalServlets) {
			servletQueryHierarchy.removeAllCallsWithName(nonCritServlet);
		}

		// Select queries with requirements violating relative response time
		Map<String, Double> violatingReqQueriesART = getReqViolatingQueries(servletQueryHierarchy);

		// Drop queries having a bigger relative response time for one user than
		// for many users

		MethodCallSet singleUserServletQueryHierarchy = servletHierarchy.getSubsetOfLowestLayer();
		DataAnalyzationUtils.addQueriesToMethodCallSet(singleUserServletQueryHierarchy, singleUserResponseTimes,
				singleUserQueries, singleUserThreadTracing);

		filterViolatingReqQueriesBySingleUserTest(violatingReqQueriesART, servletQueryHierarchy,
				singleUserServletQueryHierarchy);

		for (MethodCall call : servletQueryHierarchy.getMethodCalls()) {
			if (call.getCalledOperations().size() == 0) {
				servletQueryHierarchy.removeCall(call);
			}
		}

		return generateResult(servletQueryHierarchy, singleUserServletQueryHierarchy, violatingReqQueriesART);
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

				for (MethodCall queryCall : servletQueryHierarchy.getCallsOfMethodAtLayer(query, 1)) {
					servletQueryHierarchy.removeCall(queryCall);
				}
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

				createCharts(servletMethod, query, numUsers, DataAnalyzationUtils.getServletResponseTimesOverTime(
						servletMethod, multiUserResponseTimes), DataAnalyzationUtils.getQueryResponseTimesOverTime(
						query, multiUserResponseTimes, multiUserQueries),
						DataAnalyzationUtils.getServletResponseTimesOverTime(servletMethod, singleUserResponseTimes),
						DataAnalyzationUtils.getQueryResponseTimesOverTime(query, singleUserResponseTimes,
								singleUserQueries), result);

				double relativeRT = violatingReqQueriesART.get(query);
				List<Double> singleUserRRTs = DataAnalyzationUtils.getRelativeQueryResponseTimes(query,
						singleUserServletQueryHierarchy);
				double singleUserART = LpeNumericUtils.average(singleUserRRTs);

				createRelativeChart(servletMethod, query, numUsers, relativeRT, 1.0, singleUserART, result);

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
			double singleUserRRT, double singleUserQueryRRT, SpotterResult result) {
		NumericPairList<Integer, Double> muRRTSeries = new NumericPairList<>();
		for (int i = 0; i <= 100; i += 4) {
			muRRTSeries.add(new NumericPair<Integer, Double>(i, 100.0));
		}

		NumericPairList<Integer, Double> muQRRTSeries = new NumericPairList<>();
		for (int i = 1; i <= 100; i += 4) {
			muQRRTSeries.add(new NumericPair<Integer, Double>(i, multiUserQueryRRT * 100.0));
		}

		NumericPairList<Integer, Double> suRRTSeries = new NumericPairList<>();
		for (int i = 2; i <= 100; i += 4) {
			suRRTSeries.add(new NumericPair<Integer, Double>(i, singleUserRRT * 100.0));
		}

		NumericPairList<Integer, Double> suQRRTSeries = new NumericPairList<>();
		for (int i = 3; i <= 100; i += 4) {
			suQRRTSeries.add(new NumericPair<Integer, Double>(i, singleUserQueryRRT * singleUserRRT * 100.0));
		}

		AnalysisChartBuilder chartBuilder = AnalysisChartBuilder.getChartBuilder();

		chartBuilder.startChart("Relation between " + servlet + " and " + query, "", "Relative Response Time [%]");
		chartBuilder.addScatterSeries(muRRTSeries, "AVG servlet Response Time with " + numMaxUsers + " users");
		chartBuilder.addScatterSeries(muQRRTSeries, "AVG query Response Time with " + numMaxUsers + " users");
		chartBuilder.addScatterSeries(suRRTSeries, "AVG servlet Response Time with 1 user");
		chartBuilder.addScatterSeries(suQRRTSeries, "AVG query Response Time with 1 user");

		controller.getResultManager().storeImageChartResource(chartBuilder, "Relative Response Times", result);
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

}
