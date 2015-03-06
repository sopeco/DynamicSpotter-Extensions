package org.spotter.ext.detection.excessiveMessaging;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.aim.api.exceptions.InstrumentationException;
import org.aim.api.exceptions.MeasurementException;
import org.aim.api.measurement.dataset.Dataset;
import org.aim.api.measurement.dataset.DatasetCollection;
import org.aim.api.measurement.dataset.ParameterSelection;
import org.aim.artifacts.records.JmsServerRecord;
import org.aim.artifacts.records.NetworkInterfaceInfoRecord;
import org.aim.artifacts.records.NetworkRecord;
import org.aim.artifacts.sampler.NetworkIOSampler;
import org.aim.description.InstrumentationDescription;
import org.aim.description.builder.InstrumentationDescriptionBuilder;
import org.aim.description.sampling.SamplingDescription;
import org.lpe.common.extension.IExtension;
import org.lpe.common.util.LpeNumericUtils;
import org.lpe.common.util.NumericPair;
import org.lpe.common.util.NumericPairList;
import org.spotter.core.chartbuilder.AnalysisChartBuilder;
import org.spotter.core.detection.AbstractDetectionController;
import org.spotter.core.detection.IDetectionController;
import org.spotter.core.detection.IExperimentReuser;
import org.spotter.exceptions.WorkloadException;
import org.spotter.shared.result.model.SpotterResult;

public class ExcessiveMessagingDetectionController extends AbstractDetectionController implements IExperimentReuser {
	private static final int EXPERIMENT_STEPS = 5;
	private static final double TCP_PACKET_SIZE = 1500;
	private static final double SPEED_100_MBIT = 100000000;
	private static final double EPSILON_PERCENT = 0.05;

	private int requiredSignificantSteps;
	private double requiredSignificanceLevel;
	private String analysisStrategy;

	public ExcessiveMessagingDetectionController(IExtension<IDetectionController> provider) {
		super(provider);
	}

	@Override
	public void loadProperties() {
		String requiredSignificantStepsStr = getProblemDetectionConfiguration().getProperty(
				ExcessiveMessagingExtension.REQUIRED_SIGNIFICANT_STEPS_KEY);
		requiredSignificantSteps = requiredSignificantStepsStr != null ? Integer.parseInt(requiredSignificantStepsStr)
				: ExcessiveMessagingExtension.REQUIRED_SIGNIFICANT_STEPS_DEFAULT;

		String requiredConfidenceLevelStr = getProblemDetectionConfiguration().getProperty(
				ExcessiveMessagingExtension.REQUIRED_CONFIDENCE_LEVEL_KEY);
		requiredSignificanceLevel = 1.0 - (requiredConfidenceLevelStr != null ? Double
				.parseDouble(requiredConfidenceLevelStr)
				: ExcessiveMessagingExtension.REQUIRED_CONFIDENCE_LEVEL_DEFAULT);

		analysisStrategy = getProblemDetectionConfiguration().getProperty(
				ExcessiveMessagingExtension.DETECTION_STRATEGY_KEY);

	}

	@Override
	public long getExperimentSeriesDuration() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void executeExperiments() throws InstrumentationException, MeasurementException, WorkloadException {
		executeDefaultExperimentSeries(this, EXPERIMENT_STEPS, getInstrumentationDescription());
	}

	public InstrumentationDescription getInstrumentationDescription() {
		InstrumentationDescriptionBuilder descrBuilder = new InstrumentationDescriptionBuilder();
		descrBuilder.newSampling(NetworkIOSampler.class.getName(), 100).newSampling(
				SamplingDescription.SAMPLER_MESSAGING_STATISTICS, 100);
		return descrBuilder.build();
	}

