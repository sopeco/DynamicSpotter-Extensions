package org.spotter.ext.detection.stifle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.aim.api.exceptions.InstrumentationException;
import org.aim.api.exceptions.MeasurementException;
import org.aim.api.measurement.dataset.Dataset;
import org.aim.api.measurement.dataset.DatasetCollection;
import org.aim.api.measurement.utils.MeasurementDataUtils;
import org.aim.artifacts.probes.ResponsetimeProbe;
import org.aim.artifacts.probes.SQLQueryProbe;
import org.aim.artifacts.probes.ThreadTracingProbe;
import org.aim.artifacts.records.ResponseTimeRecord;
import org.aim.artifacts.records.SQLQueryRecord;
import org.aim.artifacts.records.ThreadTracingRecord;
import org.aim.artifacts.scopes.EntryPointScope;
import org.aim.artifacts.scopes.JDBCScope;
import org.aim.description.InstrumentationDescription;
import org.aim.description.builder.InstrumentationDescriptionBuilder;
import org.lpe.common.extension.IExtension;
import org.lpe.common.util.LpeNumericUtils;
import org.lpe.common.util.LpeStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spotter.core.ProgressManager;
import org.spotter.core.detection.AbstractDetectionController;
import org.spotter.core.detection.IDetectionController;
import org.spotter.exceptions.WorkloadException;
import org.spotter.shared.result.model.SpotterResult;

public class StifleDetectionController extends AbstractDetectionController {
	private static final Logger LOGGER = LoggerFactory.getLogger(StifleDetectionController.class);

	public StifleDetectionController(IExtension<IDetectionController> provider) {
		super(provider);
		// TODO Auto-generated constructor stub
	}

	@Override
	public void loadProperties() {
		// TODO Auto-generated method stub

	}

	@Override
	public long getExperimentSeriesDuration() {

		return ProgressManager.getInstance().calculateDefaultExperimentSeriesDuration(1);
	}

	@Override
	public void executeExperiments() throws InstrumentationException, MeasurementException, WorkloadException {
		instrumentApplication(getInstrumentationDescription());
		getMeasurementController().prepareMonitoring(getInstrumentationDescription());
		runExperiment(this, 1);
		getMeasurementController().resetMonitoring();
		uninstrumentApplication();

	}

	@Override
	protected SpotterResult analyze(DatasetCollection data) {

		LOGGER.info("Fetching datasets.");

		Dataset sqlDataset = data.getDataSet(SQLQueryRecord.class);
		Dataset rtDataset = data.getDataSet(ResponseTimeRecord.class);
		Dataset tracingDataset = data.getDataSet(ThreadTracingRecord.class);

		LOGGER.info("Converting SQL dataset.");

		List<SQLQueryRecord> sqlRecords = sqlDataset.getRecords(SQLQueryRecord.class);
		List<ThreadTracingRecord> ttRecords = tracingDataset.getRecords(ThreadTracingRecord.class);

		sqlRecords = filterQueryRecords(sqlRecords, ttRecords);

		LOGGER.info("Converting RT dataset.");

		List<ResponseTimeRecord> rtRecords = rtDataset.getRecords(ResponseTimeRecord.class);
		rtRecords = filterResponsetimeRecords(rtRecords);

		LOGGER.info("Analyzing datasets.");

		Map<String, List<StifleQuery>> stifleQueries = analyzeDatasets(rtRecords, sqlRecords);

		LOGGER.info("Creating results.");

		SpotterResult result = new SpotterResult();
		result.setDetected(false);

		if (!stifleQueries.isEmpty()) {
			result.setDetected(true);
			StringBuilder strBuilder = new StringBuilder();
			for (String operation : stifleQueries.keySet()) {
				strBuilder.append("Stifles in operation " + operation + " found:");
				strBuilder.append("\n");
				List<StifleQuery> stifles = stifleQueries.get(operation);
				for (StifleQuery stifle : stifles) {
					strBuilder.append("Query: " + stifle.getQuery());
					strBuilder.append("\n");
					strBuilder.append("occured: ( " + LpeNumericUtils.min(stifle.getOccurrences()) + " , "
							+ LpeNumericUtils.average(stifle.getOccurrences()) + " , "
							+ LpeNumericUtils.max(stifle.getOccurrences()) + " ) times");
					strBuilder.append("\n");
				}
				strBuilder.append("\n");
				strBuilder.append("\n");
			}
			result.addMessage(strBuilder.toString());
		}

		return result;
	}

