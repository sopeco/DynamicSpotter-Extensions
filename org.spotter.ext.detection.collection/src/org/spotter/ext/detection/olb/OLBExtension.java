package org.spotter.ext.detection.olb;

import java.util.HashSet;
import java.util.Set;

import org.lpe.common.config.ConfigParameterDescription;
import org.lpe.common.util.LpeSupportedTypes;
import org.spotter.core.detection.AbstractDetectionExtension;
import org.spotter.core.detection.IDetectionController;

/**
 * OLB extension.
 * 
 * @author Alexander Wert
 * 
 */
public class OLBExtension extends AbstractDetectionExtension {
	private static final double _100_PERCENT = 100.0;
	private static final String EXTENSION_DESCRIPTION = "The One Lane Bridge anti-pattern is a typical software bottleneck. ";

	public static final String CPU_UTILIZATION_THRESHOLD_KEY = "cpuThreshold";

	public static final double CPU_UTILIZATION_THRESHOLD_DEFAULT = 90.0;
	protected static final String SCOPE_KEY = "scope";
	protected static final String ENTRY_SCOPE = "entry point scope";
	protected static final String SYNC_SCOPE = "synchronization scope";
	protected static final String DB_SCOPE = "database scope";
	
	protected static final String DETECTION_STRATEGY_KEY = "strategy";
	protected static final String QUEUEING_THEORY_STRATEGY = "queueing theory strategy";
	protected static final String T_TEST_CPU_THRESHOLD_STRATEGY = "t-Test-CPU-threshold strategy";

	@Override
	public IDetectionController createExtensionArtifact() {
		return new OLBDetectionController(this);
	}

	@Override
	public String getName() {
		return "One Lane Bridge";
	}

	private ConfigParameterDescription createCpuThresholdParameter() {
		ConfigParameterDescription cpuThresholdParameter = new ConfigParameterDescription(
				CPU_UTILIZATION_THRESHOLD_KEY, LpeSupportedTypes.Double);
		cpuThresholdParameter.setDefaultValue(String.valueOf(CPU_UTILIZATION_THRESHOLD_DEFAULT));
		cpuThresholdParameter.setRange(String.valueOf(0.0), String.valueOf(_100_PERCENT));
		cpuThresholdParameter
				.setDescription("ONLY for t-test-CPU-threshold strategy! Defines the CPU utilization threshold, "
						+ "when a system is considered as overutilized.");
		return cpuThresholdParameter;
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
	
	private ConfigParameterDescription createScopeParameter() {
		ConfigParameterDescription scopeParameter = new ConfigParameterDescription(SCOPE_KEY,
				LpeSupportedTypes.String);

		Set<String> scopeOptions = new HashSet<>();
		scopeOptions.add(ENTRY_SCOPE);
		scopeOptions.add(SYNC_SCOPE);
		scopeOptions.add(DB_SCOPE);
		scopeParameter.setOptions(scopeOptions);
		scopeParameter.setDefaultValue(ENTRY_SCOPE);
		scopeParameter.setDescription("This parameter determines the scope, "
				+ "of the OLB analysis.");
		return scopeParameter;
	}

	@Override
	protected void initializeConfigurationParameters() {
		addConfigParameter(ConfigParameterDescription.createExtensionDescription(EXTENSION_DESCRIPTION));

		addConfigParameter(createCpuThresholdParameter());
		addConfigParameter(createStrategyParameter());
		addConfigParameter(createScopeParameter());
	}

}
