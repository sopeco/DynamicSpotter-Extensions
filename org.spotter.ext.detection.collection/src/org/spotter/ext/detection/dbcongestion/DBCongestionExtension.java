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
package org.spotter.ext.detection.dbcongestion;

import java.util.HashSet;
import java.util.Set;

import org.lpe.common.config.ConfigParameterDescription;
import org.lpe.common.extension.IExtensionArtifact;
import org.lpe.common.util.LpeSupportedTypes;
import org.spotter.core.detection.AbstractDetectionExtension;

public class DBCongestionExtension extends AbstractDetectionExtension {

	public DBCongestionExtension() {
		super(DBCongestionDetectionController.class);
	}

	// TODO: please provide a description
	private static final String EXTENSION_DESCRIPTION = "no description";

	
	protected static final String DETECTION_STRATEGY_KEY = "strategy";
	protected static final String THRESHOLD_STRATEGY = "fix threshold strategy";
	protected static final String QT_STRATEGY = "queueing theory strategy";
	public static final String REQUIRED_CONFIDENCE_LEVEL_KEY = "confidenceLevel";
	public static final String REQUIRED_SIGNIFICANT_STEPS_KEY = "numSignificantSteps";
	public static final String CPU_THRESHOLD_KEY = "cpuThreshold";
	public static final String EXPERIMENT_STEPS_KEY = "numExperiments";

	public static final double REQUIRED_CONFIDENCE_LEVEL_DEFAULT = 0.95;
	public static final double CPU_THRESHOLD_DEFAULT = 0.90;
	public static final int REQUIRED_SIGNIFICANT_STEPS_DEFAULT = 2;
	public static final int EXPERIMENT_STEPS_DEFAULT = 4;

	private ConfigParameterDescription createNumExperimentsParameter() {
		final ConfigParameterDescription numExperimentsParameter = new ConfigParameterDescription(EXPERIMENT_STEPS_KEY,
				LpeSupportedTypes.Integer);
		numExperimentsParameter.setDefaultValue(String.valueOf(EXPERIMENT_STEPS_DEFAULT));
		numExperimentsParameter.setRange(String.valueOf(2), String.valueOf(Integer.MAX_VALUE));
		numExperimentsParameter.setDescription("Number of experiments to execute with "
				+ "different number of users between 1 and max number of users.");
		return numExperimentsParameter;
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

	private ConfigParameterDescription createCPUThresholdParameter() {
		final ConfigParameterDescription requiredConfidenceLevel = new ConfigParameterDescription(CPU_THRESHOLD_KEY,
				LpeSupportedTypes.Double);
		requiredConfidenceLevel.setDefaultValue(String.valueOf(CPU_THRESHOLD_DEFAULT));
		requiredConfidenceLevel.setRange("0.0", "1.0");
		requiredConfidenceLevel
				.setDescription("Fix threshold strategy ONLY! This parameter defines a threshold for the DB CPU to be considered as highly utilized");
		return requiredConfidenceLevel;
	}
	
	private ConfigParameterDescription createStrategyParameter() {
		final ConfigParameterDescription scopeParameter = new ConfigParameterDescription(DETECTION_STRATEGY_KEY,
				LpeSupportedTypes.String);

		final Set<String> scopeOptions = new HashSet<>();
		scopeOptions.add(QT_STRATEGY);
		scopeOptions.add(THRESHOLD_STRATEGY);
		scopeParameter.setOptions(scopeOptions);
		scopeParameter.setDefaultValue(QT_STRATEGY);
		scopeParameter.setDescription("This parameter determines the strategy, "
				+ "used to analyse the Database congestion anti-pattern.");
		return scopeParameter;
	}

	@Override
	protected void initializeConfigurationParameters() {
		addConfigParameter(ConfigParameterDescription.createExtensionDescription(EXTENSION_DESCRIPTION));
		addConfigParameter(createConfidenceLevelParameter());
		addConfigParameter(createNumSignificantStepsParameter());
		addConfigParameter(createNumExperimentsParameter());
		addConfigParameter(createCPUThresholdParameter());
		addConfigParameter(createStrategyParameter());
	}

}
