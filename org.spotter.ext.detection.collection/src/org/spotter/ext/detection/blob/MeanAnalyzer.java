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
package org.spotter.ext.detection.blob;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.lpe.common.util.LpeNumericUtils;
import org.lpe.common.util.NumericPairList;
import org.spotter.core.chartbuilder.AnalysisChartBuilder;
import org.spotter.core.detection.DetectionResultManager;
import org.spotter.shared.result.model.SpotterResult;

/**
 * Analysis strategy for god class identification based on exclusion of
 * individual components.
 * 
 * @author Alexander Wert
 * 
 */
public class MeanAnalyzer implements IBlobAnalyzer {

	private static final double TIME_IMPROVEMENT_THREASHOLD_PERCENT = 0.4;

	@Override
	public List<Component> analyze(ProcessedData processData, DetectionResultManager resultManager, SpotterResult result) {
		List<Component> blobs = new ArrayList<>();
		double totalMessagingTime = processData.getTotalMessagingTime();

		Map<Component, Double> msgTimes = new HashMap<Component, Double>();
		for (Component comp : processData.getComponents()) {
			double messagingTime = comp.getTotalMessageSentDuration();

			for (Component sender : processData.getComponents()) {
				if (!sender.getId().equals(comp.getId())) {
					Double receivingDuration = sender.getSendToDurationMap().get(comp.getId());
					if (receivingDuration != null) {
						messagingTime += receivingDuration;
					}
				}
			}


			msgTimes.put(comp, messagingTime);

		}

		NumericPairList<Integer, Double> ownValues = new NumericPairList<>();
		
		
		double mean = LpeNumericUtils.average(msgTimes.values());
		double sd = LpeNumericUtils.stdDev(msgTimes.values());

		double threshold = mean + 3 * sd;
		int i = 1;
		for (Component comp : msgTimes.keySet()) {
			double time = msgTimes.get(comp);
			if (time >= threshold) {
				blobs.add(comp);
			}
			ownValues.add(i, time);
			i++;
		}

		AnalysisChartBuilder chartBuilder = AnalysisChartBuilder.getChartBuilder();
		chartBuilder.startChart("Components' Messaging Times", "component", "messaging time [ms]");
		chartBuilder.addScatterSeries(ownValues, "messaging time");
		chartBuilder.addHorizontalLine(threshold, "3-Sigma threshold");
		resultManager.storeImageChartResource(chartBuilder, "Messaging Times", result);
		return blobs;

	}

}
