package org.spotter.ext.detection.ramp.strategies;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import org.aim.api.exceptions.InstrumentationException;
import org.aim.api.exceptions.MeasurementException;
import org.aim.api.measurement.dataset.Dataset;
import org.aim.api.measurement.dataset.DatasetCollection;
import org.aim.api.measurement.dataset.Parameter;
import org.aim.api.measurement.dataset.ParameterSelection;
import org.aim.artifacts.probes.ResponsetimeProbe;
import org.aim.artifacts.records.ResponseTimeRecord;
import org.aim.artifacts.scopes.EntryPointScope;
import org.aim.description.InstrumentationDescription;
import org.aim.description.builder.InstrumentationDescriptionBuilder;
import org.lpe.common.config.GlobalConfiguration;
import org.lpe.common.util.LpeNumericUtils;
import org.lpe.common.util.NumericPairList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spotter.core.ProgressManager;
import org.spotter.core.detection.AbstractDetectionController;
import org.spotter.core.workload.LoadConfig;
import org.spotter.exceptions.WorkloadException;
import org.spotter.ext.detection.ramp.IRampDetectionStrategy;
import org.spotter.ext.detection.ramp.RampDetectionController;
import org.spotter.ext.detection.ramp.RampExtension;
import org.spotter.ext.detection.utils.AnalysisChartBuilder;
import org.spotter.shared.configuration.ConfigKeys;
import org.spotter.shared.result.model.SpotterResult;
import org.spotter.shared.status.DiagnosisStatus;

/**
 * Utilizie stimulation phases to trigger the Ramp.
 * 
 * @author Alexander Wert
 * 
 */
public class TimeWindowsStrategy implements IRampDetectionStrategy {
	private static final Logger LOGGER = LoggerFactory.getLogger(TimeWindowsStrategy.class);
	private static final String STEP = "step";

	private static int stimulationPhaseDuration;
	private static int experimentSteps;
	private static int reuiqredSignificanceSteps;
	private static double requiredSignificanceLevel;
	private RampDetectionController mainDetectionController;

	@Override
	public void setProblemDetectionConfiguration(Properties problemDetectionConfiguration) {
		String warmupPhaseStr = problemDetectionConfiguration
				.getProperty(RampExtension.KEY_STIMULATION_PHASE_DURATION_FACTOR);
		stimulationPhaseDuration = (int) ((warmupPhaseStr != null ? Double.parseDouble(warmupPhaseStr)
				: RampExtension.STIMULATION_PHASE_DURATION_DEFAULT) * GlobalConfiguration.getInstance()
				.getPropertyAsDouble(ConfigKeys.EXPERIMENT_DURATION));

		String experimentStepsStr = problemDetectionConfiguration.getProperty(RampExtension.KEY_EXPERIMENT_STEPS);
		experimentSteps = experimentStepsStr != null ? Integer.parseInt(experimentStepsStr)
				: RampExtension.EXPERIMENT_STEPS_DEFAULT;

		String significanceStepsStr = problemDetectionConfiguration
				.getProperty(RampExtension.KEY_REQUIRED_SIGNIFICANT_STEPS);
		reuiqredSignificanceSteps = significanceStepsStr != null ? Integer.parseInt(significanceStepsStr)
				: RampExtension.REQUIRED_SIGNIFICANT_STEPS_DEFAULT;

		String significanceLevelStr = problemDetectionConfiguration
				.getProperty(RampExtension.KEY_REQUIRED_SIGNIFICANCE_LEVEL);
		requiredSignificanceLevel = significanceLevelStr != null ? Double.parseDouble(significanceLevelStr)
				: RampExtension.REQUIRED_SIGNIFICANCE_LEVEL_DEFAULT;
	}

