package org.spotter.ext.detection.olb.strategies;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import org.aim.api.measurement.dataset.Dataset;
import org.aim.api.measurement.dataset.DatasetCollection;
import org.aim.api.measurement.dataset.ParameterSelection;
import org.aim.artifacts.records.CPUUtilizationRecord;
import org.aim.artifacts.records.NetworkInterfaceInfoRecord;
import org.aim.artifacts.records.NetworkRecord;
import org.aim.artifacts.records.ResponseTimeRecord;
import org.aim.artifacts.records.SQLQueryRecord;
import org.lpe.common.util.system.LpeSystemUtils;
import org.lpe.common.utils.numeric.LpeNumericUtils;
import org.lpe.common.utils.numeric.NumericPair;
import org.lpe.common.utils.numeric.NumericPairList;
import org.lpe.common.utils.sql.SQLStringUtils;
import org.spotter.core.chartbuilder.AnalysisChartBuilder;
import org.spotter.core.detection.AbstractDetectionController;
import org.spotter.ext.detection.olb.IOLBAnalysisStrategy;
import org.spotter.ext.detection.olb.OLBDetectionController;
import org.spotter.ext.detection.olb.OLBExtension;
import org.spotter.shared.result.model.SpotterResult;

/**
 * Utilizes queueing theory to detect a One Lane Bridge.
 * 
 * @author Alexander Wert
 * 
 */
public class QTStrategy implements IOLBAnalysisStrategy {

	private static final double ONE_PLUS_EPSILON = 1.1;
	private static final long MS_IN_SECOND = 1000L;
	private OLBDetectionController mainDetectionController;
	private static final long SPEED_100MBIT = 100000000;
	private static final double SIG_LEVEL = 0.05;
	private static final int NUM_REQ_SIG_STEPS = 2;
	private String scope;

	@Override
	public SpotterResult analyze(final DatasetCollection data) {
		final SpotterResult result = new SpotterResult();

		final Dataset rtDataset = data.getDataSet(ResponseTimeRecord.class);

		if (rtDataset == null || rtDataset.size() == 0) {
			result.setDetected(false);
			result.addMessage("Instrumentation achieved no response time results for the given scope!");
			return result;
		}

		final Dataset cpuUtilDataset = data.getDataSet(CPUUtilizationRecord.class);

		if (cpuUtilDataset == null || cpuUtilDataset.size() == 0) {
			result.setDetected(false);
			result.addMessage("Instrumentation achieved no CPU utilization results for the given scope!");
			return result;
		}

		final Dataset networkInfoDataset = data.getDataSet(NetworkInterfaceInfoRecord.class);

		if (networkInfoDataset == null || networkInfoDataset.size() == 0) {
			result.setDetected(false);
			result.addMessage("Instrumentation achieved no networkInfoDataset results for the given scope!");
			return result;
		}

		final Dataset networkIODataset = data.getDataSet(NetworkRecord.class);

		if (networkIODataset == null || networkIODataset.size() == 0) {
			result.setDetected(false);
			result.addMessage("Instrumentation achieved no netowrk IO results for the given scope!");
			return result;
		}

		final List<Integer> numUsersList = getNumUsersList(rtDataset);
		Map<String, NumericPairList<Integer, Double>> responseTimesMap = null;
		if (scope.equals(OLBExtension.DB_SCOPE)) {
			final Dataset sqlDataset = data.getDataSet(SQLQueryRecord.class);

			if (sqlDataset == null || sqlDataset.size() == 0) {
				result.setDetected(false);
				result.addMessage("Instrumentation achieved no SQL results for the given scope!");
				return result;
			}

			responseTimesMap = getOperationResponseTimesWithSQL(rtDataset, sqlDataset, result, numUsersList);
		} else {
			responseTimesMap = getOperationResponseTimes(rtDataset, result, numUsersList);

		}
		final Map<String, NumericPairList<Integer, Double>> utilsMap = getCPUUtilizations(cpuUtilDataset, result,
				numUsersList);

		utilsMap.putAll(getNetworkUtilizations(networkIODataset, networkInfoDataset, result, numUsersList));
		final Map<String, Integer> numServersMap = getNumberOfCPUCores(cpuUtilDataset);
		numServersMap.putAll(getNumberServers(networkInfoDataset));

		// List<String> candidateOperations =
		// analyseResponseTimesIncrease(result, numUsersList, responseTimesMap);
		// Set<String> operations = new HashSet<>();
		// operations.addAll(responseTimesMap.keySet());
		// for (String operation : operations) {
		// if (!candidateOperations.contains(operation)) {
		// responseTimesMap.remove(operation);
		// }
		// }
		analyzeOLB(result, numUsersList, responseTimesMap, utilsMap, numServersMap);

		return result;
	}

