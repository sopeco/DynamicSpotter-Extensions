package org.spotter.ext.detection.stifle;

import org.spotter.core.detection.AbstractDetectionExtension;
import org.spotter.core.detection.IDetectionController;

public class StifleExtension extends AbstractDetectionExtension{

	public StifleExtension() {
		super(StifleDetectionController.class);
	}

	@Override
	protected void initializeConfigurationParameters() {
		// TODO Auto-generated method stub
		
	}

}
