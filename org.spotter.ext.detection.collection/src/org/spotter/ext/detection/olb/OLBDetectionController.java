package org.spotter.ext.detection.olb;

import org.aim.api.exceptions.InstrumentationException;
import org.aim.api.exceptions.MeasurementException;
import org.aim.api.measurement.dataset.DatasetCollection;
import org.aim.artifacts.probes.ResponsetimeProbe;
import org.aim.artifacts.sampler.CPUSampler;
import org.aim.artifacts.scopes.EntryPointScope;
import org.aim.description.InstrumentationDescription;
import org.aim.description.builder.InstrumentationDescriptionBuilder;
import org.lpe.common.extension.IExtension;
import org.spotter.core.ProgressManager;
import org.spotter.core.detection.AbstractDetectionController;
import org.spotter.core.detection.AbstractDetectionExtension;
import org.spotter.core.detection.IDetectionController;
import org.spotter.core.detection.IExperimentReuser;
import org.spotter.exceptions.WorkloadException;
import org.spotter.ext.detection.olb.strategies.QTStrategy;
import org.spotter.shared.result.model.SpotterResult;

/**
 * Detection controller for the One Lane Bridge anti-pattern.
 * 
 * @author Alexander Wert
 * 
 */
public class OLBDetectionController extends AbstractDetectionController implements IExperimentReuser {

	private static final int SAMPLING_DELAY = 100;

	private int experimentSteps;
	private String analysisStrategy;
	private IOLBAnalysisStrategy analysisStrategyImpl;
	private boolean reuser = false;

	/**
	 * Constructor.
	 * 
	 * @param provider
	 *            extension provider
	 */
	public OLBDetectionController(IExtension<IDetectionController> provider) {
		super(provider);
	}

	@Override
	public void loadProperties() {
		analysisStrategy = getProblemDetectionConfiguration().getProperty(OLBExtension.DETECTION_STRATEGY_KEY,
				OLBExtension.QUEUEING_THEORY_STRATEGY);

		switch (analysisStrategy) {
		case OLBExtension.QUEUEING_THEORY_STRATEGY:
			analysisStrategyImpl = new QTStrategy();
			break;

		default:
			analysisStrategyImpl = new QTStrategy();
		}
		analysisStrategyImpl.setMainDetectionController(this);
		analysisStrategyImpl.setProblemDetectionConfiguration(getProblemDetectionConfiguration());
		reuser = Boolean.parseBoolean(this.getProblemDetectionConfiguration().getProperty(
				AbstractDetectionExtension.REUSE_EXPERIMENTS_FROM_PARENT, "false"));
	}

	@Override
	public long getExperimentSeriesDuration() {

		if (reuser) {
			return 0;
		} else {
			return ProgressManager.getInstance().calculateDefaultExperimentSeriesDuration(experimentSteps);
		}

	}

	@Override
	public void executeExperiments() throws InstrumentationException, MeasurementException, WorkloadException {
		if (!reuser) {
			executeDefaultExperimentSeries(this, experimentSteps, getInstrumentationDescription());
		}
	}

	@Override
	protected SpotterResult analyze(DatasetCollection data) {
		return analysisStrategyImpl.analyze(data);
	}

	@Override
	public InstrumentationDescription getInstrumentationDescription() {
		InstrumentationDescriptionBuilder idBuilder = new InstrumentationDescriptionBuilder();
		if (!reuser) {
			idBuilder.newAPIScopeEntity(EntryPointScope.class.getName()).addProbe(ResponsetimeProbe.MODEL_PROBE)
					.entityDone();
		}
		idBuilder.newSampling(CPUSampler.class.getName(), SAMPLING_DELAY);
		return idBuilder.build();
	}

}
