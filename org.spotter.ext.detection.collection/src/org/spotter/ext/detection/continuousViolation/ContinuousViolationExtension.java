package org.spotter.ext.detection.continuousViolation;

import java.util.HashSet;
import java.util.Set;

import org.lpe.common.config.ConfigParameterDescription;
import org.lpe.common.util.LpeSupportedTypes;
import org.spotter.core.detection.AbstractDetectionExtension;
import org.spotter.ext.detection.continuousViolation.util.AnalysisConfig;

/**
 * Extensions for the detection of continuoous performance requirements
 * violation.
 * 
 * @author Alexander Wert
 * 
 */
public class ContinuousViolationExtension extends AbstractDetectionExtension {
	public ContinuousViolationExtension() {
		super(ContinuousViolationController.class);
	}

	private static final String EXTENSION_DESCRIPTION = "Checks if performance requirements are "
			+ "violated continuously under high load.";
	
	protected static final String VIOLATION_DETECTION_STRATEGY_KEY = "strategy";
	protected static final String DBSCAN_STRATEGY = "DBSCAN analysis";
	protected static final String PERCENTILE_STRATEGY = "moving percentile analysis";
	protected static final String BUCKET_STRATEGY = "bucket analysis";

	/* (non-Javadoc)
	 * @see org.lpe.common.extension.ReflectiveAbstractExtension#getDescription()
	 */
	@Override
	public String getDescription() {
		return EXTENSION_DESCRIPTION;
	}

	private ConfigParameterDescription createStrategyParameter() {
		final ConfigParameterDescription scopeParameter = new ConfigParameterDescription(VIOLATION_DETECTION_STRATEGY_KEY,
				LpeSupportedTypes.String);

		final Set<String> scopeOptions = new HashSet<>();
		scopeOptions.add(DBSCAN_STRATEGY);
		scopeOptions.add(BUCKET_STRATEGY);
		scopeOptions.add(PERCENTILE_STRATEGY);
		scopeParameter.setOptions(scopeOptions);
		scopeParameter.setDefaultValue(DBSCAN_STRATEGY);
		scopeParameter.setDescription("This parameter determines the strategy, "
				+ "used to analyse continuous violation of requirements.");
		return scopeParameter;
	}
	
	@Override
	protected void initializeConfigurationParameters() {
		addConfigParameter(createStrategyParameter());
		for (final ConfigParameterDescription cpd : AnalysisConfig.getConfigurationParameters()) {
			addConfigParameter(cpd);
		}

	}

}
