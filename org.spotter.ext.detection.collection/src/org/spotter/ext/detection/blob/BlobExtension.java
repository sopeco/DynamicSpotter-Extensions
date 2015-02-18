package org.spotter.ext.detection.blob;

import java.util.HashSet;
import java.util.Set;

import org.lpe.common.config.ConfigParameterDescription;
import org.lpe.common.util.LpeSupportedTypes;
import org.spotter.core.detection.AbstractDetectionExtension;
import org.spotter.core.detection.IDetectionController;
import org.spotter.ext.detection.ramp.RampDetectionController;

public class BlobExtension extends AbstractDetectionExtension {
	protected static final String DETECTION_STRATEGY_KEY = "strategy";
	protected static final String COMP_EXCLUSION_STRATEGY = "exclusion analysis strategy";
	protected static final String MEAN_ANALYSIS_STRATEGY = "mean analysis strategy";
	@Override
	public IDetectionController createExtensionArtifact() {
		return new BlobDetectionController(this);
	}

	@Override
	public String getName() {
		return "Blob";
	}

	private ConfigParameterDescription createStrategyParameter() {
		ConfigParameterDescription scopeParameter = new ConfigParameterDescription(DETECTION_STRATEGY_KEY,
				LpeSupportedTypes.String);

		Set<String> scopeOptions = new HashSet<>();
		scopeOptions.add(COMP_EXCLUSION_STRATEGY);
		scopeOptions.add(MEAN_ANALYSIS_STRATEGY);
		scopeParameter.setOptions(scopeOptions);
		scopeParameter.setDefaultValue(COMP_EXCLUSION_STRATEGY);
		scopeParameter.setDescription("This parameter determines the strategy, "
				+ "used to analyse the Blob anti-pattern.");
		return scopeParameter;
	}


	@Override
	protected void initializeConfigurationParameters() {
		addConfigParameter(createStrategyParameter());
	}

}
