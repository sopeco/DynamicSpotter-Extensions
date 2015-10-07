package org.spotter.ext.detection.trafficJam.strategies;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.aim.api.measurement.dataset.Dataset;
import org.aim.api.measurement.dataset.ParameterSelection;
import org.aim.artifacts.records.ResponseTimeRecord;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.lpe.common.utils.numeric.LpeNumericUtils;
import org.lpe.common.utils.numeric.NumericPair;
import org.lpe.common.utils.numeric.NumericPairList;
import org.spotter.core.chartbuilder.AnalysisChartBuilder;
import org.spotter.ext.detection.trafficJam.ITrafficJamStrategy;
import org.spotter.ext.detection.trafficJam.TrafficJamDetectionController;
import org.spotter.ext.detection.trafficJam.TrafficJamExtension;
import org.spotter.ext.detection.utils.Utils;
import org.spotter.shared.result.model.SpotterResult;

public class LinearRegression implements ITrafficJamStrategy {

	private double slopeThreshold;
	private TrafficJamDetectionController mainDetectionController;

	@Override
	public void setProblemDetectionConfiguration(Properties problemDetectionConfiguration) {
		String slopeThresholdStr = problemDetectionConfiguration.getProperty(TrafficJamExtension.REGRESSION_SLOPE_KEY);
		slopeThreshold = slopeThresholdStr != null ? Double.parseDouble(slopeThresholdStr)
				: TrafficJamExtension.REGRESSION_SLOPE_DEFAULT;
	}

	@Override
	public void setMainDetectionController(TrafficJamDetectionController mainDetectionController) {
		this.mainDetectionController = mainDetectionController;

	}

	@Override
	public boolean analyseOperationResponseTimes(Dataset dataset, String operation, SpotterResult result) {
		ParameterSelection selectOperation = new ParameterSelection().select(ResponseTimeRecord.PAR_OPERATION,
				operation);
		Dataset operationSpecificDataset = selectOperation.applyTo(dataset);

		NumericPairList<Integer, Double> responseTimeSeries = Utils.toUserRTPairs(operationSpecificDataset);

		SimpleRegression regression = LpeNumericUtils.linearRegression(responseTimeSeries);

		double slope = regression.getSlope();

		createChart(result, operation, responseTimeSeries, regression);

		return slope > slopeThreshold;
	}

	private void createChart(SpotterResult result, String operation,
			NumericPairList<Integer, Double> responseTimeSeries, SimpleRegression regression) {
		NumericPairList<Long, Double> linRegressionPoints = new NumericPairList<>();
		NumericPairList<Long, Double> thresholdPoints = new NumericPairList<>();
		long minTimestamp = responseTimeSeries.getKeyMin();
		long maxTimestamp = responseTimeSeries.getKeyMax();
		double intercept = regression.predict(minTimestamp);
		linRegressionPoints.add(minTimestamp, intercept);
		linRegressionPoints.add(maxTimestamp, regression.predict(maxTimestamp));
		thresholdPoints.add(minTimestamp, intercept);
		thresholdPoints.add(maxTimestamp, slopeThreshold * (double) (maxTimestamp - minTimestamp) + intercept);

		NumericPairList<Integer, Double> means = new NumericPairList<>();
		List<Number> standDeviations = new ArrayList<>();
		int prevNumUsers = -1;
		List<Double> values = null;
		for (NumericPair<Integer, Double> pair : responseTimeSeries) {
			if (!pair.getKey().equals(prevNumUsers)) {
				if (values != null) {
					double mean = LpeNumericUtils.average(values);
					double sd = LpeNumericUtils.stdDev(values);
					means.add(prevNumUsers, mean);
					standDeviations.add(sd);

				}
				values = new ArrayList<>();
				prevNumUsers = pair.getKey();
			}
			values.add(pair.getValue());
		}
		if (values != null) {
			double mean = LpeNumericUtils.average(values);
			double sd = LpeNumericUtils.stdDev(values);
			means.add(prevNumUsers, mean);
			standDeviations.add(sd);

		}

		AnalysisChartBuilder chartBuilder = AnalysisChartBuilder.getChartBuilder();
		chartBuilder.startChart(operation, "Number of Users", "Response Time [ms]");

		chartBuilder.addTimeSeriesWithErrorBars(means, standDeviations, "Response Times");
		chartBuilder.addTimeSeriesWithLine(thresholdPoints, "Threshold Slope");
		chartBuilder.addTimeSeriesWithLine(linRegressionPoints, "Regression Slope");
		mainDetectionController.getResultManager()
				.storeImageChartResource(chartBuilder, "Ramp Detection (Lin)", result);
	}
}
