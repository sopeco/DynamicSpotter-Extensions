package org.spotter.ext.detection.appHiccups.strategies;

import java.util.ArrayList;
import java.util.List;

import org.lpe.common.utils.numeric.LpeNumericUtils;
import org.lpe.common.utils.numeric.NumericPairList;
import org.spotter.core.chartbuilder.AnalysisChartBuilder;
import org.spotter.core.detection.DetectionResultManager;
import org.spotter.ext.detection.appHiccups.IHiccupAnalysisStrategy;
import org.spotter.ext.detection.appHiccups.utils.Hiccup;
import org.spotter.ext.detection.appHiccups.utils.HiccupDetectionConfig;
import org.spotter.shared.result.model.SpotterResult;

public class DBSCANStrategy implements IHiccupAnalysisStrategy {

	private static final int numMinNeighbours = 20;

	@Override
	public List<Hiccup> findHiccups(NumericPairList<Long, Double> responsetimeSeries,
			HiccupDetectionConfig hiccupConfig, double perfReqThreshold, double perfReqConfidence,
			DetectionResultManager resultManager, SpotterResult result) {
		List<Hiccup> hiccups = new ArrayList<Hiccup>();
		double keyRange = responsetimeSeries.getKeyMax() - responsetimeSeries.getKeyMin();
		double valueRange = responsetimeSeries.getValueMax() - responsetimeSeries.getValueMin();
		double epsilon = LpeNumericUtils.meanNormalizedDistance(responsetimeSeries, keyRange, valueRange);
		List<NumericPairList<Long, Double>> clusters = LpeNumericUtils.dbscanNormalized(responsetimeSeries, epsilon,
				numMinNeighbours, keyRange, valueRange);

		for (NumericPairList<Long, Double> c : clusters) {
			int numViolations = countRequirementViolations(perfReqThreshold, c.getValueList());
			if (((double) numViolations) / ((double) c.size()) > 1.0 - perfReqConfidence) {
				Hiccup hiccup = new Hiccup();
				hiccup.setStartTimestamp(c.getKeyMin());
				hiccup.setEndTimestamp(c.getKeyMax());
				hiccup.setMaxHiccupResponseTime(c.getValueMax());
				hiccups.add(hiccup);
			}

		}

		AnalysisChartBuilder chartBuilder = AnalysisChartBuilder.getChartBuilder();
		chartBuilder.startChartWithoutLegend("Clusters", "Experiment Time [ms]", "Response Time [ms]");

		int i = 1;
		for (NumericPairList<Long, Double> c : clusters) {
			chartBuilder.addFixScaledTimeSeries(c, "Cluster " + i, 1.0 / 1000.0 / 60.0);
			i++;
		}
		chartBuilder.addHorizontalLine(perfReqThreshold, "Performance Requirement");
		resultManager.storeImageChartResource(chartBuilder, "Response Time Clusters", result);
		return hiccups;
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

}
