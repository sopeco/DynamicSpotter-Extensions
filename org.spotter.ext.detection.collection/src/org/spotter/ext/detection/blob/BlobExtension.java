package org.spotter.ext.detection.blob;

import java.util.HashSet;
import java.util.Set;

import org.lpe.common.config.ConfigParameterDescription;
import org.lpe.common.extension.IExtensionArtifact;
import org.lpe.common.util.LpeSupportedTypes;
import org.spotter.core.detection.AbstractDetectionExtension;

public class BlobExtension extends AbstractDetectionExtension {
	public BlobExtension() {
		super(BlobDetectionController.class);
	}

	protected static final String DETECTION_STRATEGY_KEY = "strategy";
	protected static final String COMP_EXCLUSION_STRATEGY = "exclusion analysis strategy";
	protected static final String MEAN_ANALYSIS_STRATEGY = "mean analysis strategy";
	private static final String EXTENSION_DESCRIPTION = 
			"The Blob occurs when one class performs most of the system "
			+ "work relegating other classes to minor, supporting roles.";

	private ConfigParameterDescription createStrategyParameter() {
		final ConfigParameterDescription scopeParameter = new ConfigParameterDescription(DETECTION_STRATEGY_KEY,
				LpeSupportedTypes.String);

		final Set<String> scopeOptions = new HashSet<>();
		scopeOptions.add(COMP_EXCLUSION_STRATEGY);
		scopeOptions.add(MEAN_ANALYSIS_STRATEGY);
		scopeParameter.setOptions(scopeOptions);
		scopeParameter.setDefaultValue(COMP_EXCLUSION_STRATEGY);
		scopeParameter.setDescription("This parameter determines the strategy, "
				+ "used to analyse the Blob anti-pattern.");
		return scopeParameter;
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
		addConfigParameter(createStrategyParameter());
	}

}
