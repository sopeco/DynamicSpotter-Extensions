package org.spotter.ext.detection.edc.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.aim.api.measurement.AbstractRecord;
import org.aim.api.measurement.dataset.Dataset;
import org.aim.api.measurement.dataset.ParameterSelection;
import org.aim.artifacts.records.ResponseTimeRecord;
import org.aim.artifacts.records.SQLQueryRecord;
import org.aim.artifacts.records.StackTraceRecord;
import org.aim.artifacts.records.ThreadTracingRecord;
import org.lpe.common.util.NumericPair;
import org.lpe.common.util.NumericPairList;

public class DataAnalyzationUtils {

	private DataAnalyzationUtils() {
	}

	/**
	 * Generates a NumericPairList of the response times of the given servlet.
	 * 
	 * @param servlet
	 *            servlet to get the response times of
	 * @param responseTimes
	 *            dataset of all response times
	 * @return a NumericPairList of the response times of the given servlet
	 */
	public static NumericPairList<Long, Long> getServletResponseTimesOverTime(String servlet, Dataset responseTimes) {
		NumericPairList<Long, Long> rtList = new NumericPairList<>();

		Dataset thisServletsRTs = selectRTOperation(servlet).applyTo(responseTimes);

		if (thisServletsRTs == null) {
			return rtList;
		}

		for (ResponseTimeRecord rtRec : thisServletsRTs.getRecords(ResponseTimeRecord.class)) {
			rtList.add(new NumericPair<Long, Long>(rtRec.getTimeStamp(), rtRec.getResponseTime()));
		}

		return rtList;
	}

	/**
	 * Generates a NumericPairList of the response times of the given query.
	 * 
	 * @param query
	 *            query to get the response times of
	 * @param responseTimes
	 *            dataset of all response times
	 * @param queries
	 *            dataset of all queries
	 * @return a NumericPairList of the response times of the given query
	 */
	public static NumericPairList<Long, Long> getQueryResponseTimesOverTime(String query, Dataset responseTimes,
			Dataset queries) {
		NumericPairList<Long, Long> rtList = new NumericPairList<>();

		Dataset thisQueryRecords = new ParameterSelection().select(SQLQueryRecord.PAR_QUERY_STRING, query).applyTo(
				queries);
		if (thisQueryRecords == null) {
			return rtList;
		}

		for (SQLQueryRecord sqlRec : thisQueryRecords.getRecords(SQLQueryRecord.class)) {
			Dataset rtDs = selectCallID(sqlRec.getCallId()).applyTo(responseTimes);
			if (rtDs == null) {
				return rtList;
			}

			for (ResponseTimeRecord rtRec : rtDs.getRecords(ResponseTimeRecord.class)) {
				rtList.add(new NumericPair<Long, Long>(rtRec.getTimeStamp(), rtRec.getResponseTime()));
			}
		}

		return rtList;
	}

	/**
	 * Extracts the unique operation names in the given dataset.
	 * 
	 * @param responseTimes
	 *            dataset to retrieve the operation names from
	 * @return unique operation names in the given dataset
	 */
	public static Set<String> extractUniqueMethodNames(Dataset responseTimes) {
		Set<String> uniqueNames = new TreeSet<>();

		for (ResponseTimeRecord rtRecord : responseTimes.getRecords(ResponseTimeRecord.class)) {
			if (!rtRecord.getOperation().startsWith("org.apache")) {
				uniqueNames.add(rtRecord.getOperation());
			}
		}

		return uniqueNames;
	}

	/**
	 * Generates a MethodCallSet only taking into account the given method
	 * names.
	 * 
	 * @param methodNames
	 *            method names to take into account
	 * @param responseTimes
	 *            dataset of all response times
	 * @param threadTracing
	 *            dataset of all thread tracing
	 * @return a MethodCallSet only taking into account the given method names
	 */
	public static MethodCallSet getMethodCallSetOfMethods(Set<String> methodNames, Dataset responseTimes,
			Dataset threadTracing) {
		MethodCallSet servletCallSet = new MethodCallSet();

		for (ResponseTimeRecord rtRec : responseTimes.getRecords(ResponseTimeRecord.class)) {
			if (methodNames.contains(rtRec.getOperation())) {
				Dataset ttSet = selectCallID(rtRec.getCallId()).applyTo(threadTracing);
				if (ttSet == null) {
					continue;
				}
				long threadId = ttSet.getRecords(ThreadTracingRecord.class).get(0).getThreadId();
				servletCallSet.addCall(new MethodCall(rtRec.getOperation(), rtRec.getTimeStamp(), rtRec.getTimeStamp()
						+ rtRec.getResponseTime(), threadId));
			}
		}

		return servletCallSet;
	}

