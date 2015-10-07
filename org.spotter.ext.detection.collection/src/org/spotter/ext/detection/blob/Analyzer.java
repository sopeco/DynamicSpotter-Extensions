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

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import org.lpe.common.utils.numeric.LpeNumericUtils;
import org.spotter.shared.result.model.SpotterResult;

/**
 * God Class data analyzer.
 * 
 * @author Alexander Wert
 * 
 */
public final class Analyzer {

	private static final int THREE_SIGMA = 3;

	private static final double _100_PERCENT = 100D;

	private static Analyzer analyzer;

	/**
	 * Analyze the given ProcessedData and put the results in the specified
	 * SpotterResult object.
	 * 
	 * @param processData
	 *            Data to analyze
	 * @param result
	 *            Object for storing the results
	 */
	public static void analyzeData(ProcessedData processData, SpotterResult result) {
		if (result == null) {
			throw new NullPointerException("SpotterResult must not be null.");
		}
		if (analyzer == null) {
			analyzer = new Analyzer();
		}
		analyzer.analyze(processData, result);
	}

	private static final DecimalFormat df = new DecimalFormat("0.000");

	private long highestReceiveCount = 0;

	private double mean;
	private double standardDeviation;

	/**
	 * Hide default constructor.
	 */
	private Analyzer() {
	}

	/**
	 * Analyze the given data.
	 * 
	 * @param processData
	 *            processed Data
	 * @param result
	 *            spotter result to add detection results to
	 */
	public void analyze(ProcessedData processData, SpotterResult result) {
		findHighestReceiveCount(processData);
		calculateMean(processData);
		calculateStandardDeviation(processData);

		// oldAnalysis(processData, result);

		newAnalysis(processData, result);
	}

	private void newAnalysis(ProcessedData processData, SpotterResult result) {
		for (Component outer : processData.getComponents()) {
			result.addMessage("Investigated component: " + outer.getId());

			List<Double> pctMsgReceivedList = new ArrayList<Double>();
			for (Component inner : processData.getComponents()) {
				if (inner == outer) {
					continue;
				}
				pctMsgReceivedList.add(getRelativeReceivePct(inner));
			}

			double currentMean = LpeNumericUtils.average(pctMsgReceivedList);
			double standardDeviation = LpeNumericUtils.stdDev(pctMsgReceivedList);

			result.addMessage("Component Pct Messages Sent:   " + df.format(getRelativeReceivePct(outer)) + "$");
			result.addMessage("Current Mean:   " + df.format(currentMean) + "$");
			result.addMessage("Current StdDev: " + df.format(standardDeviation) + "%");
			result.addMessage("Critical Threshold (Mean + 3 * SD): "
					+ df.format(currentMean + THREE_SIGMA * standardDeviation) + "%");

			if (currentMean + THREE_SIGMA * standardDeviation < getRelativeReceivePct(outer)) {
				result.addMessage("Result: As GodClass detected");
				result.setDetected(true);
			} else {
				result.addMessage("Result: not detected");
			}
			result.addMessage("* * * *");
		}
	}

	/**
	 * Find the highest message received count in the given data.
	 * 
	 * @param data
	 */
	private void findHighestReceiveCount(ProcessedData data) {
		for (Component c : data.getComponents()) {
			if (c.getMessagesReceived() > highestReceiveCount) {
				highestReceiveCount = c.getMessagesReceived();
			}
		}
	}

	/**
	 * Calculate the mean value of the relative percentage receive count.
	 * 
	 * @param data
	 */
	private void calculateMean(ProcessedData data) {
		for (Component c : data.getComponents()) {
			mean += getRelativeReceivePct(c);
		}
		mean /= data.getComponents().size();
	}

	/**
	 * Calculate the standard deviation value of the relative percentage receive
	 * count.
	 * 
	 * @param data
	 */
	private void calculateStandardDeviation(ProcessedData data) {
		for (Component c : data.getComponents()) {
			standardDeviation += Math.pow(getRelativeReceivePct(c) - mean, 2);
		}
		standardDeviation = Math.sqrt(standardDeviation / (data.getComponents().size() - 1));
	}

	/**
	 * Calculates relative to the component, which has received the most
	 * messages, the percentage of messages that were received by the specified
	 * component.
	 */
	private double getRelativeReceivePct(Component component) {
		return _100_PERCENT / highestReceiveCount * component.getMessagesReceived();
	}

}
