package org.spotter.ext.detection.appHiccups;

import java.util.List;

import org.aim.api.exceptions.InstrumentationException;
import org.aim.api.exceptions.MeasurementException;
import org.aim.api.measurement.dataset.Dataset;
import org.aim.api.measurement.dataset.DatasetCollection;
import org.aim.api.measurement.dataset.ParameterSelection;
import org.aim.artifacts.records.ResponseTimeRecord;
import org.aim.description.InstrumentationDescription;
import org.lpe.common.config.GlobalConfiguration;
import org.lpe.common.extension.IExtension;
import org.lpe.common.util.NumericPairList;
import org.spotter.core.detection.AbstractDetectionController;
import org.spotter.core.detection.IDetectionController;
import org.spotter.core.detection.IExperimentReuser;
import org.spotter.exceptions.WorkloadException;
import org.spotter.ext.detection.appHiccups.strategies.BucketStrategy;
import org.spotter.ext.detection.appHiccups.strategies.MVAStrategy;
import org.spotter.ext.detection.appHiccups.strategies.NaiveStrategy;
import org.spotter.ext.detection.appHiccups.utils.Hiccup;
import org.spotter.ext.detection.appHiccups.utils.HiccupDetectionConfig;
import org.spotter.ext.detection.utils.Utils;
import org.spotter.shared.configuration.ConfigKeys;
import org.spotter.shared.result.model.SpotterResult;

/**
 * Detection Controller for the Application Hiccups problem.
 * 
 * @author Alexander Wert
 * 
 */
public class AppHiccupsController extends AbstractDetectionController implements IExperimentReuser {

	private String analysisStrategy;
	private double maxHiccupTimeProportion = AppHiccupsExtension.MAX_HICCUPS_TIME_PROPORTION_DEFAULT;
	private HiccupDetectionConfig hiccupDetectionConfig = new HiccupDetectionConfig();
	private IHiccupAnalysisStrategy analysisStrategyImpl;

	/**
	 * Constructor.
	 * 
	 * @param provider
	 *            extension provider
	 */
	public AppHiccupsController(IExtension<IDetectionController> provider) {
		super(provider);
	}

	@Override
	public void loadProperties() {
		analysisStrategy = getProblemDetectionConfiguration().getProperty(AppHiccupsExtension.APP_HICCUPS_STRATEGY_KEY,
				AppHiccupsExtension.MVA_STRATEGY);
		String mvaWindowSize = getProblemDetectionConfiguration().getProperty(
				HiccupDetectionConfig.MOVING_AVERAGE_WINDOW_SIZE_KEY,
				String.valueOf(HiccupDetectionConfig.MOVING_AVERAGE_WINDOW_SIZE_DEFAULT));
		hiccupDetectionConfig.setMvaWindowSize(Integer.parseInt(mvaWindowSize));

		String bucketStepStr = getProblemDetectionConfiguration().getProperty(
				HiccupDetectionConfig.BUCKET_STEP_KEY,
				String.valueOf(HiccupDetectionConfig.BUCKET_STEP_DEFAULT));
		hiccupDetectionConfig.setBucketStep(Integer.parseInt(bucketStepStr));
		
		String maxHiccupTimeProportionStr = getProblemDetectionConfiguration().getProperty(
				AppHiccupsExtension.MAX_HICCUPS_TIME_PROPORTION_KEY,
				String.valueOf(AppHiccupsExtension.MAX_HICCUPS_TIME_PROPORTION_DEFAULT));
		maxHiccupTimeProportion = Double.parseDouble(maxHiccupTimeProportionStr);

		switch (analysisStrategy) {
		case AppHiccupsExtension.MVA_STRATEGY:
			analysisStrategyImpl = new MVAStrategy();
			break;
		case AppHiccupsExtension.NAIVE_STRATEGY:
			analysisStrategyImpl = new NaiveStrategy();
			break;
		case AppHiccupsExtension.BUCKET_STRATEGY:
			analysisStrategyImpl = new BucketStrategy();
			break;
		default:
			analysisStrategyImpl = new MVAStrategy();
		}
	}

	@Override
	public long getExperimentSeriesDuration() {
		// no experiments executed
		return 0;
	}

	@Override
	protected void executeExperiments() throws InstrumentationException, MeasurementException, WorkloadException {
		// not required
	}

	@Override
	protected SpotterResult analyze(DatasetCollection data) {
		long perfReqThreshold = GlobalConfiguration.getInstance().getPropertyAsInteger(
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
			List<Hiccup> hiccups = analysisStrategyImpl.findHiccups(responseTimeSeries, hiccupDetectionConfig,
					perfReqThreshold, perfReqConfidence);

			long experimentDuration = responseTimeSeries.getKeyMax() - responseTimeSeries.getKeyMin();
			long hiccupsDuration = 0;
			for (Hiccup hiccup : hiccups) {
				hiccupsDuration += hiccup.getEndTimestamp() - hiccup.getStartTimestamp();
			}

			if (!hiccups.isEmpty() && hiccupsDuration < maxHiccupTimeProportion * experimentDuration) {
				result.addMessage("Detected hiccup behaviour in operation: " + operation);
				result.setDetected(true);
			}
		}

		return result;
	}

	@Override
	public InstrumentationDescription getInstrumentationDescription() {
		// no additional instrumentation required
		return null;
	}

}