	private List<String> analyseResponseTimesIncrease(final SpotterResult result, final List<Integer> numUsersList,
			final Map<String, NumericPairList<Integer, Double>> responseTimesMap) {
		final List<String> guiltyOperations = new ArrayList<>();

		for (final String operation : responseTimesMap.keySet()) {
			try {

				int prevNumUsers = -1;
				int firstSignificantNumUsers = -1;
				int significantSteps = 0;
				List<Double> prevValues = null;
				for (final Integer numUsers : numUsersList) {
					if (prevNumUsers > 0) {
						final List<Double> currentValues = LpeNumericUtils.filterOutliersUsingIQR(getValuesForNumUsers(
								responseTimesMap.get(operation), numUsers));
						prevValues = LpeNumericUtils.filterOutliersUsingIQR(getValuesForNumUsers(
								responseTimesMap.get(operation), prevNumUsers));

						final List<Double> sums1 = new ArrayList<>();
						final List<Double> sums2 = new ArrayList<>();
						LpeNumericUtils
								.createNormalDistributionByBootstrapping(prevValues, currentValues, sums1, sums2);

						if (sums2.size() < 2 || sums1.size() < 2) {
							throw new IllegalArgumentException("OLB detection failed for the operation '" + operation
									+ "', because there are not enough measurement points for a t-test.");
						}
						final double prevMean = LpeNumericUtils.average(sums1);
						final double currentMean = LpeNumericUtils.average(sums2);

						final double pValue = LpeNumericUtils.tTest(sums2, sums1);
						if (pValue >= 0 && pValue <= SIG_LEVEL && prevMean < currentMean) {
							if (firstSignificantNumUsers < 0) {
								firstSignificantNumUsers = prevNumUsers;
							}
							significantSteps++;
						} else {
							firstSignificantNumUsers = -1;
							significantSteps = 0;
						}
					}
					prevNumUsers = numUsers;

				}

				if (firstSignificantNumUsers > 0 && significantSteps >= NUM_REQ_SIG_STEPS) {
					guiltyOperations.add(operation);
				}
			} catch (final Exception e) {
			}
		}

		return guiltyOperations;
	}

