package org.spotter.ext.detection.continuousViolation.strategies;

import org.lpe.common.utils.numeric.NumericPairList;
import org.spotter.ext.detection.continuousViolation.IViolationAnalysisStrategy;
import org.spotter.ext.detection.continuousViolation.util.AnalysisConfig;
import org.spotter.ext.detection.utils.Utils;

/**
 * Analyzes continuous violation of performance requirements by percentile value
 * analysis.
 * 
 * @author Alexander Wert
 * 
 */
public class MovingPercentileStrategy implements IViolationAnalysisStrategy {

	@Override
	public boolean analyze(NumericPairList<Long, Double> responsetimeSeries, AnalysisConfig analysisConfig,
			double perfReqThreshold, double perfReqConfidence) {
		double percentileValue = 0.0;
		for (int i = 0; i < responsetimeSeries.size(); i++) {
			percentileValue = Utils.calculateWindowPercentile(responsetimeSeries, perfReqConfidence, i,
					analysisConfig.getMvaWindowSize());

			if (percentileValue < perfReqThreshold) {
				return false;
			}
		}
		return true;
	}

}
