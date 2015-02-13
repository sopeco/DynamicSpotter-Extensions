package org.spotter.ext.detection.edc;

import java.util.Properties;

import org.aim.api.measurement.dataset.DatasetCollection;
import org.spotter.shared.result.model.SpotterResult;

/**
 * Interface for analysis strategies.
 * 
 * @author Henning Schulz
 * 
 */
public interface IEDCAnalysisStrategy {

	/**
	 * Set configuration and calling controller.
	 * 
	 * @param problemDetectionConfiguration
	 *            Configuration to be used
	 * @param controller
	 *            Calling controller
	 */
	void init(Properties problemDetectionConfiguration, EDCDetectionController controller);

	/**
	 * Set the gained measurement data.
	 * 
	 * @param data
	 *            measurement data
	 */
	void setMeasurementData(DatasetCollection data);

	/**
	 * Analyze the given data.
	 * 
	 * @return
	 */
	SpotterResult analyze();

}
