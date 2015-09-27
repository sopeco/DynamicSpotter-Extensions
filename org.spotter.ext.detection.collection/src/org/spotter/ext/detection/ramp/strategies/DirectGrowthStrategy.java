package org.spotter.ext.detection.ramp.strategies;

import java.util.ArrayList;
import java.util.List;
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
import org.lpe.common.util.LpeNumericUtils;
import org.lpe.common.util.NumericPair;
import org.lpe.common.util.NumericPairList;
import org.spotter.core.ProgressManager;
import org.spotter.core.chartbuilder.AnalysisChartBuilder;
import org.spotter.ext.detection.ramp.IRampDetectionStrategy;
import org.spotter.ext.detection.ramp.RampDetectionController;
import org.spotter.ext.detection.ramp.RampExtension;
import org.spotter.ext.detection.utils.Utils;
import org.spotter.shared.result.model.SpotterResult;

/**
 * Conducts analysis by means of a single experiment.
 * 
 * @author Alexander Wert
 * 
 */
public class DirectGrowthStrategy implements IRampDetectionStrategy {

	private RampDetectionController mainDetectionController;
	private static double requiredSignificanceLevel;

	@Override
	public void setProblemDetectionConfiguration(Properties problemDetectionConfiguration) {
		String significanceLevelStr = problemDetectionConfiguration
				.getProperty(RampExtension.KEY_REQUIRED_SIGNIFICANCE_LEVEL);
		requiredSignificanceLevel = significanceLevelStr != null ? Double.parseDouble(significanceLevelStr)
				: RampExtension.REQUIRED_SIGNIFICANCE_LEVEL_DEFAULT;
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
			long minTimestamp = responseTimeSeries.getKeyMin();
			long diff = responseTimeSeries.getKeyMax() - responseTimeSeries.getKeyMin();
			long midTimestamp = minTimestamp + (diff) / 2L;

			List<Double> firstHalf = new ArrayList<>();
			List<Double> secondHalf = new ArrayList<>();
			for (NumericPair<Long, Double> valuePair : responseTimeSeries) {
				if (valuePair.getKey() < midTimestamp) {
					firstHalf.add(valuePair.getValue());
				} else {
					secondHalf.add(valuePair.getValue());
				}
			}

			List<Double> sums1 = new ArrayList<>();
			List<Double> sums2 = new ArrayList<>();
			LpeNumericUtils.createNormalDistributionByBootstrapping(firstHalf, secondHalf, sums1, sums2);
			double firstMean = LpeNumericUtils.average(sums1);
			double secondMean = LpeNumericUtils.average(sums2);
			double pValue = LpeNumericUtils.tTest(sums1, sums2);
			if (pValue <= requiredSignificanceLevel && firstMean < secondMean) {
				result.addMessage("Ramp detected in operation: " + operation);
				result.setDetected(true);
			}
			createChart(result, operation, responseTimeSeries, minTimestamp, diff, sums1, sums2, firstMean, secondMean);

		}

		return result;
	}

	private void createChart(SpotterResult result, String operation, NumericPairList<Long, Double> responseTimeSeries,
			long minTimestamp, long diff, List<Double> sums1, List<Double> sums2, double firstMean, double secondMean) {
		double firstStdDev = LpeNumericUtils.stdDev(sums1);
		double firstCIWidth = LpeNumericUtils.getConfidenceIntervalWidth(sums1.size(), firstStdDev,
				requiredSignificanceLevel);

		double secondStdDev = LpeNumericUtils.stdDev(sums2);
		double secondCIWidth = LpeNumericUtils.getConfidenceIntervalWidth(sums2.size(), secondStdDev,
				requiredSignificanceLevel);

		AnalysisChartBuilder chartBuilder = AnalysisChartBuilder.getChartBuilder();
		chartBuilder.startChart(operation, "Experiment Time [ms]", "Response Time [ms]");
//		chartBuilder.addTimeSeries(responseTimeSeries, "Response Times");

		NumericPairList<Long, Double> means = new NumericPairList<>();
		List<Number> ci = new ArrayList<>();
		means.add(minTimestamp + diff / 4L, firstMean);
		ci.add(firstCIWidth / 2.0);
		means.add(minTimestamp + (3L * diff) / 4L, secondMean);
		ci.add(secondCIWidth / 2.0);

		chartBuilder.addTimeSeriesWithErrorBars(means, ci, "Confidence Intervals");
		mainDetectionController.getResultManager().storeImageChartResource(chartBuilder, "Ramp Detection (DG)",
				result);
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
