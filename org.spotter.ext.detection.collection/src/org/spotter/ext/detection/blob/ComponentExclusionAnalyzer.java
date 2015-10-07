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

import org.lpe.common.utils.numeric.LpeNumericUtils;
import org.lpe.common.utils.numeric.NumericPairList;
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
public class ComponentExclusionAnalyzer implements IBlobAnalyzer {

	@Override
	public List<Component> analyze(ProcessedData processData, DetectionResultManager resultManager, SpotterResult result) {
		List<Component> blobs = new ArrayList<>();
		double totalMessagingTime = processData.getTotalMessagingTime();
double totalNumMessages = Math.max(processData.getTotalMessagesReceived(),processData.getTotalMessagesSent());
		Map<Component, Double> msgContributions = new HashMap<Component, Double>();
		Map<Component, Double> msgNumContributions = new HashMap<Component, Double>();
		for (Component comp : processData.getComponents()) {
			double messagingTimeWithoutComp = totalMessagingTime - comp.getTotalMessageSentDuration();
			double messagesNumWithoutComp = totalNumMessages - comp.getMessagesSent()-comp.getMessagesReceived();
			for (Component sender : processData.getComponents()) {
				if (!sender.getId().equals(comp.getId())) {
					Double receivingDuration = sender.getSendToDurationMap().get(comp.getId());
					if (receivingDuration != null) {
						messagingTimeWithoutComp -= receivingDuration;
					}

				}
			}

			double msgTimeContribution = 1.0 - messagingTimeWithoutComp / totalMessagingTime;
			double msgNumContribution = 1.0 - messagesNumWithoutComp / totalNumMessages;
			msgNumContributions.put(comp, msgNumContribution);
			msgContributions.put(comp, msgTimeContribution);
		}
		NumericPairList<Integer, Double> ownValues = new NumericPairList<>();
		NumericPairList<Integer, Double> excludedMeans = new NumericPairList<>();
		List<Number> excludedThresholds = new ArrayList<>();
		int i = 0;
		for (Component comp : processData.getComponents()) {
			List<Double> conts = new ArrayList<>();
			List<Double> contsNum = new ArrayList<>();
			for (Component otherComp : processData.getComponents()) {
				if (!otherComp.equals(comp)) {
					conts.add(msgContributions.get(otherComp));
					contsNum.add(msgNumContributions.get(otherComp));
				}
			}

			double mean = LpeNumericUtils.average(conts);
			double sd = LpeNumericUtils.stdDev(conts);
			double threshold = mean + 3.0 * sd;
			double ownControbution = msgContributions.get(comp);
			
			double meanNum = LpeNumericUtils.average(contsNum);
			double sdNum = LpeNumericUtils.stdDev(contsNum);
			double thresholdNum = meanNum + 3.0 * sdNum;
			double ownControbutionNum = msgNumContributions.get(comp);
			
			ownValues.add(i, ownControbution);
			excludedMeans.add(i, mean);
			excludedThresholds.add(3 * sd);
			if (ownControbution > threshold || ownControbutionNum >thresholdNum ) {
				blobs.add(comp);
			}
		}

		AnalysisChartBuilder chartBuilder = AnalysisChartBuilder.getChartBuilder();
		chartBuilder.startChart("Components' Messaging Contributions", "component", "messaging time [ms]");
		chartBuilder.addScatterSeries(ownValues, "messaging contribution");
		chartBuilder.addScatterSeriesWithErrorBars(excludedMeans, excludedThresholds, "individual thresholds");
		resultManager.storeImageChartResource(chartBuilder, "Messaging Contributions", result);
		return blobs;
	}

}
