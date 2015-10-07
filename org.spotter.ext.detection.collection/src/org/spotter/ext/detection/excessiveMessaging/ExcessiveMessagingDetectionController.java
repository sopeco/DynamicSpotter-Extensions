package org.spotter.ext.detection.excessiveMessaging;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.aim.aiminterface.description.instrumentation.InstrumentationDescription;
import org.aim.aiminterface.description.sampling.SamplingDescription;
import org.aim.aiminterface.exceptions.InstrumentationException;
import org.aim.aiminterface.exceptions.MeasurementException;
import org.aim.api.measurement.dataset.Dataset;
import org.aim.api.measurement.dataset.DatasetCollection;
import org.aim.api.measurement.dataset.ParameterSelection;
import org.aim.artifacts.records.JmsServerRecord;
import org.aim.artifacts.records.NetworkInterfaceInfoRecord;
import org.aim.artifacts.records.NetworkRecord;
import org.aim.artifacts.sampler.NetworkIOSampler;
import org.aim.description.builder.InstrumentationDescriptionBuilder;
import org.lpe.common.extension.IExtension;
import org.lpe.common.utils.numeric.LpeNumericUtils;
import org.lpe.common.utils.numeric.NumericPair;
import org.lpe.common.utils.numeric.NumericPairList;
import org.spotter.core.chartbuilder.AnalysisChartBuilder;
import org.spotter.core.detection.AbstractDetectionController;
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

	public ExcessiveMessagingDetectionController(final IExtension provider) {
		super(provider);
	}

	@Override
	public void loadProperties() {
		final String requiredSignificantStepsStr = getProblemDetectionConfiguration().getProperty(
				ExcessiveMessagingExtension.REQUIRED_SIGNIFICANT_STEPS_KEY);
		requiredSignificantSteps = requiredSignificantStepsStr != null ? Integer.parseInt(requiredSignificantStepsStr)
				: ExcessiveMessagingExtension.REQUIRED_SIGNIFICANT_STEPS_DEFAULT;

		final String requiredConfidenceLevelStr = getProblemDetectionConfiguration().getProperty(
				ExcessiveMessagingExtension.REQUIRED_CONFIDENCE_LEVEL_KEY);
		requiredSignificanceLevel = 1.0 - (requiredConfidenceLevelStr != null ? Double
				.parseDouble(requiredConfidenceLevelStr)
				: ExcessiveMessagingExtension.REQUIRED_CONFIDENCE_LEVEL_DEFAULT);

		analysisStrategy = getProblemDetectionConfiguration().getProperty(
				ExcessiveMessagingExtension.DETECTION_STRATEGY_KEY, ExcessiveMessagingExtension.THRESHOLD_STRATEGY);

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

	@Override
	public InstrumentationDescription getInstrumentationDescription() {
		final InstrumentationDescriptionBuilder descrBuilder = new InstrumentationDescriptionBuilder();
		descrBuilder.newSampling(NetworkIOSampler.class.getName(), 100).newSampling(
				SamplingDescription.SAMPLER_MESSAGING_STATISTICS, 100);
		return descrBuilder.build();
	}

	@Override
	protected SpotterResult analyze(final DatasetCollection data) {
		final SpotterResult result = new SpotterResult();
		result.setDetected(false);
		if (data.getDataSet(JmsServerRecord.class) == null) {
			result.addMessage("No Messaging data available!");
			return result;
		}

		boolean highMessagingOverhead = false;
		if (analysisStrategy.equals(ExcessiveMessagingExtension.THRESHOLD_STRATEGY)) {
			final Map<String, NumericPair<Double, Double>> networkSpeedsAndThresholds = calculateNetworkUtilizationThreshold(data);
			if (networkSpeedsAndThresholds != null) {
				highMessagingOverhead = analyzeNetworkUtilization(data, result, networkSpeedsAndThresholds);
			}
		} else if (analysisStrategy.equals(ExcessiveMessagingExtension.STAGNATION_STRATEGY)) {
			final Map<String, NumericPair<Double, Double>> networkSpeedsAndThresholds = calculateNetworkUtilizationThreshold(data);
			if (networkSpeedsAndThresholds != null) {
				highMessagingOverhead = analyzeNetworkUtilizationGrowth(data, result, networkSpeedsAndThresholds);
			}
		} else if (analysisStrategy.equals(ExcessiveMessagingExtension.MSG_THORUGHPUT_STAGNATION_STRATEGY)) {
			highMessagingOverhead = analyzeMessageThroughput(data, result);
		}

		final boolean queueSizesGrow = analyzeQueueSizes(data, result);

		if (highMessagingOverhead || queueSizesGrow) {
			result.setDetected(true);
		}
		return result;
	}

	private boolean analyzeMessageThroughput(final DatasetCollection data, final SpotterResult result) {
		final Dataset msgStatisticsDataset = data.getDataSet(JmsServerRecord.class);
		if (msgStatisticsDataset == null) {
			return false;
		}
		final List<Integer> users = new ArrayList<>(msgStatisticsDataset.getValueSet(
				AbstractDetectionController.NUMBER_OF_USERS_KEY, Integer.class));
		Collections.sort(users);
		final Set<String> queueNames = msgStatisticsDataset.getValueSet(JmsServerRecord.PAR_QUEUE_NAME, String.class);
		for (final String queueName : queueNames) {
			int significantSteps = 0;
			int firstSignificantNumUsers = -1;
			int prevNumUsers = 0;
			double prevThroughput = -1;
			final NumericPairList<Integer, Double> messageThroughputs = new NumericPairList<>();
			boolean notZero = false;
			for (final Integer numUsers : users) {
				final Dataset tmpDataset = ParameterSelection.newSelection()
						.select(AbstractDetectionController.NUMBER_OF_USERS_KEY, numUsers)
						.select(JmsServerRecord.PAR_QUEUE_NAME, queueName).applyTo(msgStatisticsDataset);

				final List<Long> enqueueCounts = tmpDataset.getValues(JmsServerRecord.PAR_ENQUEUE_COUNT, Long.class);
				final List<Long> timeStamps = tmpDataset.getValues(JmsServerRecord.PAR_TIMESTAMP, Long.class);
				final long minTimestamp = LpeNumericUtils.min(timeStamps);
				final long maxTimestamp = LpeNumericUtils.max(timeStamps);
				final long minCount = LpeNumericUtils.min(enqueueCounts);
				final long maxCount = LpeNumericUtils.max(enqueueCounts);
				if (maxCount - minCount > 0L) {
					notZero = true;
				}
				final double msgThroughput = (maxCount - minCount)
						/ ((maxTimestamp - minTimestamp) * 0.001);

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
				final AnalysisChartBuilder chartBuilder = AnalysisChartBuilder.getChartBuilder();
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

	private boolean analyzeQueueSizes(final DatasetCollection data, final SpotterResult result) {

		final Dataset msgStatisticsDataset = data.getDataSet(JmsServerRecord.class);
		if (msgStatisticsDataset == null) {
			return false;
		}
		final List<Integer> users = new ArrayList<>(msgStatisticsDataset.getValueSet(
				AbstractDetectionController.NUMBER_OF_USERS_KEY, Integer.class));
		Collections.sort(users);
		final Set<String> queueNames = msgStatisticsDataset.getValueSet(JmsServerRecord.PAR_QUEUE_NAME, String.class);
		for (final String queueName : queueNames) {
			List<Long> prevSizes = null;
			int significantSteps = 0;
			int firstSignificantNumUsers = -1;
			int prevNumUsers = 0;
			final NumericPairList<Integer, Long> qSizesForChart = new NumericPairList<>();
			boolean allZero = true;
			for (final Integer numUsers : users) {
				final List<Long> qSizes = LpeNumericUtils.filterOutliersUsingIQR(ParameterSelection.newSelection()
						.select(AbstractDetectionController.NUMBER_OF_USERS_KEY, numUsers)
						.select(JmsServerRecord.PAR_QUEUE_NAME, queueName).applyTo(msgStatisticsDataset)
						.getValues(JmsServerRecord.PAR_QUEUE_SIZE, Long.class));
				for (final Long s : qSizes) {
					if (s > 0L) {
						allZero = false;
					}
					qSizesForChart.add(numUsers, s);
				}
				if (prevSizes != null) {
					final List<Double> sums1 = new ArrayList<>();
					final List<Double> sums2 = new ArrayList<>();
					LpeNumericUtils.createNormalDistributionByBootstrapping(qSizes, prevSizes, sums1, sums2);
					if (sums2.size() < 2 || sums1.size() < 2) {
						throw new IllegalArgumentException("Excessive Messaging detection failed for the operation"
								+ ", because there are not enough measurement points for a t-test.");
					}
					final double prevMean = LpeNumericUtils.average(sums1);
					final double currentMean = LpeNumericUtils.average(sums2);

					final double pValue = LpeNumericUtils.tTest(sums2, sums1);
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
			final AnalysisChartBuilder chartBuilder = AnalysisChartBuilder.getChartBuilder();
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

	private Map<String, NumericPair<Double, Double>> calculateNetworkUtilizationThreshold(final DatasetCollection data) {
		final Map<String, NumericPair<Double, Double>> result = new HashMap<>();

		final Dataset msgStatisticsDataset = data.getDataSet(JmsServerRecord.class);
		if (msgStatisticsDataset == null) {
			return null;
		}
		final double avgMessageSize = 8.0 * LpeNumericUtils.average(msgStatisticsDataset.getValueSet(
				JmsServerRecord.PAR_AVG_MESSAGE_SIZE, Double.class));

		final Dataset nwInfoDataset = data.getDataSet(NetworkInterfaceInfoRecord.class);
		final Set<String> nodes = nwInfoDataset.getValueSet(NetworkInterfaceInfoRecord.PAR_PROCESS_ID, String.class);
		final Set<String> nwInterfaces = nwInfoDataset.getValueSet(NetworkInterfaceInfoRecord.PAR_NETWORK_INTERFACE,
				String.class);

		for (final String node : nodes) {
			for (final String nwInterface : nwInterfaces) {
				final String interfaceName = getInterfaceName(node, nwInterface);
				final Dataset tmpDataset = ParameterSelection.newSelection()
						.select(NetworkInterfaceInfoRecord.PAR_PROCESS_ID, node)
						.select(NetworkInterfaceInfoRecord.PAR_NETWORK_INTERFACE, nwInterface).applyTo(nwInfoDataset);
				double tmpSpeed = LpeNumericUtils.min(tmpDataset.getValueSet(
						NetworkInterfaceInfoRecord.PAR_INTERFACE_SPEED, Long.class));
				// TODO: HACK WITH UNAVAILABLE NW_SPEED
				if (tmpSpeed < 0) {
					tmpSpeed = SPEED_100_MBIT;
				}
				final double speed = tmpSpeed / 8.0;
				final double packetRate = speed / TCP_PACKET_SIZE;

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

	private boolean analyzeNetworkUtilization(final DatasetCollection data, final SpotterResult result,
			final Map<String, NumericPair<Double, Double>> speedThresholdPair) {
		boolean highNWUtil = false;
		final Dataset nwDataset = data.getDataSet(NetworkRecord.class);
		final List<Integer> users = new ArrayList<>(nwDataset.getValueSet(AbstractDetectionController.NUMBER_OF_USERS_KEY,
				Integer.class));
		Collections.sort(users);
		for (final String node : nwDataset.getValueSet(NetworkRecord.PAR_PROCESS_ID, String.class)) {
			interfaceLoop: for (final String nwInterface : nwDataset.getValueSet(NetworkRecord.PAR_NETWORK_INTERFACE,
					String.class)) {

				final String interfaceName = getInterfaceName(node, nwInterface);
				final double networkSpeed = speedThresholdPair.get(interfaceName).getKey();
				final double utilizationThreshold = speedThresholdPair.get(interfaceName).getValue();
				final NumericPairList<Integer, Double> utils = new NumericPairList<>();

				for (final Integer numUsers : users) {
					final Dataset tmpDataset = ParameterSelection.newSelection()
							.select(AbstractDetectionController.NUMBER_OF_USERS_KEY, numUsers)
							.select(NetworkRecord.PAR_PROCESS_ID, node)
							.select(NetworkRecord.PAR_NETWORK_INTERFACE, nwInterface).applyTo(nwDataset);
					if (tmpDataset == null) {
						continue interfaceLoop;
					}
					Set<Long> set = tmpDataset.getValueSet(NetworkRecord.PAR_TIMESTAMP, Long.class);
					final long startSend = LpeNumericUtils.min(set);
					final long endSend = LpeNumericUtils.max(set);
					set = tmpDataset.getValueSet(NetworkRecord.PAR_TRANSFERRED_BYTES, Long.class);
					final long minNumSend = LpeNumericUtils.min(set);
					final long maxNumSend = LpeNumericUtils.max(set);
					set = tmpDataset.getValueSet(NetworkRecord.PAR_RECEIVED_BYTES, Long.class);
					final long minNumReceived = LpeNumericUtils.min(set);
					final long maxNumReceived = LpeNumericUtils.max(set);

					final double sent = ((maxNumSend - minNumSend) * 1000.0) / (endSend - startSend);
					final double received = ((maxNumReceived - minNumReceived) * 1000.0)
							/ (endSend - startSend);

					final double bandWidthUsage = Math.max(sent, received);
					final double util = bandWidthUsage / networkSpeed;
					utils.add(numUsers, util);

					if (bandWidthUsage > utilizationThreshold) {
						highNWUtil = true;
						result.addMessage("Network interface " + interfaceName + " is highly utilized!");
					}

				}

				final AnalysisChartBuilder chartBuilder = AnalysisChartBuilder.getChartBuilder();
				chartBuilder.startChart(interfaceName, "number of users", "utilization [%]");
				chartBuilder.addUtilizationLineSeries(utils, "network utilization", true);
				chartBuilder.addHorizontalLine((utilizationThreshold / networkSpeed) * 100.0, "threshold");
				getResultManager().storeImageChartResource(chartBuilder, "Network" + interfaceName, result);
			}

		}
		return highNWUtil;
	}

	private boolean analyzeNetworkUtilizationGrowth(final DatasetCollection data, final SpotterResult result,
			final Map<String, NumericPair<Double, Double>> speedThresholdPair) {
		boolean stagnationDetected = false;
		final Dataset nwDataset = data.getDataSet(NetworkRecord.class);
		final List<Integer> users = new ArrayList<>(nwDataset.getValueSet(AbstractDetectionController.NUMBER_OF_USERS_KEY,
				Integer.class));
		Collections.sort(users);
		for (final String node : nwDataset.getValueSet(NetworkRecord.PAR_PROCESS_ID, String.class)) {
			interfaceLoop: for (final String nwInterface : nwDataset.getValueSet(NetworkRecord.PAR_NETWORK_INTERFACE,
					String.class)) {

				final String interfaceName = getInterfaceName(node, nwInterface);
				final double networkSpeed = speedThresholdPair.get(interfaceName).getKey();
				final double threshold = speedThresholdPair.get(interfaceName).getValue();
				final NumericPairList<Integer, Double> utils = new NumericPairList<>();

				int numSignificantSteps = 0;
				double prevUtil = -1;
				double maxUtil = 0;
				for (final Integer numUsers : users) {
					final Dataset tmpDataset = ParameterSelection.newSelection()
							.select(AbstractDetectionController.NUMBER_OF_USERS_KEY, numUsers)
							.select(NetworkRecord.PAR_PROCESS_ID, node)
							.select(NetworkRecord.PAR_NETWORK_INTERFACE, nwInterface).applyTo(nwDataset);
					if (tmpDataset == null) {
						continue interfaceLoop;
					}
					Set<Long> set = tmpDataset.getValueSet(NetworkRecord.PAR_TIMESTAMP, Long.class);
					final long startSend = LpeNumericUtils.min(set);
					final long endSend = LpeNumericUtils.max(set);
					set = tmpDataset.getValueSet(NetworkRecord.PAR_TRANSFERRED_BYTES, Long.class);
					final long minNumSend = LpeNumericUtils.min(set);
					final long maxNumSend = LpeNumericUtils.max(set);
					set = tmpDataset.getValueSet(NetworkRecord.PAR_RECEIVED_BYTES, Long.class);
					final long minNumReceived = LpeNumericUtils.min(set);
					final long maxNumReceived = LpeNumericUtils.max(set);

					final double sent = ((maxNumSend - minNumSend) * 1000.0) / (endSend - startSend);
					final double received = ((maxNumReceived - minNumReceived) * 1000.0)
							/ (endSend - startSend);

					final double bandWidthUsage = Math.max(sent, received);
					final double util = bandWidthUsage / networkSpeed;
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

				final AnalysisChartBuilder chartBuilder = AnalysisChartBuilder.getChartBuilder();
				chartBuilder.startChart(interfaceName, "#Users", "Utilization [%]");
				chartBuilder.addUtilizationLineSeries(utils, "Network Utilization", true);
				getResultManager().storeImageChartResource(chartBuilder, "Network" + interfaceName, result);
			}

		}
		return stagnationDetected;
	}

	private String getInterfaceName(final String node, final String nwInterface) {
		final String interfaceName = node + "-" + nwInterface;
		return interfaceName;
	}

}
