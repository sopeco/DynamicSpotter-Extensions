package org.spotter.ext.detection.appHiccups;

import java.util.HashSet;
import java.util.Set;

import org.lpe.common.config.ConfigParameterDescription;
import org.lpe.common.util.LpeSupportedTypes;
import org.spotter.core.detection.AbstractDetectionExtension;
import org.spotter.ext.detection.appHiccups.utils.HiccupDetectionConfig;

/**
 * Extension for Application Hiccups Detection.
 * 
 * @author Alexander Wert
 * 
 */
public class AppHiccupsExtension extends AbstractDetectionExtension {

	public AppHiccupsExtension() {
		super(AppHiccupsController.class);
	}

	private static final String EXTENSION_DESCRIPTION = "Application Hiccups "
			+ "represents the problem of periodically violated performancerequirements.";

	protected static final String APP_HICCUPS_STRATEGY_KEY = "strategy";
	protected static final String MVA_STRATEGY = "moving percentile analysis";
	protected static final String DBSCAN_STRATEGY = "DBSCAN analysis";
	protected static final String BUCKET_STRATEGY = "bucket analysis";
	protected static final String MAX_HICCUPS_TIME_PROPORTION_KEY = "maxHiccupsTimeProportion";
	protected static final double MAX_HICCUPS_TIME_PROPORTION_DEFAULT = 0.3;

	private ConfigParameterDescription createStrategyParameter() {
		final ConfigParameterDescription scopeParameter = new ConfigParameterDescription(APP_HICCUPS_STRATEGY_KEY,
				LpeSupportedTypes.String);

		final Set<String> scopeOptions = new HashSet<>();
		scopeOptions.add(MVA_STRATEGY);
		scopeOptions.add(BUCKET_STRATEGY);
		scopeOptions.add(DBSCAN_STRATEGY);
		scopeParameter.setOptions(scopeOptions);
		scopeParameter.setDefaultValue(MVA_STRATEGY);
		scopeParameter.setDescription("This parameter determines the strategy, "
				+ "used to analyse application hiccups.");
		return scopeParameter;
	}

	/* (non-Javadoc)
	 * @see org.lpe.common.extension.ReflectiveAbstractExtension#getDescription()
	 */
	@Override
	public String getDescription() {
		return EXTENSION_DESCRIPTION;
	}

	private ConfigParameterDescription maxHiccupTimeProportionParameter() {
		final ConfigParameterDescription parameter = new ConfigParameterDescription(MAX_HICCUPS_TIME_PROPORTION_KEY,
				LpeSupportedTypes.Double);
		parameter.setMandatory(false);
		parameter.setDefaultValue(String.valueOf(MAX_HICCUPS_TIME_PROPORTION_DEFAULT));
		parameter.setDescription("This parameter determines the maximum allowed proportion "
				+ "in time the hiccups may cover of the overall experiment time.");
		return parameter;
	}

	@Override
	protected void initializeConfigurationParameters() {
		addConfigParameter(createStrategyParameter());
		addConfigParameter(maxHiccupTimeProportionParameter());
		for (final ConfigParameterDescription cpd : HiccupDetectionConfig.getConfigurationParameters()) {
			addConfigParameter(cpd);
		}

	}

}