	private List<SQLQueryRecord> filterQueryRecords(List<SQLQueryRecord> sqlRecords, List<ThreadTracingRecord> ttRecords) {
		MeasurementDataUtils.sortRecordsAscending(sqlRecords, SQLQueryRecord.PAR_CALL_ID);
		MeasurementDataUtils.sortRecordsAscending(ttRecords, ThreadTracingRecord.PAR_CALL_ID);
		List<SQLQueryRecord> sqlRecordsTmp = new ArrayList<>();
		List<ThreadTracingRecord> ttRecordsTmp = new ArrayList<>();
		int firstSQLIndex = 0;
		int firstTTIndex = 0;
		int lastSQLIndex = sqlRecords.size() - 1;
		int lastTTIndex = ttRecords.size() - 1;
		while (sqlRecords.get(firstSQLIndex).getCallId() < ttRecords.get(firstTTIndex).getCallId()) {
			firstSQLIndex++;
		}

		while (sqlRecords.get(firstSQLIndex).getCallId() > ttRecords.get(firstTTIndex).getCallId()) {
			firstTTIndex++;
		}

		while (sqlRecords.get(lastSQLIndex).getCallId() < ttRecords.get(lastTTIndex).getCallId()) {
			lastTTIndex--;
		}

		while (sqlRecords.get(lastSQLIndex).getCallId() > ttRecords.get(lastTTIndex).getCallId()) {
			lastSQLIndex--;
		}

		for (int i = firstSQLIndex; i <= lastSQLIndex; i++) {
			sqlRecordsTmp.add(sqlRecords.get(i));
		}

		for (int i = firstTTIndex; i <= lastTTIndex; i++) {
			ttRecordsTmp.add(ttRecords.get(i));
		}

		if (sqlRecordsTmp.size() != ttRecordsTmp.size()) {
			throw new RuntimeException("Unequal amount of records!");
		}
		List<SQLQueryRecord> uniqueSQLRecords = new ArrayList<>();
		int index = 0;
		long currentStart = -1;
		long refEnd = -1;
		while (index < sqlRecordsTmp.size()) {
			if (sqlRecordsTmp.get(index).getCallId() != ttRecordsTmp.get(index).getCallId()) {
				throw new RuntimeException("CallIDs do not match!");
			}
			currentStart = ttRecordsTmp.get(index).getEnterNanoTime();
			if (refEnd < 0 || currentStart > refEnd) {
				refEnd = ttRecordsTmp.get(index).getExitNanoTime();
				uniqueSQLRecords.add(sqlRecordsTmp.get(index));
			}

			index++;
		}
		return uniqueSQLRecords;
	}

	private List<ResponseTimeRecord> filterResponsetimeRecords(List<ResponseTimeRecord> rtRecords) {
		MeasurementDataUtils.sortRecordsAscending(rtRecords, ResponseTimeRecord.PAR_CALL_ID);
		List<ResponseTimeRecord> uniqueServletRecords = new ArrayList<>();
		int index = 0;
		long currentStart = -1;
		long refEnd = -1;
		while (index < rtRecords.size()) {
			currentStart = rtRecords.get(index).getTimeStamp();
			if (refEnd < 0 || currentStart > refEnd) {
				refEnd = rtRecords.get(index).getTimeStamp() + rtRecords.get(index).getResponseTime();
				uniqueServletRecords.add(rtRecords.get(index));
			}

			index++;
		}
		return uniqueServletRecords;
	}

