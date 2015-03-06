package org.spotter.ext.detection.dbcongestion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.aim.api.exceptions.InstrumentationException;
import org.aim.api.exceptions.MeasurementException;
import org.aim.api.measurement.dataset.Dataset;
import org.aim.api.measurement.dataset.DatasetCollection;
import org.aim.api.measurement.dataset.ParameterSelection;
import org.aim.artifacts.records.CPUUtilizationRecord;
import org.aim.artifacts.records.DBStatisticsRecrod;
import org.aim.artifacts.sampler.CPUSampler;
import org.aim.description.InstrumentationDescription;
import org.aim.description.builder.InstrumentationDescriptionBuilder;
import org.aim.description.sampling.SamplingDescription;
import org.lpe.common.config.ConfigParameterDescription;
import org.lpe.common.config.GlobalConfiguration;
import org.lpe.common.extension.IExtension;
import org.lpe.common.util.LpeNumericUtils;
import org.lpe.common.util.NumericPairList;
import org.spotter.core.chartbuilder.AnalysisChartBuilder;
import org.spotter.core.detection.AbstractDetectionController;
import org.spotter.core.detection.IDetectionController;
import org.spotter.core.detection.IExperimentReuser;
import org.spotter.exceptions.WorkloadException;
import org.spotter.shared.configuration.ConfigKeys;
import org.spotter.shared.result.model.SpotterResult;

public class DBCongestionDetectionController extends AbstractDetectionController implements IExperimentReuser {

	private int requiredSignificantSteps;
	private double requiredSignificanceLevel;
	private double cpuThreshold;
	private int experimentSteps;
	private boolean qtStrategy = false;

	public DBCongestionDetectionController(IExtension<IDetectionController> provider) {
		super(provider);
		// TODO Auto-generated constructor stub
	}

	@Override
	public void loadProperties() {
		String experimentStepsStr = getProblemDetectionConfiguration().getProperty(
				DBCongestionExtension.EXPERIMENT_STEPS_KEY);
		experimentSteps = experimentStepsStr != null ? Integer.parseInt(experimentStepsStr)
				: DBCongestionExtension.EXPERIMENT_STEPS_DEFAULT;

		String requiredSignificantStepsStr = getProblemDetectionConfiguration().getProperty(
				DBCongestionExtension.REQUIRED_SIGNIFICANT_STEPS_KEY);
		requiredSignificantSteps = requiredSignificantStepsStr != null ? Integer.parseInt(requiredSignificantStepsStr)
				: DBCongestionExtension.REQUIRED_SIGNIFICANT_STEPS_DEFAULT;

		String requiredConfidenceLevelStr = getProblemDetectionConfiguration().getProperty(
				DBCongestionExtension.REQUIRED_CONFIDENCE_LEVEL_KEY);
		requiredSignificanceLevel = 1.0 - (requiredConfidenceLevelStr != null ? Double
				.parseDouble(requiredConfidenceLevelStr) : DBCongestionExtension.REQUIRED_CONFIDENCE_LEVEL_DEFAULT);

		String cpuThresholdStr = getProblemDetectionConfiguration()
				.getProperty(DBCongestionExtension.CPU_THRESHOLD_KEY);
		cpuThreshold = cpuThresholdStr != null ? Double.parseDouble(cpuThresholdStr)
				: DBCongestionExtension.CPU_THRESHOLD_DEFAULT;

		String tmpStrategy = getProblemDetectionConfiguration().getProperty(
				DBCongestionExtension.DETECTION_STRATEGY_KEY);
		switch (tmpStrategy) {
		case DBCongestionExtension.THRESHOLD_STRATEGY:
			qtStrategy = false;
			break;
		case DBCongestionExtension.QT_STRATEGY:
			qtStrategy = true;
			break;
		default:
			qtStrategy = false;
			break;
		}
	}

	@Override
	public long getExperimentSeriesDuration() {
		return 0;
	}

	@Override
	public void executeExperiments() throws InstrumentationException, MeasurementException, WorkloadException {
		executeDefaultExperimentSeries(this, experimentSteps, getInstrumentationDescription());
	}

