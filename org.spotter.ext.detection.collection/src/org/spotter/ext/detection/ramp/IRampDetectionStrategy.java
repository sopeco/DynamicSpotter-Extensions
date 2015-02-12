package org.spotter.ext.detection.ramp;

import java.util.Properties;

import org.aim.api.exceptions.InstrumentationException;
import org.aim.api.exceptions.MeasurementException;
import org.aim.api.measurement.dataset.DatasetCollection;
import org.aim.description.InstrumentationDescription;
import org.spotter.shared.result.model.SpotterResult;

/**
 * Interface for Ramp analysis strategies.
 * 
 * @author Alexander Wert
 * 
 */
public interface IRampDetectionStrategy {

	/**
	 * Sets configuration properties.
	 * 
	 * @param problemDetectionConfiguration
	 *            properties to set
	 */
	void setProblemDetectionConfiguration(Properties problemDetectionConfiguration);

	/**
	 * Sets the RampDetectionController as parent.
	 * 
	 * @param mainDetectionController
	 *            parent
	 */
	void setMainDetectionController(RampDetectionController mainDetectionController);

	/**
	 * Executes experiments.
	 * 
	 * @throws InstrumentationException
	 *             if instrumentaiton fails
	 * @throws MeasurementException
	 *             if experiment execution fails
	 */
	void executeExperiments() throws InstrumentationException, MeasurementException;

	/**
	 * Analyzes measured data.
	 * 
	 * @param data
	 *            data to analyze.
	 * @return SpotterResult
	 */
	SpotterResult analyze(DatasetCollection data);

	/**
	 * 
	 * @return estimated experiment duration
	 */
	long getExperimentSeriesDuration();
	
	 InstrumentationDescription getInstrumentationDescription();
}
