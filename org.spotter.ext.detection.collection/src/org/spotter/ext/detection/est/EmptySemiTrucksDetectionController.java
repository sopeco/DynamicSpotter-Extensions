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
package org.spotter.ext.detection.est;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.aim.aiminterface.description.instrumentation.InstrumentationDescription;
import org.aim.aiminterface.entities.measurements.AbstractRecord;
import org.aim.aiminterface.exceptions.InstrumentationException;
import org.aim.aiminterface.exceptions.MeasurementException;
import org.aim.api.measurement.dataset.Dataset;
import org.aim.api.measurement.dataset.DatasetCollection;
import org.aim.api.measurement.dataset.ParameterSelection;
import org.aim.api.measurement.utils.MeasurementDataUtils;
import org.aim.artifacts.probes.JmsCommunicationProbe;
import org.aim.artifacts.probes.JmsMessageSizeProbe;
import org.aim.artifacts.probes.ThreadTracingProbe;
import org.aim.artifacts.records.JmsMessageSizeRecord;
import org.aim.artifacts.records.JmsRecord;
import org.aim.artifacts.records.ThreadTracingRecord;
import org.aim.artifacts.scopes.EntryPointScope;
import org.aim.artifacts.scopes.JmsScope;
import org.aim.description.builder.InstrumentationDescriptionBuilder;
import org.lpe.common.extension.IExtension;
import org.lpe.common.util.system.LpeSystemUtils;
import org.spotter.core.ProgressManager;
import org.spotter.core.detection.AbstractDetectionController;
import org.spotter.exceptions.WorkloadException;
import org.spotter.shared.result.model.SpotterResult;

/**
 * Detection controller for Empty Semi Trucks.
 * 
 * @author Alexander Wert
 * 
 */
public class EmptySemiTrucksDetectionController extends AbstractDetectionController {
	private static final double HUNDRED_PERCENT = 100.0;
	// private static final long NANO_TO_MILLI = 1000000L;
	private static final int NUM_EXPERIMENTS = 1;

	/**
	 * Constructor.
	 * 
	 * @param provider
	 *            extension provider
	 */
	public EmptySemiTrucksDetectionController(final IExtension provider) {
		super(provider);
		// TODO Auto-generated constructor stub
	}

	@Override
	public void loadProperties() {
		// TODO Auto-generated method stub

	}

	@Override
	public void executeExperiments() throws InstrumentationException, MeasurementException, WorkloadException {
		instrumentApplication(getInstrumentationDescription());
		getMeasurementController().prepareMonitoring(getInstrumentationDescription());
		runExperiment(this, 1);
		getMeasurementController().resetMonitoring();
		uninstrumentApplication();
	}

	private InstrumentationDescription getInstrumentationDescription() {
		final InstrumentationDescriptionBuilder idBuilder = new InstrumentationDescriptionBuilder();
		return idBuilder.newAPIScopeEntity(EntryPointScope.class.getName()).enableTrace()
				.addProbe(ThreadTracingProbe.MODEL_PROBE).entityDone().newAPIScopeEntity(JmsScope.class.getName())
				.addProbe(JmsMessageSizeProbe.MODEL_PROBE).addProbe(JmsCommunicationProbe.MODEL_PROBE).entityDone()
				.build();
	}

