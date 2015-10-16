package org.spotter.ext.detection.staticspotter.olb;

import org.lpe.common.config.ConfigParameterDescription;
import org.lpe.common.util.LpeSupportedTypes;
import org.spotter.core.detection.AbstractDetectionExtension;

/**
 * OLB extension.
 * 
 * @author Steffen Becker
 * 
 */
public class StaticSpotterSynchronizedMethodExtension extends AbstractDetectionExtension {
	
	public StaticSpotterSynchronizedMethodExtension() {
		super(StaticSpotterSynchronizedMethodDetectionController.class);
	}

	private static final double _100_PERCENT = 100.0;
	private static final String EXTENSION_DESCRIPTION = "The static spotter synchronized method extension instruments an application's methods"
			+ " detected by the CloudScale static spotter as potential One Lane Bridges (i.e., Sychronized Methods)";

	public static final String CPU_UTILIZATION_THRESHOLD_KEY = "cpuThreshold";
	public static final String EXPERIMENT_STEPS_KEY = "numExperiments";
	public static final double CPU_UTILIZATION_THRESHOLD_DEFAULT = 90.0;
	public static final int EXPERIMENT_STEPS_DEFAULT = 4;
	public static final String STATIC_SPOTTER_EXPORT_FILE = "static_spotter_export_file";
	
	private ConfigParameterDescription createCpuThresholdParameter() {
	    final ConfigParameterDescription cpuThresholdParameter = new ConfigParameterDescription(
				CPU_UTILIZATION_THRESHOLD_KEY, LpeSupportedTypes.Double);
		cpuThresholdParameter.setDefaultValue(String.valueOf(CPU_UTILIZATION_THRESHOLD_DEFAULT));
		cpuThresholdParameter.setRange(String.valueOf(0.0), String.valueOf(_100_PERCENT));
		cpuThresholdParameter
				.setDescription("ONLY for t-test-CPU-threshold strategy! Defines the CPU utilization threshold, "
						+ "when a system is considered as overutilized.");
		return cpuThresholdParameter;
	}

	private ConfigParameterDescription createNumExperimentsParameter() {
		final ConfigParameterDescription numExperimentsParameter = new ConfigParameterDescription(EXPERIMENT_STEPS_KEY,
				LpeSupportedTypes.Integer);
		numExperimentsParameter.setDefaultValue(String.valueOf(EXPERIMENT_STEPS_DEFAULT));
		numExperimentsParameter.setRange(String.valueOf(2), String.valueOf(Integer.MAX_VALUE));
		numExperimentsParameter.setDescription("Number of experiments to execute with "
				+ "different number of users between 1 and max number of users.");
		return numExperimentsParameter;
	}

	private ConfigParameterDescription createStaticSpotterExportFile() {
		final ConfigParameterDescription numExperimentsParameter = new ConfigParameterDescription(STATIC_SPOTTER_EXPORT_FILE,
				LpeSupportedTypes.String);
		numExperimentsParameter.setDefaultValue("");
		numExperimentsParameter.setAFile(true);
		numExperimentsParameter.setDescription("Full file path to the file exported by Static Spotter containing potential "
				+ "spurious methods.");
		return numExperimentsParameter;
	}
	
	/* (non-Javadoc)
	 * @see org.lpe.common.extension.ReflectiveAbstractExtension#getDescription()
	 */
	@Override
	public String getDescription() {
		return EXTENSION_DESCRIPTION;
	}
	
	@Override
	protected void initializeConfigurationParameters() {
		addConfigParameter(createCpuThresholdParameter());
		addConfigParameter(createNumExperimentsParameter());
		addConfigParameter(createStaticSpotterExportFile());
	}

}
