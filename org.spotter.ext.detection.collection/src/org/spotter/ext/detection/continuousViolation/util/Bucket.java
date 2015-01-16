package org.spotter.ext.detection.continuousViolation.util;

/**
 * Analysis container for a group of measurement values.
 * 
 * @author Alexander Wert
 * 
 */
public class Bucket {
	private long startTimestamp;
	private long endTimestamp;
	private boolean requirementViolated;

	/**
	 * @return the startTimestamp
	 */
	public long getStartTimestamp() {
		return startTimestamp;
	}

	/**
	 * @param startTimestamp
	 *            the startTimestamp to set
	 */
	public void setStartTimestamp(long startTimestamp) {
		this.startTimestamp = startTimestamp;
	}

	/**
	 * @return the endTimestamp
	 */
	public long getEndTimestamp() {
		return endTimestamp;
	}

	/**
	 * @param endTimestamp
	 *            the endTimestamp to set
	 */
	public void setEndTimestamp(long endTimestamp) {
		this.endTimestamp = endTimestamp;
	}

	/**
	 * @return the requirementViolated
	 */
	public boolean isRequirementViolated() {
		return requirementViolated;
	}

	/**
	 * @param requirementViolated
	 *            the requirementViolated to set
	 */
	public void setRequirementViolated(boolean requirementViolated) {
		this.requirementViolated = requirementViolated;
	}

}