	@Override
	public void executeExperiments() throws InstrumentationException, MeasurementException {
		try {

			mainDetectionController.instrument(getInstrumentationDescription());

			for (int i = 1; i <= experimentSteps; i++) {

				if (i > 1) {
					LOGGER.info("RampDetectionController step count ----{}----.", i);

					LOGGER.info("RampDetectionController started to stimulate the SUT with {} users.",
							GlobalConfiguration.getInstance().getPropertyAsInteger(ConfigKeys.WORKLOAD_MAXUSERS));
					stimulateSystem(stimulationPhaseDuration);
					LOGGER.info("RampDetectionController finalized to stimulate the SUT.");
				}

				LOGGER.info("RampDetectionController started to run a single user experiment.");
				runExperiment(1, i);
				LOGGER.info("RampDetectionController finalized to run a single user experiment.");

			}

			mainDetectionController.uninstrument();

		} catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

	private void runExperiment(int numUsers, int stepNumber) throws WorkloadException, MeasurementException {

		LOGGER.info("Ramp Detection (TimeWindowStrategy) started experiment with {} users ...", numUsers);

		LoadConfig lConfig = new LoadConfig();
		lConfig.setNumUsers(numUsers);
		lConfig.setRampUpIntervalLength(GlobalConfiguration.getInstance().getPropertyAsInteger(
				ConfigKeys.EXPERIMENT_RAMP_UP_INTERVAL_LENGTH));
		lConfig.setRampUpUsersPerInterval(GlobalConfiguration.getInstance().getPropertyAsInteger(
				ConfigKeys.EXPERIMENT_RAMP_UP_NUM_USERS_PER_INTERVAL));
		lConfig.setCoolDownIntervalLength(GlobalConfiguration.getInstance().getPropertyAsInteger(
				ConfigKeys.EXPERIMENT_COOL_DOWN_INTERVAL_LENGTH));
		lConfig.setCoolDownUsersPerInterval(GlobalConfiguration.getInstance().getPropertyAsInteger(
				ConfigKeys.EXPERIMENT_COOL_DOWN_NUM_USERS_PER_INTERVAL));
		lConfig.setExperimentDuration(GlobalConfiguration.getInstance().getPropertyAsInteger(
				ConfigKeys.EXPERIMENT_DURATION));

		ProgressManager.getInstance().updateProgressStatus(mainDetectionController.getProblemId(),
				DiagnosisStatus.EXPERIMENTING_RAMP_UP);
		mainDetectionController.workloadAdapter().startLoad(lConfig);

		mainDetectionController.workloadAdapter().waitForWarmupPhaseTermination();

		ProgressManager.getInstance().updateProgressStatus(mainDetectionController.getProblemId(),
				DiagnosisStatus.EXPERIMENTING_STABLE_PHASE);
		mainDetectionController.measurementAdapter().enableMonitoring();

		mainDetectionController.workloadAdapter().waitForExperimentPhaseTermination();

		ProgressManager.getInstance().updateProgressStatus(mainDetectionController.getProblemId(),
				DiagnosisStatus.EXPERIMENTING_COOL_DOWN);
		mainDetectionController.measurementAdapter().disableMonitoring();

		mainDetectionController.workloadAdapter().waitForFinishedLoad();

		ProgressManager.getInstance().updateProgressStatus(mainDetectionController.getProblemId(),
				DiagnosisStatus.COLLECTING_DATA);
		LOGGER.info("Storing data ...");
		long dataCollectionStart = System.currentTimeMillis();
		Parameter numOfUsersParameter = new Parameter(STEP, stepNumber);
		Set<Parameter> parameters = new TreeSet<>();
		parameters.add(numOfUsersParameter);
		mainDetectionController.getResultManager().storeResults(parameters,
				mainDetectionController.measurementAdapter());
		ProgressManager.getInstance().addAdditionalDuration(
				(System.currentTimeMillis() - dataCollectionStart) / AbstractDetectionController.SECOND);
		LOGGER.info("Data stored!");
	}

	private void stimulateSystem(int duration) throws WorkloadException {
		LoadConfig lConfig = new LoadConfig();
		lConfig.setNumUsers(GlobalConfiguration.getInstance().getPropertyAsInteger(ConfigKeys.WORKLOAD_MAXUSERS));
		lConfig.setRampUpIntervalLength(GlobalConfiguration.getInstance().getPropertyAsInteger(
				ConfigKeys.EXPERIMENT_RAMP_UP_INTERVAL_LENGTH));
		lConfig.setRampUpUsersPerInterval(GlobalConfiguration.getInstance().getPropertyAsInteger(
				ConfigKeys.EXPERIMENT_RAMP_UP_NUM_USERS_PER_INTERVAL));
		lConfig.setCoolDownIntervalLength(GlobalConfiguration.getInstance().getPropertyAsInteger(
				ConfigKeys.EXPERIMENT_COOL_DOWN_INTERVAL_LENGTH));
		lConfig.setCoolDownUsersPerInterval(GlobalConfiguration.getInstance().getPropertyAsInteger(
				ConfigKeys.EXPERIMENT_COOL_DOWN_NUM_USERS_PER_INTERVAL));
		lConfig.setExperimentDuration(duration);
		mainDetectionController.workloadAdapter().startLoad(lConfig);

		mainDetectionController.workloadAdapter().waitForFinishedLoad();
	}

	private InstrumentationDescription getInstrumentationDescription() {
		InstrumentationDescriptionBuilder idBuilder = new InstrumentationDescriptionBuilder();
		idBuilder.newAPIScopeEntity(EntryPointScope.class.getName()).addProbe(ResponsetimeProbe.MODEL_PROBE)
				.entityDone();
		return idBuilder.build();

	}

	@Override
	public long getExperimentSeriesDuration() {
		long experimentDuration = ProgressManager.getInstance().calculateExperimentDuration(1,
				GlobalConfiguration.getInstance().getPropertyAsInteger(ConfigKeys.EXPERIMENT_DURATION));
		long stimulationDuration = ProgressManager.getInstance().calculateExperimentDuration(
				GlobalConfiguration.getInstance().getPropertyAsInteger(ConfigKeys.WORKLOAD_MAXUSERS),
				stimulationPhaseDuration);

		return ((long) experimentSteps) * (stimulationDuration + experimentDuration);
	}

	@Override
	public void setMainDetectionController(RampDetectionController mainDetectionController) {
		this.mainDetectionController = mainDetectionController;

	}

	@Override
	public SpotterResult analyze(DatasetCollection data) {
		SpotterResult result = new SpotterResult();

		Dataset rtDataset = data.getDataSets(ResponseTimeRecord.class).get(0);
		if (rtDataset == null || rtDataset.size() == 0) {
			result.setDetected(false);
			result.addMessage("Instrumentation achieved no results for the given scope!");
			return result;
		}
		for (String operation : rtDataset.getValueSet(ResponseTimeRecord.PAR_OPERATION, String.class)) {

			boolean operationDetected = analyseOperationResponseTimes(rtDataset, operation, result);
			if (operationDetected) {
				result.setDetected(true);
				result.addMessage("Ramp detected in operation: " + operation);
			}

		}

		return result;
	}

	private boolean analyseOperationResponseTimes(Dataset rtDataset, String operation, SpotterResult result) {
		int prevStep = -1;
		int firstSignificantStep = -1;
		int significantSteps = 0;
		NumericPairList<Integer, Double> chartData = new NumericPairList<>();
		NumericPairList<Integer, Double> chartDataMeans = new NumericPairList<>();
		List<Number> confidenceIntervals = new ArrayList<>();
		for (Integer step : rtDataset.getValueSet(STEP, Integer.class)) {
			if (prevStep > 0) {
				ParameterSelection selectionCurrent = new ParameterSelection().select(STEP, step).select(
						ResponseTimeRecord.PAR_OPERATION, operation);
				ParameterSelection selectionPrev = new ParameterSelection().select(STEP, prevStep).select(
						ResponseTimeRecord.PAR_OPERATION, operation);

				Dataset datasetCurrent = selectionCurrent.applyTo(rtDataset);
				Dataset datasetPrev = selectionPrev.applyTo(rtDataset);

				double prevMean = LpeNumericUtils.average(datasetPrev.getValues(ResponseTimeRecord.PAR_RESPONSE_TIME,
						Long.class));
				double currentMean = LpeNumericUtils.average(datasetCurrent.getValues(
						ResponseTimeRecord.PAR_RESPONSE_TIME, Long.class));
				// maybe the operation could not be found in one of the current
				// selections
				if (datasetCurrent == null || datasetPrev == null) {
					prevStep = step;
					continue;
				}
				List<Double> sums1 = new ArrayList<>();
				List<Double> sums2 = new ArrayList<>();
				LpeNumericUtils.createNormalDistributionByBootstrapping(
						datasetPrev.getValues(ResponseTimeRecord.PAR_RESPONSE_TIME, Long.class),
						datasetCurrent.getValues(ResponseTimeRecord.PAR_RESPONSE_TIME, Long.class), sums1, sums2);
				double pValue = LpeNumericUtils.tTest(sums2, sums1);

				if (pValue <= requiredSignificanceLevel && currentMean > prevMean) {
					if (firstSignificantStep < 0) {
						firstSignificantStep = prevStep;
					}
					significantSteps++;
				} else {
					firstSignificantStep = -1;
					significantSteps = 0;
				}

				// create data for chart
				if (prevStep == 1) {
					for (Double value : sums1) {
						chartData.add(prevStep, value);

					}
					chartDataMeans.add(prevStep, LpeNumericUtils.average(sums1));
					double stdDev = LpeNumericUtils.stdDev(sums1);
					double width = LpeNumericUtils.getConfidenceIntervalWidth(sums1.size(), stdDev,
							requiredSignificanceLevel);
					confidenceIntervals.add(width / 2.0);
				}
				for (Double value : sums2) {
					chartData.add(step, value);
				}
				chartDataMeans.add(step, LpeNumericUtils.average(sums2));
				double stdDev = LpeNumericUtils.stdDev(sums2);
				double width = LpeNumericUtils.getConfidenceIntervalWidth(sums2.size(), stdDev,
						requiredSignificanceLevel);
				confidenceIntervals.add(width / 2.0);
			}
			prevStep = step;
		}

		createChart(operation, result, chartData, chartDataMeans, confidenceIntervals);
		if (firstSignificantStep > 0 && significantSteps >= reuiqredSignificanceSteps) {
			return true;
		}
		return false;
	}

	private void createChart(String operation, SpotterResult result, NumericPairList<Integer, Double> chartData,
			NumericPairList<Integer, Double> chartDataMeans, List<Number> confidenceIntervals) {
		AnalysisChartBuilder chartBuilder = new AnalysisChartBuilder();
		chartBuilder.startChart(operation, "Experiment", "Response Time [ms]");
		chartBuilder.addScatterSeries(chartData, "Response Times");
		chartBuilder.addScatterSeriesWithErrorBars(chartDataMeans, confidenceIntervals, "Confidence Intervals");
		chartBuilder.build();
		mainDetectionController.getResultManager().storeImageChartResource(chartBuilder.build(), "Ramp Detection (TW)",
				result);
	}

}