	@Override
	protected SpotterResult analyze(DatasetCollection data) {
		SpotterResult result = new SpotterResult();

		Dataset dbDataset = data.getDataSet(DBStatisticsRecrod.class);

		for (String dbId : dbDataset.getValueSet(DBStatisticsRecrod.PAR_PROCESS_ID, String.class)) {
			List<Integer> sortedNumUsersList = new ArrayList<Integer>(dbDataset.getValueSet(
					AbstractDetectionController.NUMBER_OF_USERS_KEY, Integer.class));
			boolean detected = analyzeDBStatistics(dbDataset, dbId, sortedNumUsersList, result);
			if (detected) {
				result.setDetected(true);
				result.addMessage("Database overhead detected on database " + dbId
						+ " due to increasing locking times!");
			}
		}

		Dataset dbUtilDataset = data.getDataSet(CPUUtilizationRecord.class);
		String dbHostStr = GlobalConfiguration.getInstance().getProperty(ConfigKeys.SYSTEM_NODE_ROLE_DB);
		List<String> dbHosts = new ArrayList<>();
		for (String host : dbHostStr.split(ConfigParameterDescription.LIST_VALUE_SEPARATOR)) {
			dbHosts.add(host);
		}
		for (String processID : dbUtilDataset.getValueSet(CPUUtilizationRecord.PAR_PROCESS_ID, String.class)) {
			boolean isDBNode = false;
			for (String dbHost : dbHosts) {
				if (processID.contains(dbHost)) {
					isDBNode = true;
					break;
				}
			}

			if (!isDBNode) {
				continue;
			}

			boolean detected = analyzeCPUUtilization(dbUtilDataset, dbHosts, processID, result);
			if (detected) {
				result.setDetected(true);
				result.addMessage("Database overhead detected on database " + processID
						+ " due to high CPU utilization on database!");
			}
		}

		return result;
	}

	private boolean analyzeCPUUtilization(Dataset dbUtilDataset, List<String> dbHosts, String processID,
			SpotterResult result) {
		boolean detected = false;
		NumericPairList<Integer, Double> chartDataUtils = new NumericPairList<>();

		ParameterSelection dbNodeelection = new ParameterSelection().select(CPUUtilizationRecord.PAR_PROCESS_ID,
				processID);
		Dataset dataset = dbNodeelection.applyTo(dbUtilDataset);
		Map<String, Integer> mapNumCores = getNumberOfCPUCores(dataset);
		for (Integer numUsers : dbUtilDataset.getValueSet(AbstractDetectionController.NUMBER_OF_USERS_KEY,
				Integer.class)) {
			ParameterSelection usersSelection = new ParameterSelection()
					.select(AbstractDetectionController.NUMBER_OF_USERS_KEY, numUsers)
					.select(CPUUtilizationRecord.PAR_PROCESS_ID, processID)
					.select(CPUUtilizationRecord.PAR_CPU_ID, CPUUtilizationRecord.RES_CPU_AGGREGATED);
			List<Double> cpuUtils = usersSelection.applyTo(dbUtilDataset).getValues(
					CPUUtilizationRecord.PAR_UTILIZATION, Double.class);

			double meanCPUUtil = LpeNumericUtils.average(cpuUtils);

			double actualThreshold = cpuThreshold;
			if (qtStrategy) {
				actualThreshold = LpeNumericUtils.getUtilizationForResponseTimeFactorQT(3, mapNumCores.get(processID)) * 0.9;
			} else {
				actualThreshold = cpuThreshold;
			}
			if (meanCPUUtil >= actualThreshold) {
				detected = true;
			}
			chartDataUtils.add(numUsers, meanCPUUtil);
		}

		AnalysisChartBuilder chartBuilder = AnalysisChartBuilder.getChartBuilder();
		chartBuilder.startChart(processID, "number of users", "utilization [%]");
		chartBuilder.addUtilizationLineSeries(chartDataUtils, "CPU utilization", true);
		chartBuilder.addHorizontalLine(cpuThreshold * 100.0, "Threshold");
		getResultManager().storeImageChartResource(chartBuilder, "DB-CPU Utilization", result);

		return detected;
	}

