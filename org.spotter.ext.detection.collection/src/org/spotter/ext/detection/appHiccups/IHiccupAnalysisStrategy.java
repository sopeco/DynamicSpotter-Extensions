package org.spotter.ext.detection.appHiccups;

import java.util.List;

import org.lpe.common.utils.numeric.NumericPairList;
import org.spotter.core.detection.DetectionResultManager;
import org.spotter.ext.detection.appHiccups.utils.Hiccup;
import org.spotter.ext.detection.appHiccups.utils.HiccupDetectionConfig;
import org.spotter.shared.result.model.SpotterResult;

/**
 * Analysis Strategy Interface for Application Hiccups detection.
 * 
 * @author Alexander Wert
 * 
 */
public interface IHiccupAnalysisStrategy {
	/**
	 * Analyzes the response time series while searching for hiccups.
	 * 
	 * @param responsetimeSeries
	 *            series to analyze
	 * @param hiccupConfig
	 *            hiccup detection configuration
	 * @param perfReqThreshold
	 *            requirements threshold
	 * @param perfReqConfidence
	 *            confidence for performance requirement thresholdO
	 * @return list of hiccups
	 */
	List<Hiccup> findHiccups(final NumericPairList<Long, Double> responsetimeSeries,
			final HiccupDetectionConfig hiccupConfig, double perfReqThreshold, double perfReqConfidence, DetectionResultManager resultManager, SpotterResult result);
}
