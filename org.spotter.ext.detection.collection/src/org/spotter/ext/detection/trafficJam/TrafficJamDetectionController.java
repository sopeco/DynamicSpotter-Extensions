package org.spotter.ext.detection.trafficJam;

import org.aim.aiminterface.description.instrumentation.InstrumentationDescription;
import org.aim.aiminterface.exceptions.InstrumentationException;
import org.aim.aiminterface.exceptions.MeasurementException;
import org.aim.api.measurement.dataset.Dataset;
import org.aim.api.measurement.dataset.DatasetCollection;
import org.aim.artifacts.probes.ResponsetimeProbe;
import org.aim.artifacts.records.ResponseTimeRecord;
import org.aim.artifacts.scopes.EntryPointScope;
import org.aim.description.builder.InstrumentationDescriptionBuilder;
import org.lpe.common.extension.IExtension;
import org.spotter.core.ProgressManager;
import org.spotter.core.detection.AbstractDetectionController;
import org.spotter.core.detection.IDetectionController;
import org.spotter.exceptions.WorkloadException;
import org.spotter.ext.detection.trafficJam.strategies.LinearRegression;
import org.spotter.ext.detection.trafficJam.strategies.TTestStrategy;
import org.spotter.shared.result.model.SpotterResult;

public class TrafficJamDetectionController extends AbstractDetectionController {

	private String analysisStrategy;
	private ITrafficJamStrategy analysisStrategyImpl;
	private int experimentSteps;

	public TrafficJamDetectionController(IExtension<IDetectionController> provider) {
		super(provider);
	}

	@Override
	public void loadProperties() {
		String experimentStepsStr = getProblemDetectionConfiguration().getProperty(
				TrafficJamExtension.EXPERIMENT_STEPS_KEY);
		experimentSteps = experimentStepsStr != null ? Integer.parseInt(experimentStepsStr)
				: TrafficJamExtension.EXPERIMENT_STEPS_DEFAULT;

		analysisStrategy = getProblemDetectionConfiguration().getProperty(TrafficJamExtension.DETECTION_STRATEGY_KEY,
				TrafficJamExtension.T_TEST_STRATEGY);

		switch (analysisStrategy) {
		case TrafficJamExtension.T_TEST_STRATEGY:
			analysisStrategyImpl = new TTestStrategy();
			break;
		case TrafficJamExtension.LIN_REGRESSION_STRATEGY:
			analysisStrategyImpl = new LinearRegression();
			break;
		default:
			analysisStrategyImpl = new TTestStrategy();
		}
		analysisStrategyImpl.setMainDetectionController(this);
		analysisStrategyImpl.setProblemDetectionConfiguration(getProblemDetectionConfiguration());

	}

	@Override
	public long getExperimentSeriesDuration() {
		return ProgressManager.getInstance().calculateDefaultExperimentSeriesDuration(experimentSteps);
	}

	@Override
	public void executeExperiments() throws InstrumentationException, MeasurementException, WorkloadException {
		executeDefaultExperimentSeries(this, experimentSteps, getInstrumentationDescription());

	}

	private InstrumentationDescription getInstrumentationDescription() {
		InstrumentationDescriptionBuilder idBuilder = new InstrumentationDescriptionBuilder();
		idBuilder.newAPIScopeEntity(EntryPointScope.class.getName()).addProbe(ResponsetimeProbe.MODEL_PROBE)
				.entityDone();
		return idBuilder.build();

	}

	@Override
	protected SpotterResult analyze(DatasetCollection data) {
		SpotterResult result = new SpotterResult();

		Dataset rtDataset = data.getDataSet(ResponseTimeRecord.class);

		if (rtDataset == null || rtDataset.size() == 0) {
			result.setDetected(false);
			result.addMessage("Instrumentation achieved no results for the given scope!");
			return result;
		}

		for (String operation : rtDataset.getValueSet(ResponseTimeRecord.PAR_OPERATION, String.class)) {

			boolean operationDetected = false;
			try {
				operationDetected = analysisStrategyImpl.analyseOperationResponseTimes(rtDataset, operation, result);
			} catch (NullPointerException npe) {
				result.addMessage("Traffic Jam detection failed for the operation '" + operation
						+ "', because the operation was not executed in each analysis cycle.");
				continue;
			} catch (IllegalArgumentException iae) {
				result.addMessage(iae.getMessage());
				continue;
			}

			if (operationDetected) {
				result.setDetected(true);
				result.addMessage("Traffic Jam detected in service: " + operation);

			}

		}

		return result;
	}

}
