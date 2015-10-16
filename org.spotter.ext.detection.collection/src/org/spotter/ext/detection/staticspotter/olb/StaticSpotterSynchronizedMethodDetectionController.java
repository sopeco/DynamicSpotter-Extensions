package org.spotter.ext.detection.staticspotter.olb;

import java.io.IOException;
import java.util.List;

import org.aim.aiminterface.description.instrumentation.InstrumentationDescription;
import org.aim.aiminterface.exceptions.InstrumentationException;
import org.aim.aiminterface.exceptions.MeasurementException;
import org.aim.api.measurement.dataset.DatasetCollection;
import org.aim.artifacts.probes.ResponsetimeProbe;
import org.aim.artifacts.sampler.CPUSampler;
import org.aim.artifacts.sampler.NetworkIOSampler;
import org.aim.description.builder.InstrumentationDescriptionBuilder;
import org.lpe.common.extension.IExtension;
import org.lpe.common.util.LpeFileUtils;
import org.spotter.core.ProgressManager;
import org.spotter.exceptions.WorkloadException;
import org.spotter.ext.detection.olb.IOLBAnalysisStrategy;
import org.spotter.ext.detection.olb.OLBDetectionController;
import org.spotter.ext.detection.olb.OLBExtension;
import org.spotter.ext.detection.olb.strategies.QTStrategy;
import org.spotter.shared.result.model.SpotterResult;

/**
 * Detection controller for the One Lane Bridge anti-pattern.
 * 
 * @author Alexander Wert
 * 
 */
public class StaticSpotterSynchronizedMethodDetectionController extends OLBDetectionController {

	private static final int SAMPLING_DELAY = 100;

	private int experimentSteps;
	private IOLBAnalysisStrategy analysisStrategyImpl;
	private String filename;

	/**
	 * Constructor.
	 * 
	 * @param provider
	 *            extension provider
	 */
	public StaticSpotterSynchronizedMethodDetectionController(final IExtension provider) {
		super(provider);
	}

	@Override
	public void loadProperties() {
		final String experimentStepsStr = getProblemDetectionConfiguration().getProperty(OLBExtension.EXPERIMENT_STEPS_KEY);
		experimentSteps = experimentStepsStr != null ? Integer.parseInt(experimentStepsStr)
				: OLBExtension.EXPERIMENT_STEPS_DEFAULT;

		analysisStrategyImpl = new QTStrategy();
		analysisStrategyImpl.setMainDetectionController(this);
		analysisStrategyImpl.setProblemDetectionConfiguration(getProblemDetectionConfiguration());
		filename = getProblemDetectionConfiguration().getProperty(StaticSpotterSynchronizedMethodExtension.STATIC_SPOTTER_EXPORT_FILE);
	}

	@Override
	public long getExperimentSeriesDuration() {
		return ProgressManager.getInstance().calculateDefaultExperimentSeriesDuration(experimentSteps);
	}

    @Override
    public void executeExperiments() throws InstrumentationException, MeasurementException, WorkloadException {
    	executeDefaultExperimentSeries(this, experimentSteps, getInstrumentationDescription());
    }
    
	@Override
	protected SpotterResult analyze(final DatasetCollection data) {
		return analysisStrategyImpl.analyze(data);
	}

	@Override
	public InstrumentationDescription getInstrumentationDescription() {
		List<String> methodNames;
		try {
			methodNames = LpeFileUtils.readLines(filename);
		} catch (final IOException e) {
			throw new RuntimeException("Failed to read static spotter export file");
		}
		final InstrumentationDescriptionBuilder idBuilder = new InstrumentationDescriptionBuilder();
		for (final String methodName : methodNames) {
			idBuilder.newMethodScopeEntity(methodName).addProbe(ResponsetimeProbe.MODEL_PROBE).entityDone();
		}
		idBuilder.newSampling(CPUSampler.class.getName(), SAMPLING_DELAY);
		idBuilder.newSampling(NetworkIOSampler.class.getName(), SAMPLING_DELAY);
		return idBuilder.build();
	}
}
