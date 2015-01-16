package org.spotter.ext.detection.continuousViolation.strategies;

import java.util.ArrayList;
import java.util.List;

import org.lpe.common.util.NumericPairList;
import org.spotter.ext.detection.continuousViolation.IViolationAnalysisStrategy;
import org.spotter.ext.detection.continuousViolation.util.AnalysisConfig;
import org.spotter.ext.detection.continuousViolation.util.Bucket;

/**
 * Analyzes continuous performance requirement violation by iterating over
 * buckets.
 * 
 * @author Alexander Wert
 * 
 */
public class BucketStrategy implements IViolationAnalysisStrategy {

	@Override
	public boolean analyze(NumericPairList<Long, Double> responsetimeSeries, AnalysisConfig analysisConfig,
			double perfReqThreshold, double perfReqConfidence) {
		List<Bucket> buckets = new ArrayList<Bucket>();

		long bucketStart = Long.MIN_VALUE;
		long timestamp;
		NumericPairList<Long, Double> bucketSeries = null;
		for (int i = 0; i < responsetimeSeries.size(); i++) {
			timestamp = responsetimeSeries.get(i).getKey();
			if (timestamp > bucketStart + analysisConfig.getBucketStep()) {
				// new bucket started
				if (bucketSeries != null) {
					// analyze previous bucket
					List<Double> responseTimes = bucketSeries.getValueListAsDouble();
					int reqViolationsCount = countRequirementViolations(perfReqThreshold, responseTimes);

					double percentageViolations = ((double) reqViolationsCount) / ((double) responseTimes.size());
					Bucket bucket = new Bucket();
					bucket.setStartTimestamp(bucketSeries.getKeyMin());
					bucket.setEndTimestamp(bucketSeries.getKeyMax());
					bucket.setRequirementViolated(percentageViolations > 1.0 - perfReqConfidence);
					buckets.add(bucket);
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
			Bucket bucket = new Bucket();
			bucket.setStartTimestamp(bucketSeries.getKeyMin());
			bucket.setEndTimestamp(bucketSeries.getKeyMax());
			bucket.setRequirementViolated(percentageViolations > 1.0 - perfReqConfidence);
			buckets.add(bucket);
		}
		int numViolatingBuckets = countViolatingBuckets(buckets);
		return ((double) numViolatingBuckets) / ((double) buckets.size()) > analysisConfig.getMinBucketTimeProportion();
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

	private int countViolatingBuckets(List<Bucket> buckets) {
		int count = 0;
		for (Bucket b : buckets) {
			if (b.isRequirementViolated()) {
				count++;
			}
		}
		return count;
	}

}
