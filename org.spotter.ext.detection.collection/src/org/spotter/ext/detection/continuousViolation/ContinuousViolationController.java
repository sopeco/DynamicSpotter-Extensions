package org.spotter.ext.detection.continuousViolation;

import org.aim.api.exceptions.InstrumentationException;
import org.aim.api.exceptions.MeasurementException;
import org.aim.api.measurement.dataset.Dataset;
import org.aim.api.measurement.dataset.DatasetCollection;
import org.aim.api.measurement.dataset.ParameterSelection;
import org.aim.artifacts.probes.ResponsetimeProbe;
import org.aim.artifacts.records.ResponseTimeRecord;
import org.aim.artifacts.scopes.EntryPointScope;
import org.aim.description.InstrumentationDescription;
import org.aim.description.builder.InstrumentationDescriptionBuilder;
import org.lpe.common.config.GlobalConfiguration;
import org.lpe.common.extension.IExtension;
import org.lpe.common.util.NumericPairList;
import org.spotter.core.ProgressManager;
import org.spotter.core.detection.AbstractDetectionController;
import org.spotter.core.detection.IDetectionController;
import org.spotter.core.detection.IExperimentReuser;
import org.spotter.exceptions.WorkloadException;
import org.spotter.ext.detection.continuousViolation.strategies.BucketStrategy;
import org.spotter.ext.detection.continuousViolation.strategies.DBSCANStrategy;
import org.spotter.ext.detection.continuousViolation.strategies.MovingPercentileStrategy;
import org.spotter.ext.detection.continuousViolation.util.AnalysisConfig;
import org.spotter.ext.detection.utils.AnalysisChartBuilder;
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
	private AnalysisConfig analysisConfig = new AnalysisConfig();
	private IViolationAnalysisStrategy analysisStrategyImpl;

	/**
	 * Constructor.
	 * 
	 * @param provider
	 *            extension provider
	 */
	public ContinuousViolationController(IExtension<IDetectionController> provider) {
		super(provider);
	}

	@Override
	public void loadProperties() {
		analysisStrategy = getProblemDetectionConfiguration().getProperty(
				ContinuousViolationExtension.VIOLATION_DETECTION_STRATEGY_KEY,
				ContinuousViolationExtension.DBSCAN_STRATEGY);

		String mvaWindowSize = getProblemDetectionConfiguration().getProperty(
				AnalysisConfig.MOVING_AVERAGE_WINDOW_SIZE_KEY,
				String.valueOf(AnalysisConfig.MOVING_AVERAGE_WINDOW_SIZE_DEFAULT));
		analysisConfig.setMvaWindowSize(Integer.parseInt(mvaWindowSize));

		String bucketStepStr = getProblemDetectionConfiguration().getProperty(AnalysisConfig.BUCKET_STEP_KEY,
				String.valueOf(AnalysisConfig.BUCKET_STEP_DEFAULT));
		analysisConfig.setBucketStep(Integer.parseInt(bucketStepStr));

		String minBucketTimeProportionStr = getProblemDetectionConfiguration().getProperty(
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
	protected SpotterResult analyze(DatasetCollection data) {
		double perfReqThreshold = GlobalConfiguration.getInstance().getPropertyAsInteger(
				ConfigKeys.PERFORMANCE_REQUIREMENT_THRESHOLD, ConfigKeys.DEFAULT_PERFORMANCE_REQUIREMENT_THRESHOLD);
		double perfReqConfidence = GlobalConfiguration.getInstance().getPropertyAsDouble(
				ConfigKeys.PERFORMANCE_REQUIREMENT_CONFIDENCE, ConfigKeys.DEFAULT_PERFORMANCE_REQUIREMENT_CONFIDENCE);

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
			boolean detected = analysisStrategyImpl.analyze(responseTimeSeries, analysisConfig, perfReqThreshold,
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

	private void createChart(double perfReqThreshold, SpotterResult result, String operation,
			NumericPairList<Long, Double> responseTimeSeries) {
		AnalysisChartBuilder chartBuilder = new AnalysisChartBuilder();
		chartBuilder.startChart(operation, "Experiment Time [ms]", "Response Time [ms]");
		chartBuilder.addScatterSeries(responseTimeSeries, "Response Times");
		chartBuilder.addHorizontalLine(perfReqThreshold, "Perf. Requirement");
		getResultManager().storeImageChartResource(chartBuilder.build(), "Response Times", result);
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
		InstrumentationDescriptionBuilder idBuilder = new InstrumentationDescriptionBuilder();
		idBuilder.newAPIScopeEntity(EntryPointScope.class.getName()).addProbe(ResponsetimeProbe.MODEL_PROBE)
				.entityDone();
		return idBuilder.build();
	}

	@Override
	public long getExperimentSeriesDuration() {
		return ProgressManager.getInstance().calculateDefaultExperimentSeriesDuration(1);
	}
}
