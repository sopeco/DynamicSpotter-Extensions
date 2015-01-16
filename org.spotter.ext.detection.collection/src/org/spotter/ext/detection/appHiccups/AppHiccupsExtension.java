package org.spotter.ext.detection.appHiccups;

import java.util.HashSet;
import java.util.Set;

import org.lpe.common.config.ConfigParameterDescription;
import org.lpe.common.util.LpeSupportedTypes;
import org.spotter.core.detection.AbstractDetectionExtension;
import org.spotter.core.detection.IDetectionController;
import org.spotter.ext.detection.appHiccups.utils.HiccupDetectionConfig;

/**
 * Extension for Application Hiccups Detection.
 * 
 * @author Alexander Wert
 * 
 */
public class AppHiccupsExtension extends AbstractDetectionExtension {

	private static final String EXTENSION_DESCRIPTION = "Application Hiccups "
			+ "represents the problem of periodically violated performancerequirements.";

	protected static final String APP_HICCUPS_STRATEGY_KEY = "strategy";
	protected static final String MVA_STRATEGY = "mean value analysis";
	protected static final String NAIVE_STRATEGY = "simple analysis";
	protected static final String BUCKET_STRATEGY = "bucket analysis";
	protected static final String MAX_HICCUPS_TIME_PROPORTION_KEY = "maxHiccupsTimeProportion";
	protected static final double MAX_HICCUPS_TIME_PROPORTION_DEFAULT = 0.4;

	@Override
	public IDetectionController createExtensionArtifact() {
		return new AppHiccupsController(this);
	}

	@Override
	public String getName() {
		return "Application Hiccups";
	}

	private ConfigParameterDescription createStrategyParameter() {
		ConfigParameterDescription scopeParameter = new ConfigParameterDescription(APP_HICCUPS_STRATEGY_KEY,
				LpeSupportedTypes.String);

		Set<String> scopeOptions = new HashSet<>();
		scopeOptions.add(MVA_STRATEGY);
		scopeOptions.add(BUCKET_STRATEGY);
		scopeOptions.add(NAIVE_STRATEGY);
		scopeParameter.setOptions(scopeOptions);
		scopeParameter.setDefaultValue(MVA_STRATEGY);
		scopeParameter.setDescription("This parameter determines the strategy, "
				+ "used to analyse application hiccups.");
		return scopeParameter;
	}

	private ConfigParameterDescription maxHiccupTimeProportionParameter() {
		ConfigParameterDescription parameter = new ConfigParameterDescription(MAX_HICCUPS_TIME_PROPORTION_KEY,
				LpeSupportedTypes.Double);
		parameter.setMandatory(false);
		parameter.setDefaultValue(String.valueOf(MAX_HICCUPS_TIME_PROPORTION_DEFAULT));
		parameter.setDescription("This parameter determines the maximum allowed proportion "
				+ "in time the hiccups may cover of the overall experiment time.");
		return parameter;
	}

	@Override
	protected void initializeConfigurationParameters() {
		addConfigParameter(ConfigParameterDescription.createExtensionDescription(EXTENSION_DESCRIPTION));
		addConfigParameter(createStrategyParameter());
		addConfigParameter(maxHiccupTimeProportionParameter());
		for (ConfigParameterDescription cpd : HiccupDetectionConfig.getConfigurationParameters()) {
			addConfigParameter(cpd);
		}

	}

}
