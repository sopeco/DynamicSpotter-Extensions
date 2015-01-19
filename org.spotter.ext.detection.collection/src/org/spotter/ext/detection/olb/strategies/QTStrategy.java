package org.spotter.ext.detection.olb.strategies;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.aim.api.measurement.dataset.Dataset;
import org.aim.api.measurement.dataset.DatasetCollection;
import org.aim.api.measurement.dataset.ParameterSelection;
import org.aim.artifacts.records.CPUUtilizationRecord;
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

		List<Integer> numUsersList = getNumUsersList(rtDataset);
		Map<String, NumericPairList<Integer, Double>> responseTimesMap = analyseOperationResponseTimes(rtDataset,
				result, numUsersList);
		Map<String, NumericPairList<Integer, Double>> cpuUtilsMap = analyseCPUUtilizations(cpuUtilDataset, result,
				numUsersList);
		Map<String, Integer> cpuNumCores = getNumberOfCPUCores(cpuUtilDataset);

		operationLoop: for (String operation : responseTimesMap.keySet()) {
			NumericPairList<Integer, Double> responseTimes = responseTimesMap.get(operation);
			double singleUserResponseTime = getValueForNumUsers(responseTimes, responseTimes.getKeyMin());
			for (String cpuProcessId : cpuUtilsMap.keySet()) {
				// prepare chart data container
				NumericPairList<Integer, Double> rtThresholdsForChart = new NumericPairList<>();
				NumericPairList<Integer, Double> cpuUtils = cpuUtilsMap.get(cpuProcessId);
				int numCores = cpuNumCores.get(cpuProcessId);
				boolean detected = false;
				for (int numUsers : numUsersList) {
					double responseTime = getValueForNumUsers(responseTimes, numUsers);
					double cpuUtil = getValueForNumUsers(cpuUtils, numUsers);

					// response time threshold derived from queueing theory for
					// multi-server queues
					double rtThreshold = singleUserResponseTime / (1 - cpuUtil)
							* LpeNumericUtils.calculateErlangsCFormula(numCores, cpuUtil) + numCores
							* singleUserResponseTime;

					if (responseTime > rtThreshold * ONE_PLUS_EPSILON) {
						detected = true;

					}

					rtThresholdsForChart.add(numUsers, rtThreshold);

				}

				AnalysisChartBuilder chartBuilder = new AnalysisChartBuilder();
				chartBuilder.startChart(operation + " - " + cpuProcessId, "number of users", "Response Time [ms]");
				chartBuilder.addScatterSeries(responseTimes, "Avg. Response Times");
				chartBuilder.addLineSeries(rtThresholdsForChart, "Threshold for Response Times");
				mainDetectionController.getResultManager().storeImageChartResource(chartBuilder.build(),
						"Response Times", result);

				chartBuilder = new AnalysisChartBuilder();
				chartBuilder.startChart("CPU on " + cpuProcessId, "number of users", "Utilization [%]");
				chartBuilder.addUtilizationLineSeries(cpuUtils, "CPU Utilization", true);
				mainDetectionController.getResultManager().storeImageChartResource(chartBuilder.build(),
						"CPU Utilization", result);
				if (detected) {
					result.setDetected(true);
					result.addMessage("OLB detected in service: " + operation);
					continue operationLoop;
				}

			}
		}

		return result;
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
			resultMap.put(processID, cpuUtilPairList);
		}
		return resultMap;
	}

	private Map<String, Integer> getNumberOfCPUCores(Dataset cpuUtilDataset) {
		Map<String, Integer> cpuNumCores = new HashMap<>();
		for (String processID : cpuUtilDataset.getValueSet(CPUUtilizationRecord.PAR_PROCESS_ID, String.class)) {
			ParameterSelection selection = new ParameterSelection().select(CPUUtilizationRecord.PAR_PROCESS_ID,
					processID);
			int numCores = selection.applyTo(cpuUtilDataset).getValueSet(CPUUtilizationRecord.PAR_CPU_ID).size() - 1;
			cpuNumCores.put(processID, numCores);
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
