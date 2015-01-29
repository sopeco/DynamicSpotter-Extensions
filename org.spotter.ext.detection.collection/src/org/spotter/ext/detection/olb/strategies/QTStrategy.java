package org.spotter.ext.detection.olb.strategies;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.aim.api.measurement.dataset.Dataset;
import org.aim.api.measurement.dataset.DatasetCollection;
import org.aim.api.measurement.dataset.ParameterSelection;
import org.aim.artifacts.records.CPUUtilizationRecord;
import org.aim.artifacts.records.NetworkInterfaceInfoRecord;
import org.aim.artifacts.records.NetworkRecord;
import org.aim.artifacts.records.ResponseTimeRecord;
import org.lpe.common.util.LpeNumericUtils;
import org.lpe.common.util.NumericPair;
import org.lpe.common.util.NumericPairList;
import org.spotter.core.detection.AbstractDetectionController;
import org.spotter.ext.detection.olb.IOLBAnalysisStrategy;
import org.spotter.ext.detection.olb.OLBDetectionController;
import org.spotter.ext.detection.utils.AnalysisChartBuilder;
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

	@Override
	public SpotterResult analyze(DatasetCollection data) {
		SpotterResult result = new SpotterResult();

		Dataset rtDataset = data.getDataSet(ResponseTimeRecord.class);

		if (rtDataset == null || rtDataset.size() == 0) {
			result.setDetected(false);
			result.addMessage("Instrumentation achieved no response time results for the given scope!");
			return result;
		}

		Dataset cpuUtilDataset = data.getDataSet(CPUUtilizationRecord.class);

		if (cpuUtilDataset == null || cpuUtilDataset.size() == 0) {
			result.setDetected(false);
			result.addMessage("Instrumentation achieved no CPU utilization results for the given scope!");
			return result;
		}

		Dataset networkInfoDataset = data.getDataSet(NetworkInterfaceInfoRecord.class);

		if (networkInfoDataset == null || networkInfoDataset.size() == 0) {
			result.setDetected(false);
			result.addMessage("Instrumentation achieved no networkInfoDataset results for the given scope!");
			return result;
		}

		Dataset networkIODataset = data.getDataSet(NetworkRecord.class);

		if (networkIODataset == null || networkIODataset.size() == 0) {
			result.setDetected(false);
			result.addMessage("Instrumentation achieved no netowrk IO results for the given scope!");
			return result;
		}

		List<Integer> numUsersList = getNumUsersList(rtDataset);
		Map<String, NumericPairList<Integer, Double>> responseTimesMap = analyseOperationResponseTimes(rtDataset,
				result, numUsersList);
		Map<String, NumericPairList<Integer, Double>> utilsMap = analyseCPUUtilizations(cpuUtilDataset, result,
				numUsersList);
		utilsMap.putAll(analyseNetworkUtilizations(networkIODataset, networkInfoDataset, result, numUsersList));
		Map<String, Integer> numServersMap = getNumberOfCPUCores(cpuUtilDataset);
		numServersMap.putAll(getNumberServers(networkInfoDataset));

		analyzeThreshold(result, numUsersList, responseTimesMap, utilsMap, numServersMap);

		return result;
	}

	private void analyzeThreshold(SpotterResult result, List<Integer> numUsersList,
			Map<String, NumericPairList<Integer, Double>> responseTimesMap,
			Map<String, NumericPairList<Integer, Double>> utilsMap, Map<String, Integer> numServersMap) {
		Set<String> utilsChartsCreatedFor = new HashSet<>();
		for (String operation : responseTimesMap.keySet()) {
			NumericPairList<Integer, Double> responseTimes = responseTimesMap.get(operation);
			double singleUserResponseTime = getValueForNumUsers(responseTimes, responseTimes.getKeyMin());
			boolean detected = true;
			for (String rersourceID : utilsMap.keySet()) {
				// prepare chart data container
				NumericPairList<Integer, Double> rtThresholdsForChart = new NumericPairList<>();
				NumericPairList<Integer, Double> utils = utilsMap.get(rersourceID);
				int numServers = numServersMap.get(rersourceID);

				boolean responseTimesUnderThresholdCurve = true;
				for (int numUsers : numUsersList) {
					double responseTime = getValueForNumUsers(responseTimes, numUsers);
					double utilization = getValueForNumUsers(utils, numUsers);

					// response time threshold derived from queueing theory for
					// multi-server queues
					double rtThreshold = singleUserResponseTime / (1 - utilization)
							* LpeNumericUtils.calculateErlangsCFormula(numServers, utilization) + numServers
							* singleUserResponseTime;

					if (responseTime > rtThreshold * ONE_PLUS_EPSILON) {
						responseTimesUnderThresholdCurve = false;

					}

					rtThresholdsForChart.add(numUsers, rtThreshold);

				}

				createChart(result, utilsChartsCreatedFor, operation, responseTimes, rersourceID, rtThresholdsForChart,
						utils);

				if (responseTimesUnderThresholdCurve) {
					detected = false;
				}

			}

			if (detected) {
				result.setDetected(true);
				result.addMessage("OLB detected in service: " + operation);
			}
		}
	}

	private void createChart(SpotterResult result, Set<String> utilsChartsCreatedFor, String operation,
			NumericPairList<Integer, Double> responseTimes, String resourceId,
			NumericPairList<Integer, Double> rtThresholdsForChart, NumericPairList<Integer, Double> cpuUtils) {
		AnalysisChartBuilder chartBuilder = new AnalysisChartBuilder();
		chartBuilder.startChart(operation + " - " + resourceId, "number of users", "Response Time [ms]");
		chartBuilder.addScatterSeries(responseTimes, "Avg. Response Times");
		chartBuilder.addLineSeries(rtThresholdsForChart, "Threshold for Response Times");
		mainDetectionController.getResultManager().storeImageChartResource(chartBuilder.build(), "Response Times",
				result);
		if (!utilsChartsCreatedFor.contains(resourceId)) {
			chartBuilder = new AnalysisChartBuilder();
			chartBuilder.startChart("CPU on " + resourceId, "number of users", "Utilization [%]");
			chartBuilder.addUtilizationLineSeries(cpuUtils, "Utilization", true);
			mainDetectionController.getResultManager().storeImageChartResource(chartBuilder.build(),
					"Utilization-" + resourceId, result);
			utilsChartsCreatedFor.add(resourceId);
		}
	}

	private double getValueForNumUsers(NumericPairList<Integer, Double> pairList, int numUsers) {
		for (NumericPair<Integer, Double> pair : pairList) {
			if (pair.getKey().equals(numUsers)) {
				return pair.getValue();
			}
		}
		throw new IllegalArgumentException("Data not found!");
	}

	private List<Integer> getNumUsersList(Dataset rtDataset) {
		List<Integer> numUsersList = new ArrayList<>(rtDataset.getValueSet(
				AbstractDetectionController.NUMBER_OF_USERS_KEY, Integer.class));
		Collections.sort(numUsersList);
		return numUsersList;
	}

	private Map<String, NumericPairList<Integer, Double>> analyseOperationResponseTimes(Dataset rtDataset,
			SpotterResult result, final List<Integer> numUsersList) {

		Map<String, NumericPairList<Integer, Double>> resultMap = new HashMap<>();

		operationLoop: for (String operation : rtDataset.getValueSet(ResponseTimeRecord.PAR_OPERATION, String.class)) {
			NumericPairList<Integer, Double> responseTimePairList = new NumericPairList<>();
			for (Integer numUsers : numUsersList) {

				ParameterSelection selection = new ParameterSelection().select(
						AbstractDetectionController.NUMBER_OF_USERS_KEY, numUsers).select(
						ResponseTimeRecord.PAR_OPERATION, operation);

				Dataset tmpRTDataset = selection.applyTo(rtDataset);

				if (tmpRTDataset == null || tmpRTDataset.size() == 0) {
					result.addMessage("One Lane Bridge detection failed for the operation '" + operation
							+ "', because the operation was not executed in each analysis cycle. "
							+ "Hence, the operation cannot be analyzed for an OLB.");
					continue operationLoop;
				}

				List<Long> responseTimes = tmpRTDataset.getValues(ResponseTimeRecord.PAR_RESPONSE_TIME, Long.class);
				double meanResponseTime = LpeNumericUtils.average(responseTimes);
				responseTimePairList.add(numUsers, meanResponseTime);
			}
			resultMap.put(operation, responseTimePairList);
		}
		return resultMap;
	}

	private Map<String, NumericPairList<Integer, Double>> analyseCPUUtilizations(Dataset cpuUtilDataset,
			SpotterResult result, final List<Integer> numUsersList) {
		Map<String, NumericPairList<Integer, Double>> resultMap = new HashMap<>();

		for (String processID : cpuUtilDataset.getValueSet(CPUUtilizationRecord.PAR_PROCESS_ID, String.class)) {
			NumericPairList<Integer, Double> cpuUtilPairList = new NumericPairList<>();
			for (Integer numUsers : numUsersList) {

				ParameterSelection selection = new ParameterSelection()
						.select(AbstractDetectionController.NUMBER_OF_USERS_KEY, numUsers)
						.select(CPUUtilizationRecord.PAR_PROCESS_ID, processID)
						.select(CPUUtilizationRecord.PAR_CPU_ID, CPUUtilizationRecord.RES_CPU_AGGREGATED);
				Dataset tmpCPUUtilDataset = selection.applyTo(cpuUtilDataset);
				List<Double> cpuUtils = tmpCPUUtilDataset.getValues(CPUUtilizationRecord.PAR_UTILIZATION, Double.class);
				double meanCPUUtil = LpeNumericUtils.average(cpuUtils);
				cpuUtilPairList.add(numUsers, meanCPUUtil);
			}
			resultMap.put(processID + " - " + CPUUtilizationRecord.RES_CPU_AGGREGATED, cpuUtilPairList);
		}
		return resultMap;
	}

	private Map<String, NumericPairList<Integer, Double>> analyseNetworkUtilizations(Dataset networkIODataset,
			Dataset networkInfoDataset, SpotterResult result, final List<Integer> numUsersList) {
		Map<String, NumericPairList<Integer, Double>> resultMap = new HashMap<>();

		for (String processID : networkInfoDataset.getValueSet(NetworkInterfaceInfoRecord.PAR_PROCESS_ID, String.class)) {
			ParameterSelection processSelection = ParameterSelection.newSelection().select(
					NetworkInterfaceInfoRecord.PAR_PROCESS_ID, processID);
			Dataset processSpecificDataset = processSelection.applyTo(networkInfoDataset);
			for (String nwInterfaceName : processSpecificDataset.getValueSet(
					NetworkInterfaceInfoRecord.PAR_NETWORK_INTERFACE, String.class)) {
				ParameterSelection nwInterfaceSelection = ParameterSelection.newSelection().select(
						NetworkInterfaceInfoRecord.PAR_NETWORK_INTERFACE, nwInterfaceName);
				Dataset nwInterfaceSpecificDataset = nwInterfaceSelection.applyTo(processSpecificDataset);
				long speed = nwInterfaceSpecificDataset.getValues(NetworkInterfaceInfoRecord.PAR_INTERFACE_SPEED,
						Long.class).get(0);
				
				NumericPairList<Integer, Double> networkUtilPairList = new NumericPairList<>();

				for (Integer numUsers : numUsersList) {
					if (speed <= 0L) {
						networkUtilPairList.add(numUsers, 0.0);
						continue;
					}
					Dataset tmpNetowrkIODataset = processSelection.applyTo(networkIODataset);
					tmpNetowrkIODataset = nwInterfaceSelection.applyTo(tmpNetowrkIODataset);

					List<Long> timestamps = tmpNetowrkIODataset.getValues(NetworkRecord.PAR_TIMESTAMP, Long.class);
					long minTimestamp = LpeNumericUtils.min(timestamps);
					long maxTimestamp = LpeNumericUtils.max(timestamps);

					List<Long> receivedBytes = tmpNetowrkIODataset.getValues(NetworkRecord.PAR_RECEIVED_BYTES,
							Long.class);
					long minReceivedBytes = LpeNumericUtils.min(receivedBytes);
					long maxReceivedBytes = LpeNumericUtils.max(receivedBytes);

					List<Long> transferredBytes = tmpNetowrkIODataset.getValues(NetworkRecord.PAR_TRANSFERRED_BYTES,
							Long.class);
					long minTransferredBytes = LpeNumericUtils.min(transferredBytes);
					long maxTransferredBytes = LpeNumericUtils.max(transferredBytes);

					double utilReceived = (((double) (MS_IN_SECOND * (maxReceivedBytes - minReceivedBytes))) / ((double) (maxTimestamp - minTimestamp)))
							/ (double) speed;
					double utilTransferred = (((double) (MS_IN_SECOND * (maxTransferredBytes - minTransferredBytes))) / ((double) (maxTimestamp - minTimestamp)))
							/ (double) speed;
					networkUtilPairList.add(numUsers, Math.max(utilReceived, utilTransferred));
				}

				resultMap.put(processID + " - " + nwInterfaceName, networkUtilPairList);

			}
		}
		return resultMap;
	}

	private Map<String, Integer> getNumberServers(Dataset networkInfoDataset) {
		Map<String, Integer> numServers = new HashMap<>();
		for (String processID : networkInfoDataset.getValueSet(NetworkInterfaceInfoRecord.PAR_PROCESS_ID, String.class)) {
			ParameterSelection processSelection = ParameterSelection.newSelection().select(
					NetworkInterfaceInfoRecord.PAR_PROCESS_ID, processID);
			Dataset processSpecificDataset = processSelection.applyTo(networkInfoDataset);
			for (String nwInterfaceName : processSpecificDataset.getValueSet(
					NetworkInterfaceInfoRecord.PAR_NETWORK_INTERFACE, String.class)) {
				numServers.put(processID + " - " + nwInterfaceName, 1);
			}
		}

		return numServers;
	}

	private Map<String, Integer> getNumberOfCPUCores(Dataset cpuUtilDataset) {
		Map<String, Integer> cpuNumCores = new HashMap<>();
		for (String processID : cpuUtilDataset.getValueSet(CPUUtilizationRecord.PAR_PROCESS_ID, String.class)) {
			ParameterSelection selection = new ParameterSelection().select(CPUUtilizationRecord.PAR_PROCESS_ID,
					processID);
			int numCores = selection.applyTo(cpuUtilDataset).getValueSet(CPUUtilizationRecord.PAR_CPU_ID).size() - 1;
			cpuNumCores.put(processID + " - " + CPUUtilizationRecord.RES_CPU_AGGREGATED, numCores);
		}
		return cpuNumCores;
	}

	@Override
	public void setProblemDetectionConfiguration(Properties problemDetectionConfiguration) {
		// TODO Auto-generated method stub

	}

	@Override
	public void setMainDetectionController(OLBDetectionController mainDetectionController) {
		this.mainDetectionController = mainDetectionController;

	}

}
