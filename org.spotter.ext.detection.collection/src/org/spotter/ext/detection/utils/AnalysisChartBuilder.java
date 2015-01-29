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
package org.spotter.ext.detection.utils;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.lpe.common.util.LpeNumericUtils;
import org.lpe.common.util.NumericPair;
import org.lpe.common.util.NumericPairList;

import com.xeiam.xchart.Chart;
import com.xeiam.xchart.ChartBuilder;
import com.xeiam.xchart.Series;
import com.xeiam.xchart.SeriesLineStyle;
import com.xeiam.xchart.SeriesMarker;
import com.xeiam.xchart.StyleManager.LegendPosition;

/**
 * Image exporter for OLB.
 * 
 * @author C5170547
 * 
 */
public final class AnalysisChartBuilder {
	private static final double _100_PERCENT = 100.0;
	private static final int IMAGE_WIDTH = 800;
	private static final int IMAGE_HEIGHT = 500;

	private static final Color[] COLORS = { Color.BLACK, Color.RED, Color.BLUE, Color.ORANGE, Color.GREEN,
			Color.YELLOW, Color.PINK, Color.MAGENTA };

	private int seriesCounter = 0;
	private Chart chart = null;
	private double xMin = Double.MAX_VALUE;
	private double xMax = Double.MIN_VALUE;
	private double yMin = Double.MAX_VALUE;
	private double yMax = Double.MIN_VALUE;

	public AnalysisChartBuilder() {
	}

	public void startChart(String title, String xLabel, String yLabel) {
		chart = new ChartBuilder().width(IMAGE_WIDTH).height(IMAGE_HEIGHT).title(title).xAxisTitle(xLabel)
				.yAxisTitle(yLabel).build();
		chart.getStyleManager().setLegendPosition(LegendPosition.InsideSE);
	}
	
	public void startChartWithoutLegend(String title, String xLabel, String yLabel) {
		chart = new ChartBuilder().width(IMAGE_WIDTH).height(IMAGE_HEIGHT).title(title).xAxisTitle(xLabel)
				.yAxisTitle(yLabel).build();
		chart.getStyleManager().setLegendPosition(LegendPosition.InsideSE);
		chart.getStyleManager().setLegendVisible(false);
	}

	public Chart build() {
		chart.getStyleManager().setXAxisMin(xMin);
		chart.getStyleManager().setXAxisMax(xMax);
		chart.getStyleManager().setYAxisMin(yMin);
		chart.getStyleManager().setYAxisMax(yMax);
		return chart;
	}

	public void addUtilizationScatterSeries(NumericPairList<? extends Number, ? extends Number> valuePairs,
			String seriesTitle, boolean scale) {
		updateAxisRanges(valuePairs.getKeyMin().doubleValue(), valuePairs.getKeyMax().doubleValue(), 0.0, _100_PERCENT);
		Series scatterSeries;
		NumericPairList<Double, Double> scaledPairs = new NumericPairList<>();

		if (scale) {
			for (NumericPair<? extends Number, ? extends Number> pair : valuePairs) {
				scaledPairs.add(pair.getKey().doubleValue(), pair.getValue().doubleValue() * _100_PERCENT);
			}
			scatterSeries = chart.addSeries(seriesTitle, scaledPairs.getKeyListAsNumbers(),
					scaledPairs.getValueListAsNumbers());
		} else {
			scatterSeries = chart.addSeries(seriesTitle, valuePairs.getKeyListAsNumbers(),
					valuePairs.getValueListAsNumbers());
		}

		scatterSeries.setLineStyle(SeriesLineStyle.NONE);
		scatterSeries.setMarker(SeriesMarker.SQUARE);
		scatterSeries.setMarkerColor(COLORS[seriesCounter % COLORS.length]);
		seriesCounter++;
	}

	public void addUtilizationLineSeries(NumericPairList<? extends Number, ? extends Number> valuePairs,
			String seriesTitle, boolean scale) {
		updateAxisRanges(valuePairs.getKeyMin().doubleValue(), valuePairs.getKeyMax().doubleValue(), 0.0, _100_PERCENT);
		Series scatterSeries;
		NumericPairList<Double, Double> scaledPairs = new NumericPairList<>();

		if (scale) {
			for (NumericPair<? extends Number, ? extends Number> pair : valuePairs) {
				scaledPairs.add(pair.getKey().doubleValue(), pair.getValue().doubleValue() * _100_PERCENT);
			}
			scatterSeries = chart.addSeries(seriesTitle, scaledPairs.getKeyListAsNumbers(),
					scaledPairs.getValueListAsNumbers());
		} else {
			scatterSeries = chart.addSeries(seriesTitle, valuePairs.getKeyListAsNumbers(),
					valuePairs.getValueListAsNumbers());
		}
		scatterSeries.setLineStyle(SeriesLineStyle.DASH_DASH);
		scatterSeries.setMarker(SeriesMarker.SQUARE);
		scatterSeries.setMarkerColor(COLORS[seriesCounter % COLORS.length]);
		seriesCounter++;
	}

