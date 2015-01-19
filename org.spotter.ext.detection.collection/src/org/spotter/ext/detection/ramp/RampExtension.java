/**
 * Copyright 2014 SAP AG
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.spotter.ext.detection.ramp;

import java.util.HashSet;
import java.util.Set;

import org.lpe.common.config.ConfigParameterDescription;
import org.lpe.common.util.LpeSupportedTypes;
import org.spotter.core.detection.AbstractDetectionExtension;
import org.spotter.core.detection.IDetectionController;

/**
 * The ramp antipattern detection extension.
 * 
 * @author Alexander Wert
 */
public class RampExtension extends AbstractDetectionExtension {

	private static final String EXTENSION_DESCRIPTION = "The ramp occurs when processing time increases as the system is used.";

	protected static final String DETECTION_STRATEGY_KEY = "strategy";
	protected static final String TIME_WINDOWS_STRATEGY = "time windows strategy";
	protected static final String DIRECT_GROWTH_STRATEGY = "direct growth strategy";
	protected static final String LIN_REGRESSION_STRATEGY = "linear regression strategy";

	public static final String KEY_STIMULATION_PHASE_DURATION_FACTOR = "stimulationPhaseDurationFactor";
	public static final String KEY_REQUIRED_SIGNIFICANT_STEPS = "numRequiredSignificantSteps";
	public static final String KEY_REQUIRED_SIGNIFICANCE_LEVEL = "requiredSignificanceLevel";
	public static final String KEY_CPU_UTILIZATION_THRESHOLD = "maxCpuUtilization";
	public static final String KEY_EXPERIMENT_STEPS = "numExperiments";
	public static final String KEY_LIN_SLOPE = "linear slope threhsold";

	public static final double STIMULATION_PHASE_DURATION_DEFAULT = 1.5; // [Sec]
	public static final int EXPERIMENT_STEPS_DEFAULT = 3;
	public static final double REQUIRED_SIGNIFICANCE_LEVEL_DEFAULT = 0.05; // [0-1]
	public static final double LIN_SLOPE_DEFAULT = 0.01; // [ms / ms]
	public static final int REQUIRED_SIGNIFICANT_STEPS_DEFAULT = 2;

	@Override
	public String getName() {
		return "The Ramp";
	}

	private ConfigParameterDescription createStimulationPhaseDurationParameter() {
		ConfigParameterDescription parameter = new ConfigParameterDescription(KEY_STIMULATION_PHASE_DURATION_FACTOR,
				LpeSupportedTypes.Double);
		parameter.setDefaultValue(String.valueOf(STIMULATION_PHASE_DURATION_DEFAULT));
		parameter.setDescription("ONLY for Time Windows Strategy! The duration of the stimulation phase.");
		return parameter;
	}

	private ConfigParameterDescription createLinearSlopeThresholdParameter() {
		ConfigParameterDescription parameter = new ConfigParameterDescription(KEY_LIN_SLOPE, LpeSupportedTypes.Double);
		parameter.setDefaultValue(String.valueOf(LIN_SLOPE_DEFAULT));
		parameter.setDescription("ONLY for Linear Regression Strategy! Defines the threshold for linear slope. "
				+ "Growth of response times per time unit of experiment. [ms / ms]");
		return parameter;
	}

	private ConfigParameterDescription createNumExperimentsParameter() {
		ConfigParameterDescription parameter = new ConfigParameterDescription(KEY_EXPERIMENT_STEPS,
				LpeSupportedTypes.Integer);
		parameter.setDefaultValue(String.valueOf(EXPERIMENT_STEPS_DEFAULT));
		parameter.setRange(String.valueOf(2), String.valueOf(Integer.MAX_VALUE));
		parameter.setDescription("ONLY for Time Windows Strategy! Number of experiments to execute with "
				+ "different number of users between 1 and max number of users.");
		return parameter;
	}

	private ConfigParameterDescription createRequiredSignificanceLevelParameter() {
		ConfigParameterDescription parameter = new ConfigParameterDescription(KEY_REQUIRED_SIGNIFICANCE_LEVEL,
				LpeSupportedTypes.Double);
		parameter.setDefaultValue(String.valueOf(REQUIRED_SIGNIFICANCE_LEVEL_DEFAULT));
		parameter.setRange(String.valueOf(0.0), String.valueOf(1.0));
		parameter.setDescription("This parameter defines the confidence level to be reached "
				+ "in order to recognize a significant difference when comparing "
				+ "two response time samples with the t-test.");
		return parameter;
	}

	private ConfigParameterDescription createRequiredSignificantStepsParameter() {
		ConfigParameterDescription parameter = new ConfigParameterDescription(KEY_REQUIRED_SIGNIFICANT_STEPS,
				LpeSupportedTypes.Integer);
		parameter.setDefaultValue(String.valueOf(REQUIRED_SIGNIFICANT_STEPS_DEFAULT));
		parameter.setRange(String.valueOf(2), String.valueOf(Integer.MAX_VALUE));
		parameter.setDescription("This parameter specifies the number of steps between experiments "
				+ "required to show a significant increase in order to detect a Ramp.");
		return parameter;
	}

	private ConfigParameterDescription createStrategyParameter() {
		ConfigParameterDescription scopeParameter = new ConfigParameterDescription(DETECTION_STRATEGY_KEY,
				LpeSupportedTypes.String);

		Set<String> scopeOptions = new HashSet<>();
		scopeOptions.add(TIME_WINDOWS_STRATEGY);
		scopeOptions.add(DIRECT_GROWTH_STRATEGY);
		scopeOptions.add(LIN_REGRESSION_STRATEGY);
		scopeParameter.setOptions(scopeOptions);
		scopeParameter.setDefaultValue(TIME_WINDOWS_STRATEGY);
		scopeParameter.setDescription("This parameter determines the strategy, "
				+ "used to analyse the Ramp anti-pattern.");
		return scopeParameter;
	}

	@Override
	public IDetectionController createExtensionArtifact() {
		return new RampDetectionController(this);
	}

	@Override
	protected void initializeConfigurationParameters() {
		addConfigParameter(ConfigParameterDescription.createExtensionDescription(EXTENSION_DESCRIPTION));
		addConfigParameter(createStimulationPhaseDurationParameter());
		addConfigParameter(createNumExperimentsParameter());
		addConfigParameter(createRequiredSignificanceLevelParameter());
		addConfigParameter(createRequiredSignificantStepsParameter());
		addConfigParameter(createLinearSlopeThresholdParameter());
		addConfigParameter(createStrategyParameter());
	}
}
