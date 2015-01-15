/**
 * Copyright 2014 SAP AG
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.spotter.ext.detection.appHiccups.utils;

/**
 * A hiccup is specified by its start timestamp, end timestamp, max response
 * time and average response time.
 * 
 * @author C5170547
 * 
 */
public class Hiccup {
	private long startTimestamp;
	private long endTimestamp;
	private double maxHiccupResponseTime;

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
	 * 
	 * @return hiccup duration
	 */
	public long getHiccupDuration() {
		return endTimestamp - startTimestamp;
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
	 * @return the maxResponseTime
	 */
	public double getMaxHiccupResponseTime() {
		return maxHiccupResponseTime;
	}

	/**
	 * @param maxResponseTime
	 *            the maxResponseTime to set
	 */
	public void setMaxHiccupResponseTime(double maxResponseTime) {
		this.maxHiccupResponseTime = maxResponseTime;
	}

}
