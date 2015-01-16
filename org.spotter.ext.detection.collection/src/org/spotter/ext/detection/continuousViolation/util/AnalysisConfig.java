package org.spotter.ext.detection.continuousViolation.util;

import java.util.HashSet;
import java.util.Set;

import org.lpe.common.config.ConfigParameterDescription;
import org.lpe.common.util.LpeSupportedTypes;

/**
 * Analysis Configuration.
 * 
 * @author Alexander Wert
 * 
 */
public class AnalysisConfig {
	public static final String MOVING_AVERAGE_WINDOW_SIZE_KEY = "mvaWindowSize";
	public static final int MOVING_AVERAGE_WINDOW_SIZE_DEFAULT = 11;
	private int mvaWindowSize; // should be an odd number

	public static final String BUCKET_STEP_KEY = "bucketWidthKey";
	public static final long BUCKET_STEP_DEFAULT = 1000;
	private int bucketStep; // should be an odd number
	
	
	public static final String MIN_BUCKET_TIME_PROPORTION_KEY = "minBucketTimeProportion";
	public static final double MIN_BUCKET_TIME_PROPORTION_DEFAULT = 0.8;
	
	private double minBucketTimeProportion;

	/**
	 * @return the mvaWindowSize
	 */
	public int getMvaWindowSize() {
		return mvaWindowSize;
	}

	/**
	 * @param mvaWindowSize
	 *            the mvaWindowSize to set
	 */
	public void setMvaWindowSize(int mvaWindowSize) {
		this.mvaWindowSize = mvaWindowSize;
	}

	/**
	 * @return the bucketStep
	 */
	public int getBucketStep() {
		return bucketStep;
	}

	/**
	 * @param bucketStep
	 *            the bucketStep to set
	 */
	public void setBucketStep(int bucketStep) {
		this.bucketStep = bucketStep;
	}

	
	/**
	 * 
	 * @return set of configuration parameters for hiccup detection
	 */
	public static Set<ConfigParameterDescription> getConfigurationParameters() {
		ConfigParameterDescription mvaWindowSizeParameter = new ConfigParameterDescription(
				MOVING_AVERAGE_WINDOW_SIZE_KEY, LpeSupportedTypes.Integer);
		mvaWindowSizeParameter
				.setDescription("ONLY for Moving Average Analysis Strategy! Defines the window size for calculating "
						+ "the moving average on a response time series.");
		mvaWindowSizeParameter.setDefaultValue(String.valueOf(MOVING_AVERAGE_WINDOW_SIZE_DEFAULT));
		mvaWindowSizeParameter.setMandatory(false);

		ConfigParameterDescription bucketStepParameter = new ConfigParameterDescription(BUCKET_STEP_KEY,
				LpeSupportedTypes.Integer);
		bucketStepParameter
				.setDescription("ONLY for Bucket Analysis Strategy! Defines the bucket width in milliseconds.");
		bucketStepParameter.setDefaultValue(String.valueOf(BUCKET_STEP_DEFAULT));
		bucketStepParameter.setMandatory(false);

		ConfigParameterDescription parameter = new ConfigParameterDescription(MIN_BUCKET_TIME_PROPORTION_KEY,
				LpeSupportedTypes.Double);
		parameter.setMandatory(false);
		parameter.setDefaultValue(String.valueOf(MIN_BUCKET_TIME_PROPORTION_DEFAULT));
		parameter.setDescription("For Bucket Analysis ONLY! This parameter determines the minimum number of buckets that "
				+ "must violate requirements in order to detect that problem.");

		
		Set<ConfigParameterDescription> set = new HashSet<>();
		set.add(mvaWindowSizeParameter);
		set.add(bucketStepParameter);
		set.add(parameter);
		return set;
	}

	/**
	 * @return the minBucketTimeProportion
	 */
	public double getMinBucketTimeProportion() {
		return minBucketTimeProportion;
	}

	/**
	 * @param minBucketTimeProportion the minBucketTimeProportion to set
	 */
	public void setMinBucketTimeProportion(double minBucketTimeProportion) {
		this.minBucketTimeProportion = minBucketTimeProportion;
	}
}
