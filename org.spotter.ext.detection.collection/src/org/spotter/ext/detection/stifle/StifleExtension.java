package org.spotter.ext.detection.stifle;

import org.spotter.core.detection.AbstractDetectionExtension;
import org.spotter.core.detection.IDetectionController;

public class StifleExtension extends AbstractDetectionExtension{

	@SuppressWarnings("unchecked")
	@Override
	public IDetectionController createExtensionArtifact(final String ... args) {
		return new StifleDetectionController(this);
	}

	@Override
	public String getName() {
		return "Stifle";
	}

	@Override
	protected void initializeConfigurationParameters() {
		// TODO Auto-generated method stub
		
	}

}
