package org.spotter.ext.detection.appHiccups;

import java.util.List;

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
import org.spotter.core.chartbuilder.AnalysisChartBuilder;
import org.spotter.core.detection.AbstractDetectionController;
import org.spotter.core.detection.IDetectionController;
import org.spotter.core.detection.IExperimentReuser;
import org.spotter.exceptions.WorkloadException;
import org.spotter.ext.detection.appHiccups.strategies.BucketStrategy;
import org.spotter.ext.detection.appHiccups.strategies.DBSCANStrategy;
import org.spotter.ext.detection.appHiccups.strategies.MovingPercentileStrategy;
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

		String maxHiccupTimeProportionStr = getProblemDetectionConfiguration().getProperty(
				AppHiccupsExtension.MAX_HICCUPS_TIME_PROPORTION_KEY,
				String.valueOf(AppHiccupsExtension.MAX_HICCUPS_TIME_PROPORTION_DEFAULT));
		maxHiccupTimeProportion = Double.parseDouble(maxHiccupTimeProportionStr);

		switch (analysisStrategy) {
		case AppHiccupsExtension.MVA_STRATEGY:
			analysisStrategyImpl = new MovingPercentileStrategy();
			break;
		case AppHiccupsExtension.DBSCAN_STRATEGY:
			analysisStrategyImpl = new DBSCANStrategy();
			break;
		case AppHiccupsExtension.BUCKET_STRATEGY:
			analysisStrategyImpl = new BucketStrategy();
			break;
		default:
			analysisStrategyImpl = new MovingPercentileStrategy();
		}
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
			if (responseTimeSeries.size() <= 5) {
				continue;
			}
			// sort chronologically
			responseTimeSeries.sort();
			List<Hiccup> hiccups = analysisStrategyImpl.findHiccups(responseTimeSeries, hiccupDetectionConfig,
					perfReqThreshold, perfReqConfidence, getResultManager(), result);

			long experimentDuration = responseTimeSeries.getKeyMax() - responseTimeSeries.getKeyMin();
			long hiccupsDuration = 0;
			for (Hiccup hiccup : hiccups) {
				hiccupsDuration += hiccup.getEndTimestamp() - hiccup.getStartTimestamp();
			}

			if (hiccups.size() > 1 && hiccupsDuration < maxHiccupTimeProportion * experimentDuration) {
				result.addMessage("Detected hiccup behaviour in operation: " + operation);
				result.setDetected(true);
				createChart(result, operation, responseTimeSeries, hiccups, perfReqThreshold);

			}

		}

		return result;
	}

	private void createChart(SpotterResult result, String operation, NumericPairList<Long, Double> responseTimeSeries,
			List<Hiccup> hiccups, long perfReqThreshold) {
		AnalysisChartBuilder chartBuilder = AnalysisChartBuilder.getChartBuilder();
		String operationName = operation.contains("(")?operation.substring(0, operation.indexOf("(")):operation;
		
		chartBuilder.startChart(operationName, "Experiment Time [ms]", "Response Time [ms]");
		chartBuilder.addTimeSeries(responseTimeSeries, "Response Times");
		chartBuilder.addHorizontalLine(perfReqThreshold, "Perf. Requirement");
		long minTimestamp = responseTimeSeries.getKeyMin();
		long maxTimestamp = responseTimeSeries.getKeyMax();
		double minRT = responseTimeSeries.getValueMin().doubleValue();

		NumericPairList<Long, Double> hiccupSeries = new NumericPairList<>();
		hiccupSeries.add(minTimestamp, minRT);
		for (Hiccup hiccup : hiccups) {
			hiccupSeries.add(hiccup.getStartTimestamp(), minRT);
			hiccupSeries.add(hiccup.getStartTimestamp(), hiccup.getMaxHiccupResponseTime());
			hiccupSeries.add(hiccup.getEndTimestamp(), hiccup.getMaxHiccupResponseTime());
			hiccupSeries.add(hiccup.getEndTimestamp(), minRT);
		}
		hiccupSeries.add(maxTimestamp, minRT);

		chartBuilder.addTimeSeriesWithLine(hiccupSeries, "Hiccups");
		getResultManager().storeImageChartResource(chartBuilder, "Hiccups", result);
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
