package org.spotter.ext.detection.trafficJam.strategies;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.aim.api.measurement.dataset.Dataset;
import org.aim.api.measurement.dataset.ParameterSelection;
import org.aim.artifacts.records.ResponseTimeRecord;
import org.lpe.common.util.LpeNumericUtils;
import org.lpe.common.util.NumericPairList;
import org.spotter.core.chartbuilder.AnalysisChartBuilder;
import org.spotter.core.detection.AbstractDetectionController;
import org.spotter.ext.detection.trafficJam.ITrafficJamStrategy;
import org.spotter.ext.detection.trafficJam.TrafficJamDetectionController;
import org.spotter.ext.detection.trafficJam.TrafficJamExtension;
import org.spotter.shared.result.model.SpotterResult;

public class TTestStrategy implements ITrafficJamStrategy {

	private int requiredSignificantSteps;
	private double requiredSignificanceLevel;
	private TrafficJamDetectionController mainDetectionController;

	@Override
	public boolean analyseOperationResponseTimes(Dataset dataset, String operation, SpotterResult result) {
		int prevNumUsers = -1;
		int firstSignificantNumUsers = -1;
		int significantSteps = 0;
		List<Integer> sortedNumUsersList = new ArrayList<Integer>(dataset.getValueSet(
				AbstractDetectionController.NUMBER_OF_USERS_KEY, Integer.class));
		Collections.sort(sortedNumUsersList);
		int minNumUsers = sortedNumUsersList.get(0);
		NumericPairList<Integer, Double> rawData = new NumericPairList<>();
		NumericPairList<Integer, Double> means = new NumericPairList<>();
		List<Number> ci = new ArrayList<>();
		for (Integer numUsers : sortedNumUsersList) {
			if (prevNumUsers > 0) {
				ParameterSelection selectionCurrent = new ParameterSelection().select(
						AbstractDetectionController.NUMBER_OF_USERS_KEY, numUsers).select(
						ResponseTimeRecord.PAR_OPERATION, operation);
				ParameterSelection selectionPrev = new ParameterSelection().select(
						AbstractDetectionController.NUMBER_OF_USERS_KEY, prevNumUsers).select(
						ResponseTimeRecord.PAR_OPERATION, operation);

				List<Long> currentValues = LpeNumericUtils.filterOutliersUsingIQR(selectionCurrent.applyTo(dataset)
						.getValues(ResponseTimeRecord.PAR_RESPONSE_TIME, Long.class));
				List<Long> prevValues = LpeNumericUtils.filterOutliersUsingIQR(selectionPrev.applyTo(dataset)
						.getValues(ResponseTimeRecord.PAR_RESPONSE_TIME, Long.class));

				List<Double> sums1 = new ArrayList<>();
				List<Double> sums2 = new ArrayList<>();
				LpeNumericUtils.createNormalDistributionByBootstrapping(prevValues, currentValues, sums1, sums2);

				if (sums2.size() < 2 || sums1.size() < 2) {
					throw new IllegalArgumentException("Traffic Jam detection failed for the operation '" + operation
							+ "', because there are not enough measurement points for a t-test.");
				}
				double prevMean = LpeNumericUtils.average(sums1);
				double currentMean = LpeNumericUtils.average(sums2);

				double pValue = LpeNumericUtils.tTest(sums2, sums1);
				if (pValue >= 0 && pValue <= requiredSignificanceLevel && prevMean < currentMean) {
					if (firstSignificantNumUsers < 0) {
						firstSignificantNumUsers = prevNumUsers;
					}
					significantSteps++;
				} else {
					firstSignificantNumUsers = -1;
					significantSteps = 0;
				}

				// update chart data
				if (prevNumUsers == minNumUsers) {
					double stdDev = LpeNumericUtils.stdDev(sums1);
					for (Double val : sums1) {
						rawData.add(prevNumUsers, val);
					}
					double ciWidth = LpeNumericUtils.getConfidenceIntervalWidth(sums1.size(), stdDev,
							requiredSignificanceLevel);
					means.add(prevNumUsers, prevMean);
					ci.add(ciWidth / 2.0);
				}

				double stdDev = LpeNumericUtils.stdDev(sums2);
				for (Double val : sums2) {
					rawData.add(numUsers, val);
				}
				double ciWidth = LpeNumericUtils.getConfidenceIntervalWidth(sums2.size(), stdDev,
						requiredSignificanceLevel);
				means.add(numUsers, currentMean);
				ci.add(ciWidth / 2.0);
			}
			prevNumUsers = numUsers;

		}

		AnalysisChartBuilder chartBuilder = AnalysisChartBuilder.getChartBuilder();
		chartBuilder.startChart(operation, "Number of Users", "Response Time [ms]");
		chartBuilder.addScatterSeries(rawData, "Response Times");
		mainDetectionController.getResultManager().storeImageChartResource(chartBuilder, "Response Times",
				result);

		chartBuilder = AnalysisChartBuilder.getChartBuilder();
		chartBuilder.startChart(operation, "Number of Users", "CI: Response Times [ms]");
		chartBuilder.addScatterSeriesWithErrorBars(means, ci, "Response Times");
		mainDetectionController.getResultManager().storeImageChartResource(chartBuilder,
				"Confidence Intervals", result);

		if (firstSignificantNumUsers > 0 && significantSteps >= requiredSignificantSteps) {
			return true;
		}
		return false;
	}

	@Override
	public void setProblemDetectionConfiguration(Properties problemDetectionConfiguration) {
		String requiredSignificantStepsStr = problemDetectionConfiguration
				.getProperty(TrafficJamExtension.REQUIRED_SIGNIFICANT_STEPS_KEY);
		requiredSignificantSteps = requiredSignificantStepsStr != null ? Integer.parseInt(requiredSignificantStepsStr)
				: TrafficJamExtension.REQUIRED_SIGNIFICANT_STEPS_DEFAULT;

		String requiredConfidenceLevelStr = problemDetectionConfiguration
				.getProperty(TrafficJamExtension.REQUIRED_CONFIDENCE_LEVEL_KEY);
		requiredSignificanceLevel = 1.0 - (requiredConfidenceLevelStr != null ? Double
				.parseDouble(requiredConfidenceLevelStr) : TrafficJamExtension.REQUIRED_CONFIDENCE_LEVEL_DEFAULT);

	}

	@Override
	public void setMainDetectionController(TrafficJamDetectionController mainDetectionController) {
		this.mainDetectionController = mainDetectionController;
	}

}
