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
package org.spotter.ext.detection.appHiccups.strategies;

import java.util.ArrayList;
import java.util.List;

import org.lpe.common.util.NumericPairList;
import org.spotter.ext.detection.appHiccups.IHiccupAnalysisStrategy;
import org.spotter.ext.detection.appHiccups.utils.Hiccup;
import org.spotter.ext.detection.appHiccups.utils.HiccupDetectionConfig;

/**
 * Applies Mean Value Analysis in order to identify hiccups.
 * 
 * @author Alexander Wert
 * 
 */
public class MVAStrategy implements IHiccupAnalysisStrategy {
	@Override
	public List<Hiccup> findHiccups(final NumericPairList<Long, Double> responsetimeSeries,
			final HiccupDetectionConfig hiccupConfig, double perfReqThreshold, double perfReqConfidence) {
		List<Hiccup> hiccups = new ArrayList<Hiccup>();
		Hiccup currentHiccup = null;
		double maxRT = Double.MIN_VALUE;
		double mvaResponseTime = 0.0;
		double responseTime = 0.0;
		long timestamp = 0L;
		for (int i = 0; i < responsetimeSeries.size(); i++) {

			timestamp = responsetimeSeries.get(i).getKey();
			responseTime = responsetimeSeries.get(i).getValue();
			mvaResponseTime = calculateWindowAverage(responsetimeSeries, i, hiccupConfig.getMvaWindowSize());

			if (mvaResponseTime > perfReqThreshold) {
				maxRT = Math.max(maxRT, responseTime);
				if (currentHiccup == null) {
					// new hiccup begin detected
					currentHiccup = new Hiccup();
					currentHiccup.setStartTimestamp(timestamp);
					hiccups.add(currentHiccup);
				}
				currentHiccup.setEndTimestamp(timestamp);
			} else {
				currentHiccup.setMaxHiccupResponseTime(maxRT);
				currentHiccup = null;
				maxRT = Double.MIN_VALUE;
			}
		}

		return hiccups;
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
}
