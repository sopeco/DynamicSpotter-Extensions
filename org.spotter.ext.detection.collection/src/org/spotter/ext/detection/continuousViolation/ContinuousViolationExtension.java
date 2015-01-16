package org.spotter.ext.detection.continuousViolation;

import java.util.HashSet;
import java.util.Set;

import org.lpe.common.config.ConfigParameterDescription;
import org.lpe.common.util.LpeSupportedTypes;
import org.spotter.core.detection.AbstractDetectionExtension;
import org.spotter.core.detection.IDetectionController;
import org.spotter.ext.detection.continuousViolation.util.AnalysisConfig;

/**
 * Extensions for the detection of continuoous performance requirements
 * violation.
 * 
 * @author Alexander Wert
 * 
 */
public class ContinuousViolationExtension extends AbstractDetectionExtension {
	private static final String EXTENSION_DESCRIPTION = "Checks if performance requirements are "
			+ "violated continuously under high load.";
	
	protected static final String VIOLATION_DETECTION_STRATEGY_KEY = "strategy";
	protected static final String MVA_STRATEGY = "mean value analysis";
	protected static final String PERCENTILE_STRATEGY = "percentile analysis";
	protected static final String BUCKET_STRATEGY = "bucket analysis";

	@Override
	public IDetectionController createExtensionArtifact() {
		return new ContinuousViolationController(this);
	}

	@Override
	public String getName() {
		return "Continuouos Violation";
	}
	
	private ConfigParameterDescription createStrategyParameter() {
		ConfigParameterDescription scopeParameter = new ConfigParameterDescription(VIOLATION_DETECTION_STRATEGY_KEY,
				LpeSupportedTypes.String);

		Set<String> scopeOptions = new HashSet<>();
		scopeOptions.add(MVA_STRATEGY);
		scopeOptions.add(BUCKET_STRATEGY);
		scopeOptions.add(PERCENTILE_STRATEGY);
		scopeParameter.setOptions(scopeOptions);
		scopeParameter.setDefaultValue(MVA_STRATEGY);
		scopeParameter.setDescription("This parameter determines the strategy, "
				+ "used to analyse continuous violation of requirements.");
		return scopeParameter;
	}

	

	@Override
	protected void initializeConfigurationParameters() {
		addConfigParameter(ConfigParameterDescription.createExtensionDescription(EXTENSION_DESCRIPTION));
		addConfigParameter(createStrategyParameter());
		for (ConfigParameterDescription cpd : AnalysisConfig.getConfigurationParameters()) {
			addConfigParameter(cpd);
		}

	}

}