	/**
	 * Inserts the given queries into the given MethodCallSet.
	 * 
	 * @param set
	 *            MethodCallSet to insert the queries
	 * @param responseTimes
	 *            dataset of all response times
	 * 
	 * @param queries
	 *            dataset of all queries
	 * @param threadTracing
	 *            dataset of all thread tracing
	 */
	public static void addQueriesToMethodCallSet(MethodCallSet set, Dataset responseTimes, Dataset queries,
			Dataset threadTracing) {
		for (SQLQueryRecord sqlRecord : queries.getRecords(SQLQueryRecord.class)) {
			if(sqlRecord.getQueryString() == null){
				continue;
			}
			Dataset querySet = selectCallID(sqlRecord.getCallId()).applyTo(responseTimes);
			if (querySet == null) {
				continue;
			}
			ResponseTimeRecord rtRecord = querySet.getRecords(ResponseTimeRecord.class).get(0);

			Dataset ttSet = selectCallID(sqlRecord.getCallId()).applyTo(threadTracing);
			if (ttSet == null) {
				continue;
			}
			ThreadTracingRecord ttRecord = ttSet.getRecords(ThreadTracingRecord.class).get(0);

			set.addCallIfNested(new MethodCall(sqlRecord.getQueryString(), rtRecord.getTimeStamp(), rtRecord
					.getTimeStamp() + rtRecord.getResponseTime(), ttRecord.getThreadId()));
		}
	}

	/**
	 * Returns a ParameterSelection which selects the given call id.
	 * 
	 * @param callId
	 *            call id
	 * @return a ParameterSelection which selects the given call id
	 */
	private static ParameterSelection selectCallID(long callId) {
		return new ParameterSelection().select(AbstractRecord.PAR_CALL_ID, callId);
	}

	/**
	 * Returns a ParameterSelection which selects the given operation.
	 * 
	 * @param operation
	 *            operation name
	 * @return a ParameterSelection which selects the given operation
	 */
	private static ParameterSelection selectRTOperation(String operation) {
		return new ParameterSelection().select(ResponseTimeRecord.PAR_OPERATION, operation);
	}

	/**
	 * Returns all stack traces containing the given query.
	 * 
	 * @param query
	 *            query to get the stack traces from
	 * @param queries
	 *            dataset of all queries
	 * @param stackTraces
	 *            dataset of all stack traces
	 * @return all stack traces containing the given query
	 */
	public static Set<String> getStackTracesOfQuery(String query, Dataset queries, Dataset stackTraces) {
		Set<String> stackTraceSet = new TreeSet<>();

		ParameterSelection selectQuery = new ParameterSelection().select(SQLQueryRecord.PAR_QUERY_STRING, query);
		List<SQLQueryRecord> queryRecords = selectQuery.applyTo(queries).getRecords(SQLQueryRecord.class);

		for (SQLQueryRecord queryRecord : queryRecords) {
			Dataset stSet = selectCallID(queryRecord.getCallId()).applyTo(stackTraces);
			if (stSet == null) {
				continue;
			}
			StackTraceRecord stackTraceRecord = stSet.getRecords(StackTraceRecord.class).get(0);
			stackTraceSet.add(stackTraceRecord.getStackTrace());
		}

		return stackTraceSet;
	}

	/**
	 * Computes the relative response times (relative to the calling servlets)
	 * of the given query.
	 * 
	 * @param query
	 *            query to compute the relative response times from
	 * @param servletQueryHierarchy
	 *            MethodCallSet
	 * @return the relative response times (relative to the calling servlets) of
	 *         the given query
	 */
	public static List<Double> getRelativeQueryResponseTimes(String query, MethodCallSet servletQueryHierarchy) {
		List<Double> relativeResponseTimes = new ArrayList<>();

		for (MethodCall servletCall : servletQueryHierarchy.getMethodCalls()) {
			for (MethodCall queryCall : servletCall.getCalledOperations()) {
				if (queryCall.getOperation().equals(query)) {
					relativeResponseTimes.add((double) queryCall.getResponseTime()
							/ (double) servletCall.getResponseTime());
				}
			}
		}

		return relativeResponseTimes;
	}

}
