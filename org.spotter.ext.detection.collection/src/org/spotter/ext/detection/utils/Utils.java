package org.spotter.ext.detection.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.aim.api.measurement.dataset.Dataset;
import org.aim.artifacts.records.ResponseTimeRecord;
import org.lpe.common.util.NumericPairList;

/**
 * Contains utility functions for analysis of measurement data.
 * 
 * @author Alexander Wert
 * 
 */
public final class Utils {
	private Utils() {

	}

	/**
	 * Creates a list of timestamp response time pairs from a response time
	 * dataset.
	 * 
	 * @param rtDataSet
	 *            dataset to read from
	 * @return list of timestamp response time pairs
	 */
	public static NumericPairList<Long, Double> toTimestampRTPairs(Dataset rtDataSet) {
		NumericPairList<Long, Double> responseTimeSeries = new NumericPairList<>();
		for (ResponseTimeRecord rtRecord : rtDataSet.getRecords(ResponseTimeRecord.class)) {
			responseTimeSeries.add(rtRecord.getTimeStamp(), (double) rtRecord.getResponseTime());
		}

		return responseTimeSeries;
	}

	/**
	 * Calculates the mean value for the given window of a series.
	 * 
	 * @param pairs
	 *            list of pairs
	 * @param windowCenter
	 *            index of the window center
	 * @param windowSize
	 *            window size
	 * @return mean value
	 */
	public static double calculateWindowAverage(NumericPairList<Long, Double> pairs, int windowCenter, int windowSize) {
		double mva = 0.0;

		int windowStart = Math.max(windowCenter - (windowSize / 2), 0);
		int windowEnd = Math.min(windowCenter + (windowSize / 2), pairs.size() - 1);

		for (int j = windowStart; j <= windowEnd; j++) {
			mva += pairs.get(j).getValue();
		}
		mva = mva / (double) (windowEnd - windowStart + 1);
		return mva;
	}

	/**
	 * Calculates the percentile value for the given window of a series.
	 * 
	 * @param pairs
	 *            list of pairs
	 * @param percentile
	 *            percentile of interest
	 * @param windowCenter
	 *            index of the window center
	 * @param windowSize
	 *            window size
	 * @return mean value
	 */
	public static double calculateWindowPercentile(NumericPairList<Long, Double> pairs, double percentile,
			int windowCenter, int windowSize) {
		int windowStart = Math.max(windowCenter - (windowSize / 2), 0);
		int windowEnd = Math.min(windowCenter + (windowSize / 2), pairs.size() - 1);
		int actualWindowSize = (windowEnd - windowStart) + 1;
		int indexPercentile = (int) Math.floor(((double) actualWindowSize) * percentile);

		List<Double> tmpList = new ArrayList<>(actualWindowSize);

		for (int j = windowStart; j <= windowEnd; j++) {
			tmpList.add(pairs.get(j).getValue());
		}
		Collections.sort(tmpList);
		return tmpList.get(indexPercentile);
	}

	public static long meanInterRequestTime(NumericPairList<Long, Double> responsetimeSeries) {
		long diffSum = 0L;
		long prevTimestamp = -1;
		for (Long ts : responsetimeSeries.getKeyList()) {
			if (prevTimestamp >= 0L) {
				diffSum += ts - prevTimestamp;
			}
			prevTimestamp = ts;
		}
		return diffSum / (long) (responsetimeSeries.size() - 1);
	}
}