	@Override
	protected SpotterResult analyze(DatasetCollection data) {
		SpotterResult result = new SpotterResult();
		result.setDetected(false);
		if (data.getDataSet(JmsServerRecord.class) == null) {
			result.addMessage("No Messaging data available!");
			return result;
		}

		boolean highMessagingOverhead = false;
		if (analysisStrategy.equals(ExcessiveMessagingExtension.THRESHOLD_STRATEGY)) {
			Map<String, NumericPair<Double, Double>> networkSpeedsAndThresholds = calculateNetworkUtilizationThreshold(data);
			if (networkSpeedsAndThresholds != null) {
				highMessagingOverhead = analyzeNetworkUtilization(data, result, networkSpeedsAndThresholds);
			}
		} else if (analysisStrategy.equals(ExcessiveMessagingExtension.STAGNATION_STRATEGY)) {
			Map<String, NumericPair<Double, Double>> networkSpeedsAndThresholds = calculateNetworkUtilizationThreshold(data);
			if (networkSpeedsAndThresholds != null) {
				highMessagingOverhead = analyzeNetworkUtilizationGrowth(data, result, networkSpeedsAndThresholds);
			}
		} else if (analysisStrategy.equals(ExcessiveMessagingExtension.MSG_THORUGHPUT_STAGNATION_STRATEGY)) {
			highMessagingOverhead = analyzeMessageThroughput(data, result);
		}

		boolean queueSizesGrow = analyzeQueueSizes(data, result);

		if (highMessagingOverhead || queueSizesGrow) {
			result.setDetected(true);
		}
		return result;
	}

	private boolean analyzeMessageThroughput(DatasetCollection data, SpotterResult result) {
		Dataset msgStatisticsDataset = data.getDataSet(JmsServerRecord.class);
		if (msgStatisticsDataset == null) {
			return false;
		}
		List<Integer> users = new ArrayList<>(msgStatisticsDataset.getValueSet(
				AbstractDetectionController.NUMBER_OF_USERS_KEY, Integer.class));
		Collections.sort(users);
		Set<String> queueNames = msgStatisticsDataset.getValueSet(JmsServerRecord.PAR_QUEUE_NAME, String.class);
		for (String queueName : queueNames) {
			int significantSteps = 0;
			int firstSignificantNumUsers = -1;
			int prevNumUsers = 0;
			double prevThroughput = -1;
			NumericPairList<Integer, Double> messageThroughputs = new NumericPairList<>();
			boolean notZero = false;
			for (Integer numUsers : users) {
				Dataset tmpDataset = ParameterSelection.newSelection()
						.select(AbstractDetectionController.NUMBER_OF_USERS_KEY, numUsers)
						.select(JmsServerRecord.PAR_QUEUE_NAME, queueName).applyTo(msgStatisticsDataset);

				List<Long> enqueueCounts = tmpDataset.getValues(JmsServerRecord.PAR_ENQUEUE_COUNT, Long.class);
				List<Long> timeStamps = tmpDataset.getValues(JmsServerRecord.PAR_TIMESTAMP, Long.class);
				long minTimestamp = LpeNumericUtils.min(timeStamps);
				long maxTimestamp = LpeNumericUtils.max(timeStamps);
				long minCount = LpeNumericUtils.min(enqueueCounts);
				long maxCount = LpeNumericUtils.max(enqueueCounts);
				if (maxCount - minCount > 0L) {
					notZero = true;
				}
				double msgThroughput = ((double) (maxCount - minCount))
						/ ((double) (maxTimestamp - minTimestamp) * 0.001);

				messageThroughputs.add(numUsers, msgThroughput);

				if (prevThroughput < 0) {
					prevThroughput = msgThroughput;
					continue;
				}

				if (msgThroughput <= prevThroughput * 1.05) {
					significantSteps++;
					if (firstSignificantNumUsers < 0) {
						firstSignificantNumUsers = prevNumUsers;
					}
				} else {
					significantSteps = 0;
					firstSignificantNumUsers = -1;
				}

				prevNumUsers = numUsers;
				prevThroughput = msgThroughput;
			}
			if (notZero) {
				AnalysisChartBuilder chartBuilder = AnalysisChartBuilder.getChartBuilder();
				chartBuilder.startChart(queueName, "number of users", "throughput");
				chartBuilder.addScatterSeriesWithLine(messageThroughputs, "message throughput");
				getResultManager().storeImageChartResource(chartBuilder, "Msg. Throughput-" + queueName, result);

				if (firstSignificantNumUsers > 1 && significantSteps >= requiredSignificantSteps) {
					result.addMessage("Message throughput stagnates at queue " + queueName + "!");
					return true;
				}
			}
		}
		return false;
	}

