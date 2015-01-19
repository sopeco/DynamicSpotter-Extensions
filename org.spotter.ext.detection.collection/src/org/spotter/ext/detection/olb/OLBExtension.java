package org.spotter.ext.detection.olb;

import java.util.HashSet;
import java.util.Set;

import org.lpe.common.config.ConfigParameterDescription;
import org.lpe.common.util.LpeSupportedTypes;
import org.spotter.core.detection.AbstractDetectionExtension;
import org.spotter.core.detection.IDetectionController;

/**
 * OLB extension.
 * @author Alexander Wert
 *
 */
public class OLBExtension extends AbstractDetectionExtension {

	private static final String EXTENSION_DESCRIPTION = "The One Lane Bridge anti-pattern is a typical software bottleneck. ";
	public static final String REQUIRED_CONFIDENCE_LEVEL_KEY = "confidenceLevel";
	public static final String REQUIRED_SIGNIFICANT_STEPS_KEY = "numSignificantSteps";
	public static final String EXPERIMENT_STEPS_KEY = "numExperiments";

	public static final double REQUIRED_CONFIDENCE_LEVEL_DEFAULT = 0.95;
	public static final int REQUIRED_SIGNIFICANT_STEPS_DEFAULT = 2;
	public static final int EXPERIMENT_STEPS_DEFAULT = 4;

	protected static final String DETECTION_STRATEGY_KEY = "strategy";
	protected static final String QUEUEING_THEORY_STRATEGY = "queueing theory strategy";
	
	@Override
	public IDetectionController createExtensionArtifact() {
		return new OLBDetectionController(this);
	}

	@Override
	public String getName() {
		return "One Lane Bridge";
	}

	private ConfigParameterDescription createNumExperimentsParameter() {
		ConfigParameterDescription numExperimentsParameter = new ConfigParameterDescription(EXPERIMENT_STEPS_KEY,
				LpeSupportedTypes.Integer);
		numExperimentsParameter.setDefaultValue(String.valueOf(EXPERIMENT_STEPS_DEFAULT));
		numExperimentsParameter.setRange(String.valueOf(2), String.valueOf(Integer.MAX_VALUE));
		numExperimentsParameter.setDescription("Number of experiments to execute with "
				+ "different number of users between 1 and max number of users.");
		return numExperimentsParameter;
	}

	private ConfigParameterDescription createNumSignificantStepsParameter() {
		ConfigParameterDescription numSignificantStepsParameter = new ConfigParameterDescription(
				REQUIRED_SIGNIFICANT_STEPS_KEY, LpeSupportedTypes.Integer);
		numSignificantStepsParameter.setDefaultValue(String.valueOf(REQUIRED_SIGNIFICANT_STEPS_DEFAULT));
		numSignificantStepsParameter.setRange(String.valueOf(1), String.valueOf(Integer.MAX_VALUE));
		numSignificantStepsParameter.setDescription("This parameter specifies the number of steps between experiments "
				+ "required to show a significant increase in order to detect a One Lane Bridge.");
		return numSignificantStepsParameter;
	}

	private ConfigParameterDescription createConfidenceLevelParameter() {
		ConfigParameterDescription requiredConfidenceLevel = new ConfigParameterDescription(
				REQUIRED_CONFIDENCE_LEVEL_KEY, LpeSupportedTypes.Double);
		requiredConfidenceLevel.setDefaultValue(String.valueOf(REQUIRED_CONFIDENCE_LEVEL_DEFAULT));
		requiredConfidenceLevel.setRange("0.0", "1.0");
		requiredConfidenceLevel.setDescription("This parameter defines the confidence level to be reached "
				+ "in order to recognize a significant difference when comparing "
				+ "two response time samples with the t-test.");
		return requiredConfidenceLevel;
	}

	private ConfigParameterDescription createStrategyParameter() {
		ConfigParameterDescription scopeParameter = new ConfigParameterDescription(DETECTION_STRATEGY_KEY,
				LpeSupportedTypes.String);

		Set<String> scopeOptions = new HashSet<>();
		scopeOptions.add(QUEUEING_THEORY_STRATEGY);
		scopeParameter.setOptions(scopeOptions);
		scopeParameter.setDefaultValue(QUEUEING_THEORY_STRATEGY);
		scopeParameter.setDescription("This parameter determines the strategy, "
				+ "used to analyse the OLB anti-pattern.");
		return scopeParameter;
	}

	@Override
	protected void initializeConfigurationParameters() {
		addConfigParameter(ConfigParameterDescription.createExtensionDescription(EXTENSION_DESCRIPTION));
		addConfigParameter(createConfidenceLevelParameter());
		addConfigParameter(createNumSignificantStepsParameter());
		addConfigParameter(createNumExperimentsParameter());
		addConfigParameter(createStrategyParameter());

	}

}
