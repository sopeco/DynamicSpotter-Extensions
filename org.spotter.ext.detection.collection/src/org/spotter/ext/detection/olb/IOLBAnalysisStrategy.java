package org.spotter.ext.detection.olb;

import java.util.Properties;

import org.aim.api.measurement.dataset.DatasetCollection;
import org.spotter.ext.detection.trafficJam.TrafficJamDetectionController;
import org.spotter.shared.result.model.SpotterResult;

/**
 * Interface for analysis strategies.
 * @author Alexander Wert
 *
 */
public interface IOLBAnalysisStrategy {
	
	/**
	 * analyse.
	 * @param data data to analyse
	 * @return detection result
	 */
	SpotterResult analyze(DatasetCollection data);
	
	/**
	 * Sets configuration properties.
	 * 
	 * @param problemDetectionConfiguration
	 *            properties to set
	 */
	void setProblemDetectionConfiguration(Properties problemDetectionConfiguration);
	
	/**
	 * Sets the OLBDetectionController as parent.
	 * 
	 * @param mainDetectionController
	 *            parent
	 */
	void setMainDetectionController(OLBDetectionController mainDetectionController);
}
