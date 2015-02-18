package org.spotter.ext.detection.blob;

import java.util.List;

import org.aim.api.exceptions.InstrumentationException;
import org.aim.api.exceptions.MeasurementException;
import org.aim.api.measurement.dataset.DatasetCollection;
import org.aim.artifacts.probes.JmsCommunicationProbe;
import org.aim.artifacts.scopes.JmsScope;
import org.aim.description.InstrumentationDescription;
import org.aim.description.builder.InstrumentationDescriptionBuilder;
import org.lpe.common.extension.IExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spotter.core.ProgressManager;
import org.spotter.core.detection.AbstractDetectionController;
import org.spotter.core.detection.IDetectionController;
import org.spotter.exceptions.WorkloadException;
import org.spotter.shared.result.model.SpotterResult;

public class BlobDetectionController extends AbstractDetectionController {
	private static final Logger LOGGER = LoggerFactory.getLogger(BlobDetectionController.class);

	private String analysisStrategy;
	private IBlobAnalyzer analysisStrategyImpl;

	public BlobDetectionController(IExtension<IDetectionController> provider) {
		super(provider);
	}

	@Override
	public void loadProperties() {
		analysisStrategy = getProblemDetectionConfiguration().getProperty(BlobExtension.DETECTION_STRATEGY_KEY,
				BlobExtension.COMP_EXCLUSION_STRATEGY);

		switch (analysisStrategy) {
		case BlobExtension.COMP_EXCLUSION_STRATEGY:
			analysisStrategyImpl = new ComponentExclusionAnalyzer();
			break;
		case BlobExtension.MEAN_ANALYSIS_STRATEGY:
			analysisStrategyImpl = new MeanAnalyzer();
			break;
		default:
			analysisStrategyImpl = new ComponentExclusionAnalyzer();
		}

	}

	@Override
	public long getExperimentSeriesDuration() {
		return ProgressManager.getInstance().calculateDefaultExperimentSeriesDuration(1);
	}

	@Override
	public void executeExperiments() throws InstrumentationException, MeasurementException, WorkloadException {
		executeDefaultExperimentSeries(this, 1, getInstrumentationDescription());

	}

	private InstrumentationDescription getInstrumentationDescription() throws InstrumentationException {
		InstrumentationDescriptionBuilder idBuilder = new InstrumentationDescriptionBuilder();
		return idBuilder.newAPIScopeEntity(JmsScope.class.getName()).addProbe(JmsCommunicationProbe.MODEL_PROBE)
				.entityDone().build();
	}

	@Override
	protected SpotterResult analyze(DatasetCollection data) {
		LOGGER.debug("Analyze data for GodClass Antipattern..");

		SpotterResult result = new SpotterResult();
		result.setDetected(false);

		/** Process the raw measurement data */
		LOGGER.debug("process data..");
		ProcessedData processData = DataProcessor.processData(data);

		/** Analyze the processed data */
		LOGGER.debug("analyze data..");

		List<Component> blobs = analysisStrategyImpl.analyze(processData, getResultManager(), result);

		if (!blobs.isEmpty()) {
			result.setDetected(true);
			int i = 1;
			for (Component blob : blobs) {
				result.addMessage("Blob Component:");
				result.addMessage("CP " + i + ": " + blob.getId());
				result.addMessage("");
				result.addMessage("");
				result.addMessage("");
				i++;
			}

		}

		return result;
	}

}