	@Override
	protected SpotterResult analyze(final DatasetCollection data) {
		final SpotterResult result = new SpotterResult();
		result.setDetected(false);

		final Dataset threadTracingDataset = data.getDataSet(ThreadTracingRecord.class);
		final Dataset messagingDataset = data.getDataSet(JmsRecord.class);
		final Dataset messageSizesDataset = data.getDataSet(JmsMessageSizeRecord.class);

		if (threadTracingDataset == null || threadTracingDataset.size() == 0) {
			result.addMessage("No trace records found!");
			result.setDetected(false);
			return result;
		}

		if (messageSizesDataset == null || messageSizesDataset.size() == 0) {
			result.addMessage("No message size records found!");
			result.setDetected(false);
			return result;
		}

		if (messagingDataset == null || messagingDataset.size() == 0) {
			result.addMessage("No messaging records found!");
			result.setDetected(false);
			return result;
		}

		for (final String processId : messagingDataset.getValueSet(AbstractRecord.PAR_PROCESS_ID, String.class)) {
			final Dataset processRelatedTraceDataset = ParameterSelection.newSelection()
					.select(AbstractRecord.PAR_PROCESS_ID, processId).applyTo(threadTracingDataset);

			final Dataset processRelatedMessagingDataset = ParameterSelection.newSelection()
					.select(AbstractRecord.PAR_PROCESS_ID, processId).applyTo(messagingDataset);

			if (processRelatedTraceDataset == null || processRelatedTraceDataset.size() == 0) {
				continue;
			}

			if (processRelatedMessagingDataset == null || processRelatedMessagingDataset.size() == 0) {
				result.addMessage("No messaging records found for processId " + processId + "!");
				continue;
			}

			final List<Trace> traces = extractTraces(processRelatedTraceDataset, processRelatedMessagingDataset,
					messageSizesDataset);

			// writeTracesToFile(result, traces, "traces");

			final List<AggTrace> aggregatedTraces = aggregateTraces(traces);

			writeTracesToFile(result, aggregatedTraces, "traces-agg-" + processId.substring(processId.indexOf("@") + 1));
			final List<ESTCandidate> estCandidates = new ArrayList<>();
			for (final AggTrace aggTrace : aggregatedTraces) {
				findESTCandidates(estCandidates, aggTrace, 1);
			}

			for (final ESTCandidate candidate : estCandidates) {
				result.setDetected(true);
				final double savingPotential = candidate.getAggTrace().getOverhead() * (candidate.getLoopCount() - 1);
				final double transmittedBytes = (candidate.getAggTrace().getPayload() + candidate.getAggTrace().getOverhead())
						* candidate.getLoopCount();
				result.addMessage("*************************************************************");
				result.addMessage("*************************************************************");
				result.addMessage("** Empty Semi Trucks Candidate **");
				result.addMessage("Avg Payload: " + candidate.getAggTrace().getPayload() + " Bytes");
				result.addMessage("Avg Messaging Overhead: " + candidate.getAggTrace().getOverhead() + " Bytes");
				result.addMessage("Loop count: " + candidate.getLoopCount());
				result.addMessage("Saving potential: " + savingPotential + " Bytes");
				result.addMessage("Saving potential %: " + (HUNDRED_PERCENT * savingPotential / transmittedBytes)
						+ " %");

				result.addMessage("TRACE: ");
				result.addMessage(candidate.getAggTrace().getPathToParentString());
				result.addMessage("*************************************************************");
				result.addMessage("*************************************************************");
			}

		}

		// search for loop

		return result;

	}

	private void findESTCandidates(final List<ESTCandidate> candidates, final AggTrace aggTrace, int loopCount) {
		if (aggTrace.isSendMethod() && loopCount > 1) {
			final ESTCandidate candidate = new ESTCandidate();
			candidate.setAggTrace(aggTrace);
			candidate.setLoopCount(loopCount);
			candidates.add(candidate);
		} else {
			if (aggTrace.isLoop()) {
				loopCount *= aggTrace.getLoopCount();
			}

			for (final AggTrace subTrace : aggTrace.getSubTraces()) {
				findESTCandidates(candidates, subTrace, loopCount);
			}

		}
	}

	private List<AggTrace> aggregateTraces(final List<Trace> traces) {
		final Map<Trace, List<Trace>> traceGrouping = new HashMap<Trace, List<Trace>>();
		for (final Trace rootTrace : traces) {
			List<Trace> groupTraces = null;
			if (!traceGrouping.containsKey(rootTrace)) {
				groupTraces = new ArrayList<>();
				traceGrouping.put(rootTrace, groupTraces);
			} else {
				groupTraces = traceGrouping.get(rootTrace);
			}

			groupTraces.add(rootTrace);

		}
		final List<AggTrace> aggregatedTraces = new ArrayList<>();

		for (final Trace representative : traceGrouping.keySet()) {

			calculateAverageTrace(representative, traceGrouping.get(representative));

			aggregatedTraces.add(AggTrace.fromTrace(representative));
		}

		return aggregatedTraces;

	}

	private void calculateAverageTrace(Trace reprTrace, final List<Trace> tracesList) {
		final List<Iterator<Trace>> iterators = new ArrayList<>();
		for (final Trace tr : tracesList) {
			iterators.add(tr.iterator());
		}

		final Iterator<Trace> itMaster = reprTrace.iterator();
		final long size = tracesList.size();
		while (itMaster.hasNext()) {
			reprTrace = itMaster.next();
			long avgPayload = 0;
			long avgOverhead = 0;
			for (final Iterator<Trace> it : iterators) {
				final Trace subTrace = it.next();
				avgPayload += subTrace.getPayload();
				avgOverhead += subTrace.getOverhead();
			}
			reprTrace.setPayload(avgPayload / size);
			reprTrace.setOverhead(avgOverhead / size);

		}
	}

