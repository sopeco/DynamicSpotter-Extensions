package org.spotter.ext.detection.continuousViolation.strategies;

import java.util.List;

import org.lpe.common.util.LpeNumericUtils;
import org.lpe.common.util.NumericPairList;
import org.spotter.ext.detection.continuousViolation.IViolationAnalysisStrategy;
import org.spotter.ext.detection.continuousViolation.util.AnalysisConfig;

/**
 * Analyzes continuous violation of performance requirements by mean value
 * analysis.
 * 
 * @author Alexander Wert
 * 
 */
public class DBSCANStrategy implements IViolationAnalysisStrategy {
	private static final int numMinNeighbours = 10;

	@Override
	public boolean analyze(NumericPairList<Long, Double> responsetimeSeries, AnalysisConfig analysisConfig,
			double perfReqThreshold, double perfReqConfidence) {
		double keyRange = responsetimeSeries.getKeyMax() - responsetimeSeries.getKeyMin();
		double valueRange = responsetimeSeries.getValueMax() - responsetimeSeries.getValueMin();
		double epsilon = LpeNumericUtils.meanNormalizedDistance(responsetimeSeries, keyRange, valueRange)
				* (double) numMinNeighbours * 0.75;
		List<NumericPairList<Long, Double>> clusters = LpeNumericUtils.dbscanNormalized(responsetimeSeries, epsilon,
				numMinNeighbours, keyRange, valueRange);

		for (NumericPairList<Long, Double> c : clusters) {
			int numViolations = countRequirementViolations(perfReqThreshold, c.getValueList());
			if (((double) numViolations) / ((double) c.size()) < 1.0 - perfReqConfidence) {
				return false;
			}

		}
		return true;

	}

	private int countRequirementViolations(double perfReqThreshold, List<Double> responseTimes) {
		int count = 0;
		for (Double rt : responseTimes) {
			if (rt > perfReqThreshold) {
				count++;
			}
		}
		return count;
	}

}
