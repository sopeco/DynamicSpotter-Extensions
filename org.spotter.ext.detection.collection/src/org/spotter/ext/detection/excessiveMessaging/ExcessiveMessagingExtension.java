package org.spotter.ext.detection.excessiveMessaging;

import java.util.HashSet;
import java.util.Set;

import org.lpe.common.config.ConfigParameterDescription;
import org.lpe.common.util.LpeSupportedTypes;
import org.spotter.core.detection.AbstractDetectionExtension;
import org.spotter.core.detection.IDetectionController;

public class ExcessiveMessagingExtension extends AbstractDetectionExtension {
	public static final String REQUIRED_CONFIDENCE_LEVEL_KEY = "confidenceLevel";
	public static final String REQUIRED_SIGNIFICANT_STEPS_KEY = "numSignificantSteps";
	
	protected static final String DETECTION_STRATEGY_KEY = "strategy";
	protected static final String THRESHOLD_STRATEGY = "fix threshold strategy";
	protected static final String STAGNATION_STRATEGY = "network stagnation strategy";
	protected static final String MSG_THORUGHPUT_STAGNATION_STRATEGY = "message throughput stagnation strategy";
	
	public static final double REQUIRED_CONFIDENCE_LEVEL_DEFAULT = 0.95;
	public static final int REQUIRED_SIGNIFICANT_STEPS_DEFAULT = 2;
	
	@SuppressWarnings("unchecked")
	@Override
	public IDetectionController createExtensionArtifact(final String ... args) {
		return new ExcessiveMessagingDetectionController(this);
	}

	@Override
	public String getName() {
		return "Excessive Messaging";
	}

	private ConfigParameterDescription createNumSignificantStepsParameter() {
		final ConfigParameterDescription numSignificantStepsParameter = new ConfigParameterDescription(
				REQUIRED_SIGNIFICANT_STEPS_KEY, LpeSupportedTypes.Integer);
		numSignificantStepsParameter.setDefaultValue(String.valueOf(REQUIRED_SIGNIFICANT_STEPS_DEFAULT));
		numSignificantStepsParameter.setRange(String.valueOf(1), String.valueOf(Integer.MAX_VALUE));
		numSignificantStepsParameter.setDescription("This parameter specifies the number of steps between experiments "
				+ "required to show a significant increase in order to detect a One Lane Bridge.");
		return numSignificantStepsParameter;
	}

	private ConfigParameterDescription createConfidenceLevelParameter() {
		final ConfigParameterDescription requiredConfidenceLevel = new ConfigParameterDescription(
				REQUIRED_CONFIDENCE_LEVEL_KEY, LpeSupportedTypes.Double);
		requiredConfidenceLevel.setDefaultValue(String.valueOf(REQUIRED_CONFIDENCE_LEVEL_DEFAULT));
		requiredConfidenceLevel.setRange("0.0", "1.0");
		requiredConfidenceLevel.setDescription("This parameter defines the confidence level to be reached "
				+ "in order to recognize a significant difference when comparing "
				+ "two response time samples with the t-test.");
		return requiredConfidenceLevel;
	}
	
	private ConfigParameterDescription createStrategyParameter() {
		final ConfigParameterDescription scopeParameter = new ConfigParameterDescription(DETECTION_STRATEGY_KEY,
				LpeSupportedTypes.String);

		final Set<String> scopeOptions = new HashSet<>();
		scopeOptions.add(STAGNATION_STRATEGY);
		scopeOptions.add(THRESHOLD_STRATEGY);
		scopeOptions.add(MSG_THORUGHPUT_STAGNATION_STRATEGY);
		scopeParameter.setOptions(scopeOptions);
		scopeParameter.setDefaultValue(STAGNATION_STRATEGY);
		scopeParameter.setDescription("This parameter determines the strategy, "
				+ "used to analyse the Database congestion anti-pattern.");
		return scopeParameter;
	}



	@Override
	protected void initializeConfigurationParameters() {
		addConfigParameter(createConfidenceLevelParameter());
		addConfigParameter(createNumSignificantStepsParameter());
		addConfigParameter(createStrategyParameter());

	}

}