	private void analyzeOLB(final SpotterResult result, final List<Integer> numUsersList,
			final Map<String, NumericPairList<Integer, Double>> responseTimesMap,
			final Map<String, NumericPairList<Integer, Double>> utilsMap, final Map<String, Integer> numServersMap) {
		final Set<String> utilsChartsCreatedFor = new HashSet<>();
		final Map<String, Double> operationScales = new HashMap<>();
		final Map<String, NumericPairList<Integer, Double>> rtThresholdsForChart = new HashMap<String, NumericPairList<Integer, Double>>();
		final Map<String, NumericPairList<Integer, Double>> chartResponseTimes = new HashMap<String, NumericPairList<Integer, Double>>();

		operationLoop: for (final String operation : responseTimesMap.keySet()) {
			final NumericPairList<Integer, Double> responseTimes = responseTimesMap.get(operation);
			double singleUserResponseTime = 0;
			try {
				singleUserResponseTime = getValueForNumUsers(responseTimes, responseTimes.getKeyMin());
			} catch (final Exception e) {
				continue operationLoop;
			}
			singleUserResponseTime = Math.max(singleUserResponseTime, 15.0);
			final boolean qtDetected = true;
			int i = 0;

			final Map<Integer, Double> thresholds = new HashMap<>();

			boolean fixThresholdExceeded = false;
			for (final String rersourceID : utilsMap.keySet()) {
				final NumericPairList<Integer, Double> utils = utilsMap.get(rersourceID);
				final int numServers = numServersMap.get(rersourceID);
				createUtilChart(result, utilsChartsCreatedFor, rersourceID, utils);
				for (final int numUsers : numUsersList) {
					final double utilization = getValueForNumUsers(utils, numUsers);
					if(utilization>0.9){
						fixThresholdExceeded=true;
					}
					final double saveUtil = Math.min(0.999, utilization + 0.1);
					if (!thresholds.containsKey(numUsers)) {
						thresholds.put(numUsers, 0.0);
					}
					// response time threshold derived from queueing theory for
					// multi-server queues
					final double t = singleUserResponseTime / (numServers * (1 - saveUtil))
							* LpeNumericUtils.calculateErlangsCFormula(numServers, saveUtil) + singleUserResponseTime;
					thresholds.put(numUsers, Math.max(t, thresholds.get(numUsers)));
				}

			}
			if(fixThresholdExceeded){
				continue operationLoop;
			}
			rtThresholdsForChart.put(operation, new NumericPairList<Integer, Double>());
			chartResponseTimes.put(operation, new NumericPairList<Integer, Double>());
			boolean responseTimesUnderThresholdCurve = true;
			double maxResponseTime = Double.MIN_VALUE;
			for (final int numUsers : numUsersList) {
				double responseTime = 0.0;
				try {
					responseTime = getValueForNumUsers(responseTimes, numUsers);
					maxResponseTime = Math.max(maxResponseTime, responseTime);
				} catch (final Exception e) {
					continue operationLoop;
				}
				if (i < numUsersList.size()) {
					chartResponseTimes.get(operation).add(numUsers, responseTime);
				}

				if (responseTime > thresholds.get(numUsers)) {
					responseTimesUnderThresholdCurve = false;

				}

				rtThresholdsForChart.get(operation).add(numUsers, thresholds.get(numUsers));
				i++;
			}

			if (!responseTimesUnderThresholdCurve) {
				operationScales.put(operation, maxResponseTime);

			}

		}
		double sum = 0.0;
		for (final Double d : operationScales.values()) {
			sum += d;
		}
		for (final String op : operationScales.keySet()) {
			final double scale = operationScales.get(op);
			operationScales.put(op, scale / sum);
		}
		final List<Entry<String, Double>> sortedEntryList = new ArrayList<>();

		sortedEntryList.addAll(operationScales.entrySet());
		Collections.sort(sortedEntryList, new Comparator<Entry<String, Double>>() {

			@Override
			public int compare(final Entry<String, Double> o1, final Entry<String, Double> o2) {
				return o2.getValue().compareTo(o1.getValue());
			}
		});

		double sumPercent = 0.0;
		for (final Entry<String, Double> entry : sortedEntryList) {
			result.setDetected(true);
			result.addMessage("OLB detected in service: " + entry.getKey());
			createDetectedChart(result, entry.getKey(), chartResponseTimes.get(entry.getKey()),
					rtThresholdsForChart.get(entry.getKey()));
			sumPercent += entry.getValue();
			if (sumPercent >= 0.8) {
				break;
			}
		}
	}

	private void createRTChart(final SpotterResult result, final String operation, final NumericPairList<Integer, Double> responseTimes) {
		final AnalysisChartBuilder chartBuilder = AnalysisChartBuilder.getChartBuilder();
		final String operationName = operation.contains("(") ? operation.substring(0, operation.indexOf("(")) : operation;
		chartBuilder
				.startChart(operationName, "number of users", "response time [ms]");
		chartBuilder.addScatterSeries(responseTimes, "avg. response times");
		mainDetectionController.getResultManager().storeImageChartResource(chartBuilder, "Response Times", result);
	}

	private void createDetectedChart(final SpotterResult result, final String operation,
			final NumericPairList<Integer, Double> responseTimes, final NumericPairList<Integer, Double> rtThresholdsForChart) {
		final AnalysisChartBuilder chartBuilder = AnalysisChartBuilder.getChartBuilder();
		final String operationName = operation.contains("(") ? operation.substring(0, operation.indexOf("(")) : operation;

		chartBuilder.startChart(operationName, "number of users", "response time [ms]");
		chartBuilder.addScatterSeriesWithLine(responseTimes, "avg. response times");
		chartBuilder.addLineSeries(rtThresholdsForChart, "response times threshold");
		mainDetectionController.getResultManager().storeImageChartResource(chartBuilder, "Detected", result);

	}