	/**
	 * A stifle antipattern can be detected with instrumenting the following:
	 * <ul>
	 * <li>servlet response time probe</li>
	 * <li>JDBC API queries</li>
	 * </ul>
	 * 
	 * @return the build {@link InstrumentationDescription}
	 * @throws InstrumentationException
	 */
	private InstrumentationDescription getInstrumentationDescription() throws InstrumentationException {

		InstrumentationDescriptionBuilder idBuilder = new InstrumentationDescriptionBuilder();
		idBuilder.newAPIScopeEntity(EntryPointScope.class.getName()).addProbe(ResponsetimeProbe.MODEL_PROBE)
				.entityDone();
		idBuilder.newAPIScopeEntity(JDBCScope.class.getName()).addProbe(SQLQueryProbe.MODEL_PROBE)
				.addProbe(ThreadTracingProbe.MODEL_PROBE).entityDone();

		return idBuilder.build();
	}

	private Map<String, List<StifleQuery>> analyzeDatasets(List<ResponseTimeRecord> rtRecords,
			List<SQLQueryRecord> sqlRecords) {

		if (rtRecords.size() < 2) {
			LOGGER.info("Less than two response time samples. We have too few data to do an analysis: Skipping stifle analyzing.");
			return new HashMap<String, List<StifleQuery>>();
		}

		Map<String, List<StifleQuery>> stifleQueries = new HashMap<>();

		// in this loop we will always be one index ahead of the element we
		// currently analyze
		ResponseTimeRecord currentRtRecord = null;
		int sqlIndex = 0;
		for (ResponseTimeRecord nextRtRecord : rtRecords) {
			if (currentRtRecord == null) {
				currentRtRecord = nextRtRecord;
				continue;
			}

			// the timespace is too inaccurate, hence we use the callId
			long currentRTCallId = currentRtRecord.getCallId();
			long nextRTCallId = nextRtRecord.getCallId();

			// we skip the first SQL queries, which are not related to the first
			// RT record
			while (sqlIndex < sqlRecords.size() && sqlRecords.get(sqlIndex).getCallId() < currentRTCallId) {
				sqlIndex++;
			}

			if (sqlIndex >= sqlRecords.size()) {
				return stifleQueries;
			}

			Map<String, Integer> potentialStifles = new HashMap<>();
			while (sqlIndex < sqlRecords.size() && sqlRecords.get(sqlIndex).getCallId() <= nextRTCallId) {

				String query = sqlRecords.get(sqlIndex).getQueryString();
				sqlIndex++;
				boolean found = false;
				for (String alreadyObservedQuery : potentialStifles.keySet()) {
					if (LpeStringUtils.getGeneralizedQuery(alreadyObservedQuery).equals(LpeStringUtils.getGeneralizedQuery(query))) {
						int newCount = potentialStifles.get(alreadyObservedQuery) + 1;
						potentialStifles.put(LpeStringUtils.getGeneralizedQuery(alreadyObservedQuery), newCount);
						found = true;
						break;
					}
				}

				if (!found) {
					potentialStifles.put(LpeStringUtils.getGeneralizedQuery(query), 1);
				}

				

			}
			String operation = currentRtRecord.getOperation();
			for (Entry<String, Integer> potStifle : potentialStifles.entrySet()) {
				if (potStifle.getValue() > 1) {
					if (!stifleQueries.containsKey(operation)) {
						stifleQueries.put(operation, new ArrayList<StifleQuery>());
					}
					List<StifleQuery> tmpStifleQueries = stifleQueries.get(operation);
					StifleQuery sQuery = null;

					for (StifleQuery tmpQuery : tmpStifleQueries) {
						if (potStifle.getKey().equals(tmpQuery.getQuery())) {
							sQuery = tmpQuery;
							break;
						}
					}
					if (sQuery == null) {
						sQuery = new StifleQuery(potStifle.getKey());
						tmpStifleQueries.add(sQuery);
					}
					sQuery.addOccurrence(potStifle.getValue());
				}
			}
			currentRtRecord = nextRtRecord;
		}

		return stifleQueries;
	}

}
