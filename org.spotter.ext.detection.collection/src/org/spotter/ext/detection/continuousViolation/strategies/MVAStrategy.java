package org.spotter.ext.detection.continuousViolation.strategies;

import org.lpe.common.util.NumericPairList;
import org.spotter.ext.detection.continuousViolation.IViolationAnalysisStrategy;
import org.spotter.ext.detection.continuousViolation.util.AnalysisConfig;
import org.spotter.ext.detection.utils.Utils;

/**
 * Analyzes continuous violation of performance requirements by mean value
 * analysis.
 * 
 * @author Alexander Wert
 * 
 */
public class MVAStrategy implements IViolationAnalysisStrategy {

	@Override
	public boolean analyze(NumericPairList<Long, Double> responsetimeSeries, AnalysisConfig analysisConfig,
			double perfReqThreshold, double perfReqConfidence) {
		double mvaResponseTime = 0.0;
		for (int i = 0; i < responsetimeSeries.size(); i++) {
			mvaResponseTime = Utils.calculateWindowAverage(responsetimeSeries, i, analysisConfig.getMvaWindowSize());

			if (mvaResponseTime < perfReqThreshold) {
				return false;
			}
		}
		return true;
	}

}
