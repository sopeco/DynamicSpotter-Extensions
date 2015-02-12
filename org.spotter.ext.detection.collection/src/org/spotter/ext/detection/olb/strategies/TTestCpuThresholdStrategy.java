package org.spotter.ext.detection.olb.strategies;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.aim.api.measurement.dataset.Dataset;
import org.aim.api.measurement.dataset.DatasetCollection;
import org.aim.api.measurement.dataset.ParameterSelection;
import org.aim.artifacts.records.CPUUtilizationRecord;
import org.lpe.common.util.LpeNumericUtils;
import org.lpe.common.util.NumericPairList;
import org.spotter.core.chartbuilder.AnalysisChartBuilder;
import org.spotter.core.detection.AbstractDetectionController;
import org.spotter.ext.detection.olb.IOLBAnalysisStrategy;
import org.spotter.ext.detection.olb.OLBDetectionController;
import org.spotter.ext.detection.olb.OLBExtension;
import org.spotter.shared.result.model.SpotterResult;

public class TTestCpuThresholdStrategy implements IOLBAnalysisStrategy {
	private static final double PER_PERCENT = 0.01;
	private OLBDetectionController mainDetectionController;
	private double cpuThreshold;

	@Override
	public SpotterResult analyze(DatasetCollection data) {
		SpotterResult result = new SpotterResult();

		Dataset cpuUtilDataset = data.getDataSet(CPUUtilizationRecord.class);
		if (cpuUtilDataset == null) {
			result.addMessage("Unable to analyze CPU utilization: CPU utilization data has not been gathered.");
			result.setDetected(false);
			return result;
		} else {
			for (String processId : cpuUtilDataset.getValueSet(CPUUtilizationRecord.PAR_PROCESS_ID, String.class)) {
				boolean cpuUtilized = cpuUtilized(cpuUtilDataset, processId, result);

				if (cpuUtilized) {
					result.addMessage("CPU Utilization is quite high. The CPU is probably a bottleneck!");
					result.setDetected(false);
					return result;
				}
			}

		}
		result.setDetected(true);
		result.addMessage("None of the hardware resources is utilized to capacity!");

		return result;
	}

	private boolean cpuUtilized(Dataset cpuUtilDataset, String processId, SpotterResult result) {

		NumericPairList<Integer, Double> cpuMeans = new NumericPairList<>();
		boolean cpuUtilized = false;

		ParameterSelection parSelection = new ParameterSelection();
		parSelection.select(CPUUtilizationRecord.PAR_CPU_ID, CPUUtilizationRecord.RES_CPU_AGGREGATED).select(
				CPUUtilizationRecord.PAR_PROCESS_ID, processId);

		for (Integer numUsers : cpuUtilDataset.getValueSet(AbstractDetectionController.NUMBER_OF_USERS_KEY,
				Integer.class)) {
			parSelection.select(AbstractDetectionController.NUMBER_OF_USERS_KEY, numUsers);
			Dataset selectedDataSet = parSelection.applyTo(cpuUtilDataset);

			double meanCpuUtil = LpeNumericUtils.average(selectedDataSet.getValues(
					CPUUtilizationRecord.PAR_UTILIZATION, Double.class));
			if (meanCpuUtil >= cpuThreshold) {
				cpuUtilized = true;
			}
			cpuMeans.add(numUsers, meanCpuUtil);
		}

		AnalysisChartBuilder chartBuilder = AnalysisChartBuilder.getChartBuilder();
		chartBuilder.startChart("CPU Utilization - " + processId, "number of users", "Mean Utilization [%]");
		chartBuilder.addUtilizationLineSeries(cpuMeans, "CPU Utilization", true);
		mainDetectionController.getResultManager().storeImageChartResource(chartBuilder, "CPU Utilization",
				result);
		return cpuUtilized;
	}

	@Override
	public void setProblemDetectionConfiguration(Properties problemDetectionConfiguration) {

	}

	@Override
	public void setMainDetectionController(OLBDetectionController mainDetectionController) {
		this.mainDetectionController = mainDetectionController;

	}

}
