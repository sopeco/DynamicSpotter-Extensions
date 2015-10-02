package org.spotter.ext.detection.continuousViolation;

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
import org.lpe.common.config.GlobalConfiguration;
import org.lpe.common.extension.IExtension;
import org.lpe.common.utils.numeric.NumericPairList;
import org.spotter.core.ProgressManager;
import org.spotter.core.chartbuilder.AnalysisChartBuilder;
import org.spotter.core.detection.AbstractDetectionController;
import org.spotter.core.detection.IExperimentReuser;
import org.spotter.exceptions.WorkloadException;
import org.spotter.ext.detection.continuousViolation.strategies.BucketStrategy;
import org.spotter.ext.detection.continuousViolation.strategies.DBSCANStrategy;
import org.spotter.ext.detection.continuousViolation.strategies.MovingPercentileStrategy;
import org.spotter.ext.detection.continuousViolation.util.AnalysisConfig;
import org.spotter.ext.detection.utils.Utils;
import org.spotter.shared.configuration.ConfigKeys;
import org.spotter.shared.result.model.SpotterResult;

/**
 * Det4ection controller for continuous violation of performance requirements.
 * 
 * @author Alexander Wert
 * 
 */
public class ContinuousViolationController extends AbstractDetectionController implements IExperimentReuser {

	private String analysisStrategy;
	private final AnalysisConfig analysisConfig = new AnalysisConfig();
	private IViolationAnalysisStrategy analysisStrategyImpl;

	/**
	 * Constructor.
	 * 
	 * @param provider
	 *            extension provider
	 */
	public ContinuousViolationController(final IExtension provider) {
		super(provider);
	}

	@Override
	public void loadProperties() {
		analysisStrategy = getProblemDetectionConfiguration().getProperty(
				ContinuousViolationExtension.VIOLATION_DETECTION_STRATEGY_KEY,
				ContinuousViolationExtension.DBSCAN_STRATEGY);

		final String mvaWindowSize = getProblemDetectionConfiguration().getProperty(
				AnalysisConfig.MOVING_AVERAGE_WINDOW_SIZE_KEY,
				String.valueOf(AnalysisConfig.MOVING_AVERAGE_WINDOW_SIZE_DEFAULT));
		analysisConfig.setMvaWindowSize(Integer.parseInt(mvaWindowSize));

		final String minBucketTimeProportionStr = getProblemDetectionConfiguration().getProperty(
				AnalysisConfig.MIN_BUCKET_TIME_PROPORTION_KEY,
				String.valueOf(AnalysisConfig.MIN_BUCKET_TIME_PROPORTION_DEFAULT));
		analysisConfig.setMinBucketTimeProportion(Double.parseDouble(minBucketTimeProportionStr));

		switch (analysisStrategy) {
		case ContinuousViolationExtension.DBSCAN_STRATEGY:
			analysisStrategyImpl = new DBSCANStrategy();
			break;
		case ContinuousViolationExtension.PERCENTILE_STRATEGY:
			analysisStrategyImpl = new MovingPercentileStrategy();
			break;
		case ContinuousViolationExtension.BUCKET_STRATEGY:
			analysisStrategyImpl = new BucketStrategy();
			break;
		default:
			analysisStrategyImpl = new DBSCANStrategy();
		}
	}

	@Override
	protected SpotterResult analyze(final DatasetCollection data) {
		final double perfReqThreshold = GlobalConfiguration.getInstance().getPropertyAsInteger(
				ConfigKeys.PERFORMANCE_REQUIREMENT_THRESHOLD, ConfigKeys.DEFAULT_PERFORMANCE_REQUIREMENT_THRESHOLD);
		final double perfReqConfidence = GlobalConfiguration.getInstance().getPropertyAsDouble(
				ConfigKeys.PERFORMANCE_REQUIREMENT_CONFIDENCE, ConfigKeys.DEFAULT_PERFORMANCE_REQUIREMENT_CONFIDENCE);

		final SpotterResult result = new SpotterResult();
		result.setDetected(false);

		final Dataset rtDataset = data.getDataSet(ResponseTimeRecord.class);

		if (rtDataset == null || rtDataset.size() == 0) {
			result.setDetected(false);
			result.addMessage("Instrumentation achieved no results for the given scope!");
			return result;
		}

		for (final String operation : rtDataset.getValueSet(ResponseTimeRecord.PAR_OPERATION, String.class)) {
			final ParameterSelection selectOperation = new ParameterSelection().select(ResponseTimeRecord.PAR_OPERATION,
					operation);
			final Dataset operationSpecificDataset = selectOperation.applyTo(rtDataset);

			final NumericPairList<Long, Double> responseTimeSeries = Utils.toTimestampRTPairs(operationSpecificDataset);
			if (responseTimeSeries.size() <= 5) {
				continue;
			}
			// sort chronologically
			responseTimeSeries.sort();
			final boolean detected = analysisStrategyImpl.analyze(responseTimeSeries, analysisConfig, perfReqThreshold,
					perfReqConfidence);

			if (detected) {
				result.addMessage("Detected continuous violation of performance requirements in operation: "
						+ operation);
				result.setDetected(true);
			}

			createChart(perfReqThreshold, result, operation, responseTimeSeries);
		}

		return result;
	}

	private void createChart(final double perfReqThreshold, final SpotterResult result, final String operation,
			final NumericPairList<Long, Double> responseTimeSeries) {
		final AnalysisChartBuilder chartBuilder = AnalysisChartBuilder.getChartBuilder();
		final String operationName = operation.contains("(")?operation.substring(0, operation.indexOf("(")):operation;
		
		chartBuilder.startChart(operationName, "experiment time [ms]", "response time [ms]");
		chartBuilder.addTimeSeries(responseTimeSeries, "response times");
		chartBuilder.addHorizontalLine(perfReqThreshold, "requirements threshold");
		getResultManager().storeImageChartResource(chartBuilder, "Response Times", result);
	}

	@Override
	public void executeExperiments() throws InstrumentationException, MeasurementException, WorkloadException {
		executeDefaultExperimentSeries(this, 1, createInstrumentationDescription());
	}

	@Override
	public InstrumentationDescription getInstrumentationDescription() {
		// no additional instrumentation required
		return null;
	}

	private InstrumentationDescription createInstrumentationDescription() {
		final InstrumentationDescriptionBuilder idBuilder = new InstrumentationDescriptionBuilder();
		idBuilder.newAPIScopeEntity(EntryPointScope.class.getName()).addProbe(ResponsetimeProbe.MODEL_PROBE)
				.entityDone();
		return idBuilder.build();
	}

	@Override
	public long getExperimentSeriesDuration() {
		return ProgressManager.getInstance().calculateDefaultExperimentSeriesDuration(1);
	}
}