	private void createUtilChart(final SpotterResult result, final Set<String> utilsChartsCreatedFor, final String resourceId,
			final NumericPairList<Integer, Double> cpuUtils) {
		AnalysisChartBuilder chartBuilder = null;
		if (!utilsChartsCreatedFor.contains(resourceId)) {
			chartBuilder = AnalysisChartBuilder.getChartBuilder();
			chartBuilder.startChart("CPU on " + resourceId, "number of users", "utilization [%]");
			chartBuilder.addUtilizationLineSeries(cpuUtils, "utilization", true);
			mainDetectionController.getResultManager().storeImageChartResource(chartBuilder,
					"Utilization-" + resourceId, result);
			utilsChartsCreatedFor.add(resourceId);
		}
	}

	private double getValueForNumUsers(final NumericPairList<Integer, Double> pairList, final int numUsers) {
		final List<Double> values = getValuesForNumUsers(pairList, numUsers);

		if (!values.isEmpty()) {
			return LpeNumericUtils.average(values);
		}
		throw new IllegalArgumentException("Data not found!");
	}

	private List<Double> getValuesForNumUsers(final NumericPairList<Integer, Double> pairList, final int numUsers) {
		final List<Double> values = new ArrayList<>();
		for (final NumericPair<Integer, Double> pair : pairList) {
			if (pair.getKey().equals(numUsers)) {
				values.add(pair.getValue());

			}
		}
		return values;

	}

	private List<Integer> getNumUsersList(final Dataset rtDataset) {
		final List<Integer> numUsersList = new ArrayList<>(rtDataset.getValueSet(
				AbstractDetectionController.NUMBER_OF_USERS_KEY, Integer.class));
		Collections.sort(numUsersList);
		return numUsersList;
	}

	private Map<String, NumericPairList<Integer, Double>> getOperationResponseTimes(final Dataset rtDataset,
			final SpotterResult result, final List<Integer> numUsersList) {

		final Map<String, NumericPairList<Integer, Double>> resultMap = new HashMap<>();

		operationLoop: for (final String operation : rtDataset.getValueSet(ResponseTimeRecord.PAR_OPERATION, String.class)) {
			final NumericPairList<Integer, Double> responseTimePairList = new NumericPairList<>();
			for (final Integer numUsers : numUsersList) {

				final ParameterSelection selection = new ParameterSelection().select(
						AbstractDetectionController.NUMBER_OF_USERS_KEY, numUsers).select(
						ResponseTimeRecord.PAR_OPERATION, operation);

				final Dataset tmpRTDataset = selection.applyTo(rtDataset);

				if (tmpRTDataset == null || tmpRTDataset.size() == 0) {
					result.addMessage("One Lane Bridge detection failed for the operation '" + operation
							+ "', because the operation was not executed in each analysis cycle. "
							+ "Hence, the operation cannot be analyzed for an OLB.");
					continue operationLoop;
				}

				final List<Long> responseTimes = tmpRTDataset.getValues(ResponseTimeRecord.PAR_RESPONSE_TIME, Long.class);

				for (final Long rt : responseTimes) {
					responseTimePairList.add(numUsers, rt.doubleValue());
				}
			}
			resultMap.put(operation, responseTimePairList);
			createRTChart(result, operation, responseTimePairList);
		}
		return resultMap;
	}

