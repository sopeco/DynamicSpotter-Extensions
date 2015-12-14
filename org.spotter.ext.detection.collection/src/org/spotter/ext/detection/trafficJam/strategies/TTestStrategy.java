package org.spotter.ext.detection.trafficJam.strategies;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.aim.api.measurement.dataset.Dataset;
import org.aim.api.measurement.dataset.ParameterSelection;
import org.aim.artifacts.records.ResponseTimeRecord;
import org.lpe.common.utils.numeric.LpeNumericUtils;
import org.lpe.common.utils.numeric.NumericPairList;
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
	public boolean analyseOperationResponseTimes(final Dataset dataset, final String operation, final SpotterResult result) {
		try {
			int prevNumUsers = -1;
			int firstSignificantNumUsers = -1;
			int significantSteps = 0;
			final List<Integer> sortedNumUsersList = new ArrayList<Integer>(dataset.getValueSet(
					AbstractDetectionController.NUMBER_OF_USERS_KEY, Integer.class));
			Collections.sort(sortedNumUsersList);
			final int minNumUsers = sortedNumUsersList.get(0);
			final NumericPairList<Integer, Double> rawData = new NumericPairList<>();
			final NumericPairList<Integer, Double> means = new NumericPairList<>();
			final List<Number> ci = new ArrayList<>();
			for (final Integer numUsers : sortedNumUsersList) {
				if (prevNumUsers > 0) {
					final ParameterSelection selectionCurrent = new ParameterSelection().select(
							AbstractDetectionController.NUMBER_OF_USERS_KEY, numUsers).select(
							ResponseTimeRecord.PAR_OPERATION, operation);
					final ParameterSelection selectionPrev = new ParameterSelection().select(
							AbstractDetectionController.NUMBER_OF_USERS_KEY, prevNumUsers).select(
							ResponseTimeRecord.PAR_OPERATION, operation);

					final List<Long> currentValues = LpeNumericUtils.filterOutliersUsingIQR(selectionCurrent.applyTo(dataset)
							.getValues(ResponseTimeRecord.PAR_RESPONSE_TIME, Long.class));
					final List<Long> prevValues = LpeNumericUtils.filterOutliersUsingIQR(selectionPrev.applyTo(dataset)
							.getValues(ResponseTimeRecord.PAR_RESPONSE_TIME, Long.class));

					final List<Double> sums1 = new ArrayList<>();
					final List<Double> sums2 = new ArrayList<>();
					LpeNumericUtils.createNormalDistributionByBootstrapping(prevValues, currentValues, sums1, sums2);

					if (sums2.size() < 2 || sums1.size() < 2) {
						throw new IllegalArgumentException("Traffic Jam detection failed for the operation '"
								+ operation + "', because there are not enough measurement points for a t-test.");
					}
					final double prevMean = LpeNumericUtils.average(sums1);
					final double currentMean = LpeNumericUtils.average(sums2);

					final double pValue = LpeNumericUtils.tTest(sums2, sums1);
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
						final double stdDev = LpeNumericUtils.stdDev(sums1);
						for (final Double val : sums1) {
							rawData.add(prevNumUsers, val);
						}
						final double ciWidth = LpeNumericUtils.getConfidenceIntervalWidth(sums1.size(), stdDev,
								requiredSignificanceLevel);
						means.add(prevNumUsers, prevMean);
						ci.add(ciWidth / 2.0);
					}

					final double stdDev = LpeNumericUtils.stdDev(sums2);
					for (final Double val : sums2) {
						rawData.add(numUsers, val);
					}
					final double ciWidth = LpeNumericUtils.getConfidenceIntervalWidth(sums2.size(), stdDev,
							requiredSignificanceLevel);
					means.add(numUsers, currentMean);
					ci.add(ciWidth / 2.0);
				}
				prevNumUsers = numUsers;

			}

			AnalysisChartBuilder chartBuilder = AnalysisChartBuilder.getChartBuilder();
			final String operationName = operation.contains("(")?operation.substring(0, operation.indexOf("(")):operation;
			chartBuilder.startChart(operationName, "number of users", "response time [ms]");
			chartBuilder.addScatterSeries(rawData, "response times");
			mainDetectionController.getResultManager().storeImageChartResource(chartBuilder, "Response Times", result);

			chartBuilder = AnalysisChartBuilder.getChartBuilder();
			chartBuilder.startChart(operationName, "number of users", "response time [ms]");
			chartBuilder.addScatterSeriesWithErrorBars(means, ci, "avg. response times");
			mainDetectionController.getResultManager().storeImageChartResource(chartBuilder, "Confidence Intervals",
					result);

			if (firstSignificantNumUsers > 0 && significantSteps >= requiredSignificantSteps) {
				return true;
			}
			return false;
		} catch (final Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void setProblemDetectionConfiguration(final Properties problemDetectionConfiguration) {
		final String requiredSignificantStepsStr = problemDetectionConfiguration
				.getProperty(TrafficJamExtension.REQUIRED_SIGNIFICANT_STEPS_KEY);
		requiredSignificantSteps = requiredSignificantStepsStr != null ? Integer.parseInt(requiredSignificantStepsStr)
				: TrafficJamExtension.REQUIRED_SIGNIFICANT_STEPS_DEFAULT;

		final String requiredConfidenceLevelStr = problemDetectionConfiguration
				.getProperty(TrafficJamExtension.REQUIRED_CONFIDENCE_LEVEL_KEY);
		requiredSignificanceLevel = 1.0 - (requiredConfidenceLevelStr != null ? Double
				.parseDouble(requiredConfidenceLevelStr) : TrafficJamExtension.REQUIRED_CONFIDENCE_LEVEL_DEFAULT);

	}

	@Override
	public void setMainDetectionController(final TrafficJamDetectionController mainDetectionController) {
		this.mainDetectionController = mainDetectionController;
	}

}
