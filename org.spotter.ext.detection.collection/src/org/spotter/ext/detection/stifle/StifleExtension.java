package org.spotter.ext.detection.stifle;

import org.spotter.core.detection.AbstractDetectionExtension;
import org.spotter.core.detection.IDetectionController;

public class StifleExtension extends AbstractDetectionExtension{

	@Override
	public IDetectionController createExtensionArtifact() {
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