	private Map<String, NumericPairList<Integer, Double>> getOperationResponseTimesWithSQL(final Dataset rtDataset,
			final Dataset sqlDataset, final SpotterResult result, final List<Integer> numUsersList) {

		final Map<String, NumericPairList<Integer, Double>> resultMap = new HashMap<>();
		final List<SQLQueryRecord> sqlRecords = sqlDataset.getRecords(SQLQueryRecord.class);
		operationLoop: for (final String operation : rtDataset.getValueSet(ResponseTimeRecord.PAR_OPERATION, String.class)) {

			if (operation.contains("execute")) {
				final Map<String, String> queryMap = new HashMap<>();
				final Map<String, NumericPairList<Integer, Double>> rtMap = new HashMap<>();
				for (final Integer numUsers : numUsersList) {
					final ParameterSelection selection = new ParameterSelection().select(
							AbstractDetectionController.NUMBER_OF_USERS_KEY, numUsers).select(
							ResponseTimeRecord.PAR_OPERATION, operation);

					final Dataset tmpRTDataset = selection.applyTo(rtDataset);

					if (tmpRTDataset == null || tmpRTDataset.size() == 0) {
						result.addMessage("One Lane Bridge detection failed for the operation '" + operation
								+ "', because the operation was not executed in each analysis cycle. "
								+ "Hence, the operation cannot be analyzed for an OLB.");
						continue operationLoop;
					}
					final Map<String, List<Long>> responsetimesMap = new HashMap<>();
					for (final ResponseTimeRecord rtRecord : tmpRTDataset.getRecords(ResponseTimeRecord.class)) {
						final SQLQueryRecord sqlRecord = findRecordForCallID(sqlRecords, rtRecord.getCallId());
						if (sqlRecord == null) {
							continue;
						}
						String sql = null;
						try {
							sql = SQLStringUtils.getGeneralizedQuery(sqlRecord.getQueryString());
							if (sql == null) {
								sql = sqlRecord.getQueryString();
								if (sql.contains("$")) {
									int idx_1 = sql.indexOf(",", sql.indexOf("$"));
									int idx_2 = sql.indexOf(" ", sql.indexOf("$"));
									idx_1 = idx_1 < 0 ? Integer.MAX_VALUE : idx_1;
									idx_2 = idx_2 < 0 ? Integer.MAX_VALUE : idx_2;
									final int endIndex = Math.min(idx_1, idx_2);
									final String name = sql.substring(sql.indexOf("$"), endIndex);
									sql = sql.replace(name, "tmp");
								}
							}
						} catch (final Exception e) {

							continue;
						}
						final int hash = sql.hashCode();
						final String opName = hash + " - " + operation;
						if (!responsetimesMap.containsKey(opName)) {
							responsetimesMap.put(opName, new ArrayList<Long>());
						}
						if (!queryMap.containsKey(opName)) {
							queryMap.put(opName, sql);
						}
						if (!rtMap.containsKey(opName)) {
							rtMap.put(opName, new NumericPairList<Integer, Double>());
						}
						final List<Long> rtList = responsetimesMap.get(opName);
						rtList.add(rtRecord.getResponseTime());
					}

					for (final String opName : responsetimesMap.keySet()) {

						final List<Long> responseTimes = responsetimesMap.get(opName);
						final NumericPairList<Integer, Double> responseTimePairList = rtMap.get(opName);
						for (final Long rt : responseTimes) {
							responseTimePairList.add(numUsers, rt.doubleValue());
						}

					}

				}
				storeQueryMap(queryMap, result);
				for (final String opName : rtMap.keySet()) {
					resultMap.put(opName, rtMap.get(opName));
					createRTChart(result, opName, resultMap.get(opName));
				}

			} else {
				final NumericPairList<Integer, Double> responseTimePairList = new NumericPairList<>();
				for (final Integer numUsers : numUsersList) {

					final ParameterSelection selection = new ParameterSelection().select(
							AbstractDetectionController.NUMBER_OF_USERS_KEY, numUsers).select(
							ResponseTimeRecord.PAR_OPERATION, operation);

					final Dataset tmpRTDataset = selection.applyTo(rtDataset);

					if (tmpRTDataset == null || tmpRTDataset.size() == 0) {
						result.addMessage("One Lane Bridge detection failed for the operation '" + operation
								+ "', because the operation was not executed in each analysis cycle. "
								+ "Hence, the operation cannot be analyzed for an OLB.");
						continue operationLoop;
					}

					final List<Long> responseTimes = tmpRTDataset.getValues(ResponseTimeRecord.PAR_RESPONSE_TIME, Long.class);
					final double meanResponseTime = LpeNumericUtils.average(responseTimes);
					responseTimePairList.add(numUsers, meanResponseTime);
				}
				resultMap.put(operation, responseTimePairList);
			}

		}
		return resultMap;
	}