	private boolean analyzeQueueSizes(DatasetCollection data, SpotterResult result) {

		Dataset msgStatisticsDataset = data.getDataSet(JmsServerRecord.class);
		if (msgStatisticsDataset == null) {
			return false;
		}
		List<Integer> users = new ArrayList<>(msgStatisticsDataset.getValueSet(
				AbstractDetectionController.NUMBER_OF_USERS_KEY, Integer.class));
		Collections.sort(users);
		Set<String> queueNames = msgStatisticsDataset.getValueSet(JmsServerRecord.PAR_QUEUE_NAME, String.class);
		for (String queueName : queueNames) {
			List<Long> prevSizes = null;
			int significantSteps = 0;
			int firstSignificantNumUsers = -1;
			int prevNumUsers = 0;
			NumericPairList<Integer, Long> qSizesForChart = new NumericPairList<>();
			boolean allZero = true;
			for (Integer numUsers : users) {
				List<Long> qSizes = LpeNumericUtils.filterOutliersUsingIQR(ParameterSelection.newSelection()
						.select(AbstractDetectionController.NUMBER_OF_USERS_KEY, numUsers)
						.select(JmsServerRecord.PAR_QUEUE_NAME, queueName).applyTo(msgStatisticsDataset)
						.getValues(JmsServerRecord.PAR_QUEUE_SIZE, Long.class));
				for (Long s : qSizes) {
					if (s > 0L) {
						allZero = false;
					}
					qSizesForChart.add(numUsers, s);
				}
				if (prevSizes != null) {
					List<Double> sums1 = new ArrayList<>();
					List<Double> sums2 = new ArrayList<>();
					LpeNumericUtils.createNormalDistributionByBootstrapping(qSizes, prevSizes, sums1, sums2);
					if (sums2.size() < 2 || sums1.size() < 2) {
						throw new IllegalArgumentException("Excessive Messaging detection failed for the operation"
								+ ", because there are not enough measurement points for a t-test.");
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
				}

				prevNumUsers = numUsers;
				prevSizes = qSizes;
			}
			if (allZero) {
				continue;
			}
			AnalysisChartBuilder chartBuilder = AnalysisChartBuilder.getChartBuilder();
			chartBuilder.startChartWithoutLegend(queueName, "number of users", "queue size");
			chartBuilder.addScatterSeries(qSizesForChart, "queue size");
			getResultManager().storeImageChartResource(chartBuilder, "QueueSize-" + queueName, result);

			if (firstSignificantNumUsers > 1 && significantSteps >= requiredSignificantSteps) {
				result.addMessage("Message queue " + queueName + " grows significantly with the load!");
				return true;
			}
		}
		return false;
	}

	private Map<String, NumericPair<Double, Double>> calculateNetworkUtilizationThreshold(DatasetCollection data) {
		Map<String, NumericPair<Double, Double>> result = new HashMap<>();

		Dataset msgStatisticsDataset = data.getDataSet(JmsServerRecord.class);
		if (msgStatisticsDataset == null) {
			return null;
		}
		double avgMessageSize = 8.0 * LpeNumericUtils.average(msgStatisticsDataset.getValueSet(
				JmsServerRecord.PAR_AVG_MESSAGE_SIZE, Double.class));

		Dataset nwInfoDataset = data.getDataSet(NetworkInterfaceInfoRecord.class);
		Set<String> nodes = nwInfoDataset.getValueSet(NetworkInterfaceInfoRecord.PAR_PROCESS_ID, String.class);
		Set<String> nwInterfaces = nwInfoDataset.getValueSet(NetworkInterfaceInfoRecord.PAR_NETWORK_INTERFACE,
				String.class);

		for (String node : nodes) {
			for (String nwInterface : nwInterfaces) {
				String interfaceName = getInterfaceName(node, nwInterface);
				Dataset tmpDataset = ParameterSelection.newSelection()
						.select(NetworkInterfaceInfoRecord.PAR_PROCESS_ID, node)
						.select(NetworkInterfaceInfoRecord.PAR_NETWORK_INTERFACE, nwInterface).applyTo(nwInfoDataset);
				double tmpSpeed = LpeNumericUtils.min(tmpDataset.getValueSet(
						NetworkInterfaceInfoRecord.PAR_INTERFACE_SPEED, Long.class));
				// TODO: HACK WITH UNAVAILABLE NW_SPEED
				if (tmpSpeed < 0) {
					tmpSpeed = SPEED_100_MBIT;
				}
				double speed = tmpSpeed / 8.0;
				double packetRate = speed / TCP_PACKET_SIZE;

				// TODO: use that threshold only in cases when evg. message size
				// smaller than TCP packet!!!
				double threshold = 0.0;
				if (avgMessageSize <= TCP_PACKET_SIZE) {
					threshold = packetRate * (Math.floor(TCP_PACKET_SIZE / avgMessageSize) + 1) * 0.5 * avgMessageSize;
				} else {
					threshold = speed;
				}
				threshold = 0.9 * threshold;
				result.put(interfaceName, new NumericPair<Double, Double>(speed, threshold));
			}
		}
		return result;
	}

	private boolean analyzeNetworkUtilization(DatasetCollection data, SpotterResult result,
			Map<String, NumericPair<Double, Double>> speedThresholdPair) {
		boolean highNWUtil = false;
		Dataset nwDataset = data.getDataSet(NetworkRecord.class);
		List<Integer> users = new ArrayList<>(nwDataset.getValueSet(AbstractDetectionController.NUMBER_OF_USERS_KEY,
				Integer.class));
		Collections.sort(users);
		for (String node : nwDataset.getValueSet(NetworkRecord.PAR_PROCESS_ID, String.class)) {
			interfaceLoop: for (String nwInterface : nwDataset.getValueSet(NetworkRecord.PAR_NETWORK_INTERFACE,
					String.class)) {

				String interfaceName = getInterfaceName(node, nwInterface);
				double networkSpeed = speedThresholdPair.get(interfaceName).getKey();
				double utilizationThreshold = speedThresholdPair.get(interfaceName).getValue();
				NumericPairList<Integer, Double> utils = new NumericPairList<>();

				for (Integer numUsers : users) {
					Dataset tmpDataset = ParameterSelection.newSelection()
							.select(AbstractDetectionController.NUMBER_OF_USERS_KEY, numUsers)
							.select(NetworkRecord.PAR_PROCESS_ID, node)
							.select(NetworkRecord.PAR_NETWORK_INTERFACE, nwInterface).applyTo(nwDataset);
					if (tmpDataset == null) {
						continue interfaceLoop;
					}
					Set<Long> set = tmpDataset.getValueSet(NetworkRecord.PAR_TIMESTAMP, Long.class);
					long startSend = LpeNumericUtils.min(set);
					long endSend = LpeNumericUtils.max(set);
					set = tmpDataset.getValueSet(NetworkRecord.PAR_TRANSFERRED_BYTES, Long.class);
					long minNumSend = LpeNumericUtils.min(set);
					long maxNumSend = LpeNumericUtils.max(set);
					set = tmpDataset.getValueSet(NetworkRecord.PAR_RECEIVED_BYTES, Long.class);
					long minNumReceived = LpeNumericUtils.min(set);
					long maxNumReceived = LpeNumericUtils.max(set);

					double sent = ((double) (maxNumSend - minNumSend) * 1000.0) / (double) (endSend - startSend);
					double received = ((double) (maxNumReceived - minNumReceived) * 1000.0)
							/ (double) (endSend - startSend);

					double bandWidthUsage = Math.max(sent, received);
					double util = bandWidthUsage / networkSpeed;
					utils.add(numUsers, util);

					if (bandWidthUsage > utilizationThreshold) {
						highNWUtil = true;
						result.addMessage("Network interface " + interfaceName + " is highly utilized!");
					}

				}

				AnalysisChartBuilder chartBuilder = AnalysisChartBuilder.getChartBuilder();
				chartBuilder.startChart(interfaceName, "number of users", "utilization [%]");
				chartBuilder.addUtilizationLineSeries(utils, "network utilization", true);
				chartBuilder.addHorizontalLine((utilizationThreshold / networkSpeed) * 100.0, "threshold");
				getResultManager().storeImageChartResource(chartBuilder, "Network" + interfaceName, result);
			}

		}
		return highNWUtil;
	}

	private boolean analyzeNetworkUtilizationGrowth(DatasetCollection data, SpotterResult result,
			Map<String, NumericPair<Double, Double>> speedThresholdPair) {
		boolean stagnationDetected = false;
		Dataset nwDataset = data.getDataSet(NetworkRecord.class);
		List<Integer> users = new ArrayList<>(nwDataset.getValueSet(AbstractDetectionController.NUMBER_OF_USERS_KEY,
				Integer.class));
		Collections.sort(users);
		for (String node : nwDataset.getValueSet(NetworkRecord.PAR_PROCESS_ID, String.class)) {
			interfaceLoop: for (String nwInterface : nwDataset.getValueSet(NetworkRecord.PAR_NETWORK_INTERFACE,
					String.class)) {

				String interfaceName = getInterfaceName(node, nwInterface);
				double networkSpeed = speedThresholdPair.get(interfaceName).getKey();
				double threshold = speedThresholdPair.get(interfaceName).getValue();
				NumericPairList<Integer, Double> utils = new NumericPairList<>();

				int numSignificantSteps = 0;
				double prevUtil = -1;
				double maxUtil = 0;
				for (Integer numUsers : users) {
					Dataset tmpDataset = ParameterSelection.newSelection()
							.select(AbstractDetectionController.NUMBER_OF_USERS_KEY, numUsers)
							.select(NetworkRecord.PAR_PROCESS_ID, node)
							.select(NetworkRecord.PAR_NETWORK_INTERFACE, nwInterface).applyTo(nwDataset);
					if (tmpDataset == null) {
						continue interfaceLoop;
					}
					Set<Long> set = tmpDataset.getValueSet(NetworkRecord.PAR_TIMESTAMP, Long.class);
					long startSend = LpeNumericUtils.min(set);
					long endSend = LpeNumericUtils.max(set);
					set = tmpDataset.getValueSet(NetworkRecord.PAR_TRANSFERRED_BYTES, Long.class);
					long minNumSend = LpeNumericUtils.min(set);
					long maxNumSend = LpeNumericUtils.max(set);
					set = tmpDataset.getValueSet(NetworkRecord.PAR_RECEIVED_BYTES, Long.class);
					long minNumReceived = LpeNumericUtils.min(set);
					long maxNumReceived = LpeNumericUtils.max(set);

					double sent = ((double) (maxNumSend - minNumSend) * 1000.0) / (double) (endSend - startSend);
					double received = ((double) (maxNumReceived - minNumReceived) * 1000.0)
							/ (double) (endSend - startSend);

					double bandWidthUsage = Math.max(sent, received);
					double util = bandWidthUsage / networkSpeed;
					utils.add(numUsers, util);

					if (maxUtil < util) {
						maxUtil = util;
					}

					if (prevUtil < 0) {
						prevUtil = util;
						continue;
					}

					if (util > prevUtil * (1.0 + EPSILON_PERCENT)) {
						prevUtil = util;
						numSignificantSteps = 0;
					} else {
						numSignificantSteps++;
					}

				}
				if (numSignificantSteps >= requiredSignificantSteps && maxUtil > Math.min(0.5, threshold)) {
					stagnationDetected = true;
					result.addMessage("Network interface " + interfaceName + " has a stagnating growth");
				}

				AnalysisChartBuilder chartBuilder = AnalysisChartBuilder.getChartBuilder();
				chartBuilder.startChart(interfaceName, "#Users", "Utilization [%]");
				chartBuilder.addUtilizationLineSeries(utils, "Network Utilization", true);
				getResultManager().storeImageChartResource(chartBuilder, "Network" + interfaceName, result);
			}

		}
		return stagnationDetected;
	}

	private String getInterfaceName(String node, String nwInterface) {
		String interfaceName = node + "-" + nwInterface;
		return interfaceName;
	}

}
