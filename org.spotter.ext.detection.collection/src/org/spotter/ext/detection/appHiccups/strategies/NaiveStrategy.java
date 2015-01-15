package org.spotter.ext.detection.appHiccups.strategies;

import java.util.ArrayList;
import java.util.List;

import org.lpe.common.util.NumericPairList;
import org.spotter.ext.detection.appHiccups.IHiccupAnalysisStrategy;
import org.spotter.ext.detection.appHiccups.utils.Hiccup;
import org.spotter.ext.detection.appHiccups.utils.HiccupDetectionConfig;

/**
 * Naive hiccup detection: each violation is recognized as hiccup.
 * 
 * @author Alexander Wert
 * 
 */
public class NaiveStrategy implements IHiccupAnalysisStrategy {

	@Override
	public List<Hiccup> findHiccups(NumericPairList<Long, Double> responsetimeSeries,
			HiccupDetectionConfig hiccupConfig, double perfReqThreshold, double perfReqConfidence) {
		List<Hiccup> hiccups = new ArrayList<Hiccup>();
		Hiccup currentHiccup = null;
		double maxRT = Double.MIN_VALUE;
		double responseTime = 0.0;
		long timestamp = 0L;
		for (int i = 0; i < responsetimeSeries.size(); i++) {

			timestamp = responsetimeSeries.get(i).getKey();
			responseTime = responsetimeSeries.get(i).getValue();

			if (responseTime > perfReqThreshold) {
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

}
