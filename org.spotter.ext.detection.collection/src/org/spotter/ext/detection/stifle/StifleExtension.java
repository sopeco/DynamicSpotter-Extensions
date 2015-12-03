package org.spotter.ext.detection.stifle;

import org.spotter.core.detection.AbstractDetectionExtension;
import org.spotter.core.detection.IDetectionController;

public class StifleExtension extends AbstractDetectionExtension{
	public StifleExtension() {
		super(StifleDetectionController.class);
	}
	
	private static final String EXTENSION_DESCRIPTION = 
			"The Stifle occurs if single database statements changing the data "
			+ "are executed for the same table in a loop instead in a batch. ";

	/* (non-Javadoc)
	 * @see org.lpe.common.extension.ReflectiveAbstractExtension#getDescription()
	 */
	@Override
	public String getDescription() {
		return EXTENSION_DESCRIPTION;
	}
	
	@Override
	protected void initializeConfigurationParameters() {
		// TODO Auto-generated method stub
		
	}

}
