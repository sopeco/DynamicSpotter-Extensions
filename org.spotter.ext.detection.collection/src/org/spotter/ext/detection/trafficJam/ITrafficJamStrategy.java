package org.spotter.ext.detection.trafficJam;

import java.util.Properties;

import org.aim.api.measurement.dataset.Dataset;
import org.spotter.shared.result.model.SpotterResult;

public interface ITrafficJamStrategy {
	/**
	 * Sets configuration properties.
	 * 
	 * @param problemDetectionConfiguration
	 *            properties to set
	 */
	void setProblemDetectionConfiguration(Properties problemDetectionConfiguration);

	/**
	 * Sets the TrafficJamDetectionController as parent.
	 * 
	 * @param mainDetectionController
	 *            parent
	 */
	void setMainDetectionController(TrafficJamDetectionController mainDetectionController);

	boolean analyseOperationResponseTimes(Dataset dataset, String operation, SpotterResult result);
}