	private void writeTracesToFile(final SpotterResult result, final List<?> traces, final String fileName) {

		try {
			final PipedOutputStream outStream = new PipedOutputStream();
			final PipedInputStream inStream = new PipedInputStream(outStream);

			LpeSystemUtils.submitTask(new Runnable() {

				@Override
				public void run() {
					final BufferedWriter bWriter = new BufferedWriter(new OutputStreamWriter(outStream));
					try {
						for (final Object trace : traces) {
							bWriter.write(trace.toString());
							bWriter.newLine();
							bWriter.newLine();
						}
					} catch (final IOException e) {

					} finally {
						if (bWriter != null) {
							try {
								bWriter.close();
							} catch (final IOException e) {
								throw new RuntimeException(e);
							}
						}

					}
				}

			});
			getResultManager().storeTextResource(fileName, result, inStream);
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	private List<Trace> extractTraces(final Dataset threadTracingDataset, final Dataset messagingDataset,
			final Dataset messageSizesDataset) {
		final List<Trace> traces = new ArrayList<>();
		for (final Long threadId : threadTracingDataset.getValueSet(ThreadTracingRecord.PAR_THREAD_ID, Long.class)) {
			final List<ThreadTracingRecord> threadRecords = ParameterSelection.newSelection()
					.select(ThreadTracingRecord.PAR_THREAD_ID, threadId).applyTo(threadTracingDataset)
					.getRecords(ThreadTracingRecord.class);
			MeasurementDataUtils.sortRecordsAscending(threadRecords, ThreadTracingRecord.PAR_CALL_ID);

			Trace trace = null;
			final long nextValidTimestamp = Long.MIN_VALUE;
			Trace previousTraceRoot = null;
			ThreadTracingRecord sendMethodRecord = null;
			for (final ThreadTracingRecord ttRecord : threadRecords) {
				if (sendMethodRecord == null || sendMethodRecord.getExitNanoTime() <= ttRecord.getEnterNanoTime()) {
					sendMethodRecord = null;

					// long durationMs = (ttRecord.getExitNanoTime() -
					// ttRecord.getEnterNanoTime()) / NANO_TO_MILLI;
					if (ttRecord.getTimeStamp() < nextValidTimestamp) {
						continue;
					}
					final String operation = ttRecord.getOperation();
					final long callId = ttRecord.getCallId();
					final String processId = ttRecord.getProcessId();

					if (trace == null) {
						trace = new Trace(operation);
						previousTraceRoot = trace;
					} else if (trace.getExitTime() >= ttRecord.getExitNanoTime()
							&& trace.getExitTime() >= ttRecord.getEnterNanoTime()) {
						// sub-method
						trace = new Trace(trace, operation);
					} else {

						while (trace != null && trace.getExitTime() <= ttRecord.getEnterNanoTime()) {
							trace = trace.getParent();
						}
						final Trace parent = trace;
						trace = new Trace(parent, operation);
						if (parent == null) {
							if (previousTraceRoot != null) {
								traces.add(previousTraceRoot);
							}
							previousTraceRoot = trace;
						}

					}
					if (operation.endsWith("send(javax.jms.Message)")) {
						sendMethodRecord = ttRecord;
					}
					setPayloadSizes(trace, operation, processId, callId, messagingDataset, messageSizesDataset);
					trace.setStartTime(ttRecord.getEnterNanoTime());
					trace.setExitTime(ttRecord.getExitNanoTime());
				}
			}
			if (previousTraceRoot != null) {
				traces.add(previousTraceRoot);
			}

		}

		return traces;
	}

	private void setPayloadSizes(final Trace trace, final String operation, final String processId, final long callId,
			final Dataset messagingDataset, final Dataset messageSizesDataset) {

		if (operation.endsWith("send(javax.jms.Message)")) {
			final JmsMessageSizeRecord mSizeRecord = getMessageSizeRecord(processId, callId +1 , messagingDataset,
					messageSizesDataset);
			if (mSizeRecord != null) {
				trace.setSendMethod(true);
				trace.setPayload(mSizeRecord.getBodySize());
				trace.setOverhead(mSizeRecord.getSize() - mSizeRecord.getBodySize());
			}

		}

	}

	private JmsMessageSizeRecord getMessageSizeRecord(final String processId, final long callId, final Dataset messagingDataset,
			final Dataset messageSizesDataset) {
		final ParameterSelection messageCorrelationSelection = ParameterSelection.newSelection().select(
				AbstractRecord.PAR_CALL_ID, callId);
		final Dataset messageCorrelationDataset = messageCorrelationSelection.applyTo(messagingDataset);

		if (messageCorrelationDataset == null || messageCorrelationDataset.size() == 0) {
			return null;
		}
		final String correlationId = messageCorrelationDataset.getValues(JmsRecord.PAR_MSG_CORRELATION_HASH, String.class)
				.get(0);

		final ParameterSelection messageSizeSelection = ParameterSelection.newSelection().select(
				JmsMessageSizeRecord.PAR_MSG_CORRELATION_HASH, correlationId);
		final Dataset mSizeDataset = messageSizeSelection.applyTo(messageSizesDataset);

		if (mSizeDataset == null || mSizeDataset.size() == 0) {
			return null;
		}

		return mSizeDataset.getRecords(JmsMessageSizeRecord.class).get(0);

	}

	@Override
	public long getExperimentSeriesDuration() {
		return ProgressManager.getInstance().calculateDefaultExperimentSeriesDuration(NUM_EXPERIMENTS);
	}

}