	public void addScatterSeries(NumericPairList<? extends Number, ? extends Number> valuePairs, String seriesTitle) {
		updateAxisRanges(valuePairs);
		Series scatterSeries = chart.addSeries(seriesTitle, valuePairs.getKeyListAsNumbers(),
				valuePairs.getValueListAsNumbers());
		scatterSeries.setLineStyle(SeriesLineStyle.NONE);
		scatterSeries.setMarker(SeriesMarker.CIRCLE);
		scatterSeries.setMarkerColor(COLORS[seriesCounter % COLORS.length]);
		seriesCounter++;
	}

	public void addScatterSeriesWithErrorBars(NumericPairList<? extends Number, ? extends Number> valuePairs,
			List<Number> errors, String seriesTitle) {
		updateAxisRanges(valuePairs);
		Series scatterSeries = chart.addSeries(seriesTitle, valuePairs.getKeyListAsNumbers(),
				valuePairs.getValueListAsNumbers(), errors);
		scatterSeries.setLineStyle(SeriesLineStyle.NONE);
		scatterSeries.setMarker(SeriesMarker.CIRCLE);
		scatterSeries.setMarkerColor(COLORS[seriesCounter % COLORS.length]);
		seriesCounter++;
	}

	public void addLineSeries(NumericPairList<? extends Number, ? extends Number> valuePairs, String seriesTitle) {
		updateAxisRanges(valuePairs);
		Series scatterSeries = chart.addSeries(seriesTitle, valuePairs.getKeyListAsNumbers(),
				valuePairs.getValueListAsNumbers());
		scatterSeries.setLineStyle(SeriesLineStyle.SOLID);
		scatterSeries.setMarker(SeriesMarker.NONE);
		scatterSeries.setMarkerColor(COLORS[seriesCounter % COLORS.length]);
		seriesCounter++;
	}

	public void addCDFSeries(Collection<? extends Number> values, String seriesTitle) {
		updateAxisRanges(LpeNumericUtils.min(values).doubleValue(), LpeNumericUtils.max(values).doubleValue(), 0.0,
				100.0);
		int size = values.size();
		List<Number> xValues = new ArrayList<>(size);
		List<Number> yValues = new ArrayList<>(size);

		xValues.addAll(values);

		Collections.sort(xValues, new Comparator<Number>() {
			@Override
			public int compare(Number o1, Number o2) {
				if (o1.doubleValue() < o2.doubleValue()) {
					return -1;
				} else if (o1.doubleValue() == o2.doubleValue()) {
					return 0;
				} else {
					return 1;
				}
			}
		});

		double inc = 100.0 / (double) size;
		double sum = 0.0;
		for (int i = 0; i < xValues.size(); i++) {
			sum += inc;
			yValues.add(sum);
		}

		Series scatterSeries = chart.addSeries(seriesTitle, xValues, yValues);
		scatterSeries.setLineStyle(SeriesLineStyle.SOLID);
		scatterSeries.setMarker(SeriesMarker.NONE);
		scatterSeries.setMarkerColor(COLORS[seriesCounter % COLORS.length]);
		seriesCounter++;
	}

	public void addHorizontalLine(double yValue, String seriesTitle) {
		double[] xValues = new double[2];
		double[] yValues = new double[2];
		xValues[0] = xMin;
		xValues[1] = xMax;
		yValues[0] = yValue;
		yValues[1] = yValue;
		Series scatterSeries = chart.addSeries(seriesTitle, xValues, yValues);
		scatterSeries.setLineStyle(SeriesLineStyle.SOLID);
		scatterSeries.setMarker(SeriesMarker.NONE);
		scatterSeries.setMarkerColor(COLORS[seriesCounter % COLORS.length]);
		seriesCounter++;
	}

	public void addVerticalLine(double xValue, String seriesTitle) {
		double[] xValues = new double[2];
		double[] yValues = new double[2];
		xValues[0] = xValue;
		xValues[1] = xValue;
		yValues[0] = yMin;
		yValues[1] = yMax;
		Series scatterSeries = chart.addSeries(seriesTitle, xValues, yValues);
		scatterSeries.setLineStyle(SeriesLineStyle.SOLID);
		scatterSeries.setMarker(SeriesMarker.NONE);
		scatterSeries.setMarkerColor(COLORS[seriesCounter % COLORS.length]);
		seriesCounter++;
	}

	private void updateAxisRanges(NumericPairList<? extends Number, ? extends Number> valuePairs) {
		double tmpXMin = valuePairs.getKeyMin().doubleValue();
		double tmpXMax = valuePairs.getKeyMax().doubleValue();
		double tmpYMin = valuePairs.getValueMin().doubleValue();
		double tmpYMax = valuePairs.getValueMax().doubleValue();
		xMin = xMin > tmpXMin ? tmpXMin : xMin;
		xMax = xMax < tmpXMax ? tmpXMax : xMax;
		yMin = yMin > tmpYMin ? tmpYMin : yMin;
		yMax = yMax < tmpYMax ? tmpYMax : yMax;
	}

	private void updateAxisRanges(double tmpXMin, double tmpXMax, double tmpYMin, double tmpYMax) {
		xMin = xMin > tmpXMin ? tmpXMin : xMin;
		xMax = xMax < tmpXMax ? tmpXMax : xMax;
		yMin = yMin > tmpYMin ? tmpYMin : yMin;
		yMax = yMax < tmpYMax ? tmpYMax : yMax;
	}
}
