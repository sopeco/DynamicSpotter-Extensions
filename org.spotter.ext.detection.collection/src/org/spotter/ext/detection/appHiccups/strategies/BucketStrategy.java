package org.spotter.ext.detection.appHiccups.strategies;

import java.util.ArrayList;
import java.util.List;

import org.lpe.common.utils.numeric.NumericPairList;
import org.spotter.core.detection.DetectionResultManager;
import org.spotter.ext.detection.appHiccups.IHiccupAnalysisStrategy;
import org.spotter.ext.detection.appHiccups.utils.Hiccup;
import org.spotter.ext.detection.appHiccups.utils.HiccupDetectionConfig;
import org.spotter.ext.detection.utils.Utils;
import org.spotter.shared.result.model.SpotterResult;

/**
 * Bucket strategy devides the experiment time in fixed-sizes buckets and
 * analyzes each bucket whether it conforms to the performance requirements, in
 * order to identify hiccups.
 * 
 * @author Alexander Wert
 * 
 */
public class BucketStrategy implements IHiccupAnalysisStrategy {

	@Override
	public List<Hiccup> findHiccups(NumericPairList<Long, Double> responsetimeSeries,
			HiccupDetectionConfig hiccupConfig, double perfReqThreshold, double perfReqConfidence,
			DetectionResultManager resultManager, SpotterResult result) {
		List<Hiccup> hiccups = new ArrayList<Hiccup>();
		Hiccup currentHiccup = null;
		double maxRT = Double.MIN_VALUE;
		long bucketStart = Long.MIN_VALUE;
		long timestamp;
		long bucketStep = Math.max(5000, Utils.meanInterRequestTime(responsetimeSeries) * 50);
		NumericPairList<Long, Double> bucketSeries = null;
		for (int i = 0; i < responsetimeSeries.size(); i++) {
			timestamp = responsetimeSeries.get(i).getKey();
			if (timestamp > bucketStart + bucketStep) {
				// new bucket started
				if (bucketSeries != null) {
					// analyze previous bucket
					List<Double> responseTimes = bucketSeries.getValueListAsDouble();
					int reqViolationsCount = countRequirementViolations(perfReqThreshold, responseTimes);

					double percentageViolations = ((double) reqViolationsCount) / ((double) responseTimes.size());
					if (percentageViolations > perfReqConfidence) {
						// new hiccup started
						maxRT = Math.max(maxRT, bucketSeries.getValueMax());
						if (currentHiccup == null) {
							// new hiccup begin detected
							currentHiccup = new Hiccup();
							currentHiccup.setStartTimestamp(bucketSeries.getKeyMin());
							hiccups.add(currentHiccup);
						}
						currentHiccup.setEndTimestamp(bucketSeries.getKeyMax());
					} else {
						if (currentHiccup != null) {
							currentHiccup.setMaxHiccupResponseTime(maxRT);
							currentHiccup = null;
						}
						maxRT = Double.MIN_VALUE;
					}
				}
				bucketSeries = new NumericPairList<>();
				bucketStart = timestamp;
			}
			bucketSeries.add(responsetimeSeries.get(i));
		}

		if (bucketSeries != null && bucketSeries.size() > 0) {
			// analyze previous bucket
			List<Double> responseTimes = bucketSeries.getValueListAsDouble();
			int reqViolationsCount = countRequirementViolations(perfReqThreshold, responseTimes);

			double percentageViolations = ((double) reqViolationsCount) / ((double) responseTimes.size());
			if (percentageViolations > perfReqConfidence) {
				// new hiccup started
				maxRT = Math.max(maxRT, bucketSeries.getValueMax());
				if (currentHiccup == null) {
					// new hiccup begin detected
					currentHiccup = new Hiccup();
					currentHiccup.setStartTimestamp(bucketSeries.getKeyMin());
					hiccups.add(currentHiccup);
				}
				currentHiccup.setEndTimestamp(bucketSeries.getKeyMax());
			}

			if (currentHiccup != null) {
				currentHiccup.setMaxHiccupResponseTime(maxRT);
			}
		}

		return hiccups;
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
