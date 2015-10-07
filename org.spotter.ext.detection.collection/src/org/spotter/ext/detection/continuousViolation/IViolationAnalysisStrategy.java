package org.spotter.ext.detection.continuousViolation;

import org.lpe.common.utils.numeric.NumericPairList;
import org.spotter.ext.detection.continuousViolation.util.AnalysisConfig;

/**
 * Interface for the violation analysis strategy.
 * 
 * @author Alexander Wert
 * 
 */
public interface IViolationAnalysisStrategy {
	/**
	 * Analyzes the response time series while searching for hiccups.
	 * 
	 * @param responsetimeSeries
	 *            series to analyze
	 * @param analysisConfig
	 *            analysis configuration
	 * @param perfReqThreshold
	 *            requirements threshold
	 * @param perfReqConfidence
	 *            confidence for performance requirement thresholdO
	 * @return true if detected
	 */
	boolean analyze(final NumericPairList<Long, Double> responsetimeSeries, final AnalysisConfig analysisConfig,
			double perfReqThreshold, double perfReqConfidence);
}