	private void storeQueryMap(final Map<String, String> queryMap, final SpotterResult result) {
		try {
			final PipedOutputStream outStream = new PipedOutputStream();
			final PipedInputStream inStream = new PipedInputStream(outStream);

			LpeSystemUtils.submitTask(new Runnable() {

				@Override
				public void run() {
					final BufferedWriter bWriter = new BufferedWriter(new OutputStreamWriter(outStream));
					try {
						for (final Entry<String, String> entry : queryMap.entrySet()) {
							bWriter.write(entry.getKey() + " : " + entry.getValue());
							bWriter.newLine();
						}
					} catch (final IOException e) {

					} finally {
						if (bWriter != null) {
							try {
								bWriter.close();
							} catch (final IOException e) {
								throw new RuntimeException(e);
							}
						}

					}
				}

			});
			mainDetectionController.getResultManager().storeTextResource("QueryMap", result, inStream);
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	private SQLQueryRecord findRecordForCallID(final List<SQLQueryRecord> sqlRecords, final long callID) {
		for (final SQLQueryRecord rec : sqlRecords) {
			if (rec.getCallId() == callID) {
				return rec;
			}
		}
		return null;

	}

	private Map<String, NumericPairList<Integer, Double>> getCPUUtilizations(final Dataset cpuUtilDataset,
			final SpotterResult result, final List<Integer> numUsersList) {
		final Map<String, NumericPairList<Integer, Double>> resultMap = new HashMap<>();

		for (final String processID : cpuUtilDataset.getValueSet(CPUUtilizationRecord.PAR_PROCESS_ID, String.class)) {
			final NumericPairList<Integer, Double> cpuUtilPairList = new NumericPairList<>();
			for (final Integer numUsers : numUsersList) {

				final ParameterSelection selection = new ParameterSelection()
						.select(AbstractDetectionController.NUMBER_OF_USERS_KEY, numUsers)
						.select(CPUUtilizationRecord.PAR_PROCESS_ID, processID)
						.select(CPUUtilizationRecord.PAR_CPU_ID, CPUUtilizationRecord.RES_CPU_AGGREGATED);

				final Dataset tmpCPUUtilDataset = selection.applyTo(cpuUtilDataset);
				final List<Double> cpuUtils = tmpCPUUtilDataset.getValues(CPUUtilizationRecord.PAR_UTILIZATION, Double.class);
				final double meanCPUUtil = LpeNumericUtils.average(cpuUtils);
				cpuUtilPairList.add(numUsers, meanCPUUtil);
			}
			resultMap.put(processID + " - " + CPUUtilizationRecord.RES_CPU_AGGREGATED, cpuUtilPairList);

		}
		return resultMap;
	}

	private Map<String, NumericPairList<Integer, Double>> getNetworkUtilizations(final Dataset networkIODataset,
			final Dataset networkInfoDataset, final SpotterResult result, final List<Integer> numUsersList) {
		final Map<String, NumericPairList<Integer, Double>> resultMap = new HashMap<>();

		for (final String processID : networkInfoDataset.getValueSet(NetworkInterfaceInfoRecord.PAR_PROCESS_ID, String.class)) {
			final ParameterSelection processSelection = ParameterSelection.newSelection().select(
					NetworkInterfaceInfoRecord.PAR_PROCESS_ID, processID);
			final Dataset processSpecificDataset = processSelection.applyTo(networkInfoDataset);
			for (final String nwInterfaceName : processSpecificDataset.getValueSet(
					NetworkInterfaceInfoRecord.PAR_NETWORK_INTERFACE, String.class)) {
				final ParameterSelection nwInterfaceSelection = ParameterSelection.newSelection().select(
						NetworkInterfaceInfoRecord.PAR_NETWORK_INTERFACE, nwInterfaceName);
				final Dataset nwInterfaceSpecificDataset = nwInterfaceSelection.applyTo(processSpecificDataset);
				long tmpSpeed = nwInterfaceSpecificDataset.getValues(NetworkInterfaceInfoRecord.PAR_INTERFACE_SPEED,
						Long.class).get(0);
				if (tmpSpeed < 0L) {
					tmpSpeed = SPEED_100MBIT;
				}
				final double speed = (tmpSpeed) / 8.0;
				final NumericPairList<Integer, Double> networkUtilPairList = new NumericPairList<>();

				for (final Integer numUsers : numUsersList) {
					if (tmpSpeed == 0L) {
						networkUtilPairList.add(numUsers, 0.0);
						continue;
					}
					final Dataset tmpNetowrkIODataset = ParameterSelection.newSelection()
							.select(AbstractDetectionController.NUMBER_OF_USERS_KEY, numUsers)
							.select(NetworkRecord.PAR_PROCESS_ID, processID)
							.select(NetworkRecord.PAR_NETWORK_INTERFACE, nwInterfaceName).applyTo(networkIODataset);

					final List<Long> timestamps = tmpNetowrkIODataset.getValues(NetworkRecord.PAR_TIMESTAMP, Long.class);
					final long minTimestamp = LpeNumericUtils.min(timestamps);
					final long maxTimestamp = LpeNumericUtils.max(timestamps);

					final List<Long> receivedBytes = tmpNetowrkIODataset.getValues(NetworkRecord.PAR_RECEIVED_BYTES,
							Long.class);
					final long minReceivedBytes = LpeNumericUtils.min(receivedBytes);
					final long maxReceivedBytes = LpeNumericUtils.max(receivedBytes);

					final List<Long> transferredBytes = tmpNetowrkIODataset.getValues(NetworkRecord.PAR_TRANSFERRED_BYTES,
							Long.class);
					final long minTransferredBytes = LpeNumericUtils.min(transferredBytes);
					final long maxTransferredBytes = LpeNumericUtils.max(transferredBytes);

					final double utilReceived = (((double) (MS_IN_SECOND * (maxReceivedBytes - minReceivedBytes))) / ((double) (maxTimestamp - minTimestamp)))
							/ speed;
					final double utilTransferred = (((double) (MS_IN_SECOND * (maxTransferredBytes - minTransferredBytes))) / ((double) (maxTimestamp - minTimestamp)))
							/ speed;
					networkUtilPairList.add(numUsers, Math.max(utilReceived, utilTransferred));
				}

				resultMap.put(processID + " - " + nwInterfaceName, networkUtilPairList);

			}
		}
		return resultMap;
	}

	private Map<String, Integer> getNumberServers(final Dataset networkInfoDataset) {
		final Map<String, Integer> numServers = new HashMap<>();
		for (final String processID : networkInfoDataset.getValueSet(NetworkInterfaceInfoRecord.PAR_PROCESS_ID, String.class)) {
			final ParameterSelection processSelection = ParameterSelection.newSelection().select(
					NetworkInterfaceInfoRecord.PAR_PROCESS_ID, processID);
			final Dataset processSpecificDataset = processSelection.applyTo(networkInfoDataset);
			for (final String nwInterfaceName : processSpecificDataset.getValueSet(
					NetworkInterfaceInfoRecord.PAR_NETWORK_INTERFACE, String.class)) {
				numServers.put(processID + " - " + nwInterfaceName, 1);
			}
		}

		return numServers;
	}

	private Map<String, Integer> getNumberOfCPUCores(final Dataset cpuUtilDataset) {
		final Map<String, Integer> cpuNumCores = new HashMap<>();
		for (final String processID : cpuUtilDataset.getValueSet(CPUUtilizationRecord.PAR_PROCESS_ID, String.class)) {
			final ParameterSelection selection = new ParameterSelection().select(CPUUtilizationRecord.PAR_PROCESS_ID,
					processID);
			final int numCores = selection.applyTo(cpuUtilDataset).getValueSet(CPUUtilizationRecord.PAR_CPU_ID).size() - 1;

			cpuNumCores.put(processID + " - " + CPUUtilizationRecord.RES_CPU_AGGREGATED, numCores);
		}
		return cpuNumCores;
	}

	@Override
	public void setProblemDetectionConfiguration(final Properties problemDetectionConfiguration) {
		scope = problemDetectionConfiguration.getProperty(OLBExtension.SCOPE_KEY, OLBExtension.ENTRY_SCOPE);

	}

	@Override
	public void setMainDetectionController(final OLBDetectionController mainDetectionController) {
		this.mainDetectionController = mainDetectionController;

	}

}
