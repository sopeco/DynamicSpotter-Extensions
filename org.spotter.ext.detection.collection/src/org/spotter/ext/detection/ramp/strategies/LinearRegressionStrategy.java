package org.spotter.ext.detection.ramp.strategies;

import java.util.Properties;

import org.aim.aiminterface.description.instrumentation.InstrumentationDescription;
import org.aim.aiminterface.exceptions.InstrumentationException;
import org.aim.aiminterface.exceptions.MeasurementException;
import org.aim.api.measurement.dataset.Dataset;
import org.aim.api.measurement.dataset.DatasetCollection;
import org.aim.api.measurement.dataset.ParameterSelection;
import org.aim.artifacts.probes.ResponsetimeProbe;
import org.aim.artifacts.records.ResponseTimeRecord;
import org.aim.artifacts.scopes.EntryPointScope;
import org.aim.description.builder.InstrumentationDescriptionBuilder;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.lpe.common.util.LpeNumericUtils;
import org.lpe.common.util.NumericPairList;
import org.spotter.core.ProgressManager;
import org.spotter.core.chartbuilder.AnalysisChartBuilder;
import org.spotter.ext.detection.ramp.IRampDetectionStrategy;
import org.spotter.ext.detection.ramp.RampDetectionController;
import org.spotter.ext.detection.ramp.RampExtension;
import org.spotter.ext.detection.utils.Utils;
import org.spotter.shared.result.model.SpotterResult;

public class LinearRegressionStrategy implements IRampDetectionStrategy {
	private RampDetectionController mainDetectionController;
	private double slopeThreshold = RampExtension.LIN_SLOPE_DEFAULT;

	@Override
	public void setProblemDetectionConfiguration(Properties problemDetectionConfiguration) {
		String slopeThresholdStr = problemDetectionConfiguration.getProperty(RampExtension.KEY_LIN_SLOPE);
		slopeThreshold = slopeThresholdStr != null ? Double.parseDouble(slopeThresholdStr)
				: RampExtension.LIN_SLOPE_DEFAULT;

	}

	@Override
	public void setMainDetectionController(RampDetectionController mainDetectionController) {
		this.mainDetectionController = mainDetectionController;

	}

	@Override
	public void executeExperiments() throws InstrumentationException, MeasurementException {
		mainDetectionController.executeHighLoadExperiment(getInstrumentationDescription());
	}

	@Override
	public SpotterResult analyze(DatasetCollection data) {
		SpotterResult result = new SpotterResult();
		result.setDetected(false);

		Dataset rtDataset = data.getDataSet(ResponseTimeRecord.class);

		if (rtDataset == null || rtDataset.size() == 0) {
			result.setDetected(false);
			result.addMessage("Instrumentation achieved no results for the given scope!");
			return result;
		}

		for (String operation : rtDataset.getValueSet(ResponseTimeRecord.PAR_OPERATION, String.class)) {
			ParameterSelection selectOperation = new ParameterSelection().select(ResponseTimeRecord.PAR_OPERATION,
					operation);
			Dataset operationSpecificDataset = selectOperation.applyTo(rtDataset);

			NumericPairList<Long, Double> responseTimeSeries = Utils.toTimestampRTPairs(operationSpecificDataset);
			// sort chronologically
			responseTimeSeries.sort();

			SimpleRegression regression = LpeNumericUtils.linearRegression(responseTimeSeries);

			double slope = regression.getSlope();
			if (slope > slopeThreshold) {
				result.addMessage("Ramp detected in operation: " + operation);
				result.setDetected(true);
			}

			createChart(result, operation, responseTimeSeries, regression);
		}

		return result;
	}

	private void createChart(SpotterResult result, String operation, NumericPairList<Long, Double> responseTimeSeries,
			SimpleRegression regression) {
		NumericPairList<Long, Double> linRegressionPoints = new NumericPairList<>();
		NumericPairList<Long, Double> thresholdPoints = new NumericPairList<>();
		long minTimestamp = responseTimeSeries.getKeyMin();
		long maxTimestamp = responseTimeSeries.getKeyMax();
		double intercept = regression.predict(minTimestamp);
		linRegressionPoints.add(minTimestamp, intercept);
		linRegressionPoints.add(maxTimestamp, regression.predict(maxTimestamp));
		thresholdPoints.add(minTimestamp, intercept);
		thresholdPoints.add(maxTimestamp, slopeThreshold * (double) (maxTimestamp - minTimestamp) + intercept);

		AnalysisChartBuilder chartBuilder = AnalysisChartBuilder.getChartBuilder();
		chartBuilder.startChart(operation, "Experiment Time [ms]", "Response Time [ms]");
//		chartBuilder.addTimeSeries(responseTimeSeries, "Response Times");
		chartBuilder.addTimeSeriesWithLine(thresholdPoints, "Threshold Slope");
		chartBuilder.addTimeSeriesWithLine(linRegressionPoints, "Regression Slope");
		mainDetectionController.getResultManager().storeImageChartResource(chartBuilder,
				"Ramp Detection (Lin)", result);
	}

	@Override
	public long getExperimentSeriesDuration() {
		return ProgressManager.getInstance().calculateDefaultExperimentSeriesDuration(1);
	}

	public InstrumentationDescription getInstrumentationDescription() {
		InstrumentationDescriptionBuilder idBuilder = new InstrumentationDescriptionBuilder();
		idBuilder.newAPIScopeEntity(EntryPointScope.class.getName()).addProbe(ResponsetimeProbe.MODEL_PROBE)
				.entityDone();
		return idBuilder.build();

	}

}