	private boolean analyzeDBStatistics(Dataset dbDataset, String dbId, List<Integer> sortedNumUsersList,
			SpotterResult result) {
		Collections.sort(sortedNumUsersList);
		int prevNumUsers = -1;
		int firstSignificantNumUsers = -1;
		int significantSteps = 0;
		int minNumUsers = sortedNumUsersList.get(0);
		NumericPairList<Integer, Double> rawData = new NumericPairList<>();
		NumericPairList<Integer, Double> means = new NumericPairList<>();
		List<Number> ci = new ArrayList<>();
		List<Double> waitTimesPerLock_prev = null;
		for (Integer numUsers : sortedNumUsersList) {
			Dataset tmpDataset = ParameterSelection.newSelection().select(NUMBER_OF_USERS_KEY, numUsers)
					.select(DBStatisticsRecrod.PAR_PROCESS_ID, dbId).applyTo(dbDataset);

			NumericPairList<Long, Long> numWaitsSeries = getNumWaitsTimeseries(tmpDataset);

			NumericPairList<Long, Long> waitTimeSeries = getWaitTimeTimeseries(tmpDataset);
			if (numWaitsSeries.size() != waitTimeSeries.size()) {
				throw new RuntimeException("Unequal list sizes!");
			}
			List<Double> waitTimesPerLock = new ArrayList<>();
			for (int i = 1; i < numWaitsSeries.size(); i++) {
				long numWait_prev = numWaitsSeries.get(i - 1).getValue();
				long waitTime_prev = waitTimeSeries.get(i - 1).getValue();
				long numWait = numWaitsSeries.get(i).getValue();
				long waitTime = waitTimeSeries.get(i).getValue();
				if (numWait - numWait_prev == 0L) {
					waitTimesPerLock.add(0.0);
				} else {
					waitTimesPerLock.add(((double) (waitTime - waitTime_prev) / ((double) (numWait - numWait_prev))));
				}

			}

			if (prevNumUsers > 0) {
				List<Double> sums1 = new ArrayList<>();
				List<Double> sums2 = new ArrayList<>();
				LpeNumericUtils.createNormalDistributionByBootstrapping(waitTimesPerLock_prev, waitTimesPerLock, sums1,
						sums2);

				if (sums2.size() < 2 || sums1.size() < 2) {
					throw new IllegalArgumentException("too small sets");
				}
				double prevMean = LpeNumericUtils.average(sums1);
				double currentMean = LpeNumericUtils.average(sums2);

				double pValue = LpeNumericUtils.tTest(sums2, sums1);
				if (pValue >= 0 && pValue <= requiredSignificanceLevel && prevMean < currentMean) {
					if (firstSignificantNumUsers < 0) {
						firstSignificantNumUsers = prevNumUsers;
					}
					significantSteps++;
				} else {
					firstSignificantNumUsers = -1;
					significantSteps = 0;
				}

				// update chart data
				if (prevNumUsers == minNumUsers) {
					double stdDev = LpeNumericUtils.stdDev(sums1);
					for (Double val : sums1) {
						rawData.add(prevNumUsers, val);
					}
					double ciWidth = LpeNumericUtils.getConfidenceIntervalWidth(sums1.size(), stdDev,
							requiredSignificanceLevel);
					means.add(prevNumUsers, prevMean);
					ci.add(ciWidth / 2.0);
				}

				double stdDev = LpeNumericUtils.stdDev(sums2);
				for (Double val : sums2) {
					rawData.add(numUsers, val);
				}
				double ciWidth = LpeNumericUtils.getConfidenceIntervalWidth(sums2.size(), stdDev,
						requiredSignificanceLevel);
				means.add(numUsers, currentMean);
				ci.add(ciWidth / 2.0);
			}
			waitTimesPerLock_prev = waitTimesPerLock;
			prevNumUsers = numUsers;
		}

		AnalysisChartBuilder chartBuilder = AnalysisChartBuilder.getChartBuilder();
		chartBuilder.startChart(dbId, "number of users", "avg. locking time [ms]");
		chartBuilder.addScatterSeries(rawData, "locking times");
		getResultManager().storeImageChartResource(chartBuilder, "Lock Times", result);

		chartBuilder = AnalysisChartBuilder.getChartBuilder();
		chartBuilder.startChart(dbId, "number of users", "locking time [ms]");
		chartBuilder.addScatterSeriesWithErrorBars(means, ci, "locking times");
		getResultManager().storeImageChartResource(chartBuilder, "Confidence Intervals", result);

		if (firstSignificantNumUsers > 0 && significantSteps >= requiredSignificantSteps) {
			return true;
		}
		return false;
	}

	@Override
	public InstrumentationDescription getInstrumentationDescription() {
		InstrumentationDescriptionBuilder descrBuilder = new InstrumentationDescriptionBuilder();
		descrBuilder.newSampling(CPUSampler.class.getName(), 100);
		descrBuilder.newSampling(SamplingDescription.SAMPLER_DATABASE_STATISTICS, 500);
		return descrBuilder.build();
	}

	private NumericPairList<Long, Long> getNumWaitsTimeseries(Dataset rtDataSet) {
		NumericPairList<Long, Long> timeSeries = new NumericPairList<>();
		for (DBStatisticsRecrod rec : rtDataSet.getRecords(DBStatisticsRecrod.class)) {
			timeSeries.add(rec.getTimeStamp(), rec.getNumLockWaits());
		}
		timeSeries.sort();
		return timeSeries;
	}

	private NumericPairList<Long, Long> getWaitTimeTimeseries(Dataset rtDataSet) {
		NumericPairList<Long, Long> timeSeries = new NumericPairList<>();
		for (DBStatisticsRecrod rec : rtDataSet.getRecords(DBStatisticsRecrod.class)) {
			timeSeries.add(rec.getTimeStamp(), rec.getLockTime());
		}

		timeSeries.sort();
		return timeSeries;
	}

	private Map<String, Integer> getNumberOfCPUCores(Dataset cpuUtilDataset) {
		Map<String, Integer> cpuNumCores = new HashMap<>();
		for (String processID : cpuUtilDataset.getValueSet(CPUUtilizationRecord.PAR_PROCESS_ID, String.class)) {
			ParameterSelection selection = new ParameterSelection().select(CPUUtilizationRecord.PAR_PROCESS_ID,
					processID);
			int numCores = selection.applyTo(cpuUtilDataset).getValueSet(CPUUtilizationRecord.PAR_CPU_ID).size() - 1;
			cpuNumCores.put(processID, numCores);
		}
		return cpuNumCores;
	}
}
