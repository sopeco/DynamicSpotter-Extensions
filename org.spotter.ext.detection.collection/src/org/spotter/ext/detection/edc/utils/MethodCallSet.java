package org.spotter.ext.detection.edc.utils;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

/**
 * This class represents a special set for storing and managing
 * {@link MethodCall MethodCalls}.
 * 
 * @author Henning Schulz
 * @see MethodCall
 * 
 */
public class MethodCallSet {

	private final Map<Long, MethodCallSetPerTid> methodCallsPerThreadId = new HashMap<>();

	private final long min;
	private final long max;
	private final long avgNumOfCallsPerThread;

	/**
	 * Constructor.
	 * 
	 * @param min
	 *            Minimum of used timestamps
	 * @param max
	 *            Maximum of used timestamps
	 * @param avgNumOfCallsPerThread
	 *            Estimated number of calls per thread id
	 */
	public MethodCallSet(long min, long max, long avgNumOfCallsPerThread) {
		super();
		this.min = min;
		this.max = max;
		this.avgNumOfCallsPerThread = avgNumOfCallsPerThread;
	}

	/**
	 * Adds a new method call recursively.
	 * 
	 * @param call
	 *            {@link MethodCall} to be added
	 */
	public void addCall(MethodCall call) {
		MethodCallSetPerTid callsForThisId = methodCallsPerThreadId.get(call.getThreadId());

		if (callsForThisId == null) {
			callsForThisId = new MethodCallSetPerTid(min, max, avgNumOfCallsPerThread, call.getThreadId());
			methodCallsPerThreadId.put(call.getThreadId(), callsForThisId);
		}

		callsForThisId.insert(call);
	}

	public boolean addCallIfNested(MethodCall call) {
		MethodCallSetPerTid callsForThisId = methodCallsPerThreadId.get(call.getThreadId());

		if (callsForThisId == null) {
			return false;
		}

		return callsForThisId.insertIfNested(call);
	}

	/**
	 * Returns all stored method calls.
	 * 
	 * @param callSet
	 *            All stored method calls
	 */
	public void addAllCalls(Collection<? extends MethodCall> callSet) {
		for (MethodCall call : callSet) {
			addCall(call);
		}
	}

	/**
	 * Returns all stored method calls.
	 * 
	 * @return All stored method calls
	 */
	public Set<MethodCall> getMethodCalls() {
		Set<MethodCall> callSet = new HashSet<>();

		for (long tid : methodCallsPerThreadId.keySet()) {
			callSet.addAll(methodCallsPerThreadId.get(tid).getAllCalls());
		}

		return callSet;
	}

	/**
	 * Returns all unique method names in this {@code MethodCallSet}.
	 * 
	 * @return All unique method names
	 */
	public Set<String> getUniqueMethods() {
		Set<String> uniqueMethods = new TreeSet<>();

		for (MethodCall call : getMethodCalls()) {
			uniqueMethods.add(call.getOperation());
		}

		return uniqueMethods;
	}

	/**
	 * Returns all unique method names of the given layer.
	 * 
	 * @return All unique method names of the given layer
	 */
	public Set<String> getUniqueMethodsOfLayer(int layer) {
		return getSubsetAtLayer(layer).getUniqueMethods();
	}

	/**
	 * Returns a new {@code MethodCallSet} rooting at the given layer.
	 * 
	 * @param layer
	 *            Layer of which the new {@code MethodCallSet} is to be created
	 * @return New {@code MethodCallSet} rooting at the given layer
	 */
	public MethodCallSet getSubsetAtLayer(int layer) {
		if (layer <= 0) {
			return this;
		}

		Set<MethodCall> callsOfLayer = getMethodCalls();

		for (int i = 0; i < layer; i++) {
			Set<MethodCall> tmp = new HashSet<>();

			for (MethodCall subcall : callsOfLayer) {
				tmp.addAll(subcall.getCalledOperations());
			}

			callsOfLayer = tmp;
		}

		MethodCallSet subset = new MethodCallSet(min, max, avgNumOfCallsPerThread);
		subset.addAllCalls(callsOfLayer);
		return subset;
	}

	public MethodCallSet getSubsetOfLowestLayer() {
		MethodCallSet finalSet = new MethodCallSet(min, max, avgNumOfCallsPerThread);

		for (MethodCall call : getMethodCalls()) {
			finalSet.addAllCalls(call.getFinalCalls());
		}

		return finalSet;
	}

	/**
	 * Generates a new {@code MethodCallSet} with all calls of the given layer
	 * but without recursive calls.
	 * 
	 * @param layer
	 *            Layer of which the calls are to be taken
	 * @return New {@code MethodCallSet} with all calls of the given layer but
	 *         without recursive calls
	 * @see MethodCallSet#getIsolatedCallsOfLayer(int)
	 */
	public MethodCallSet getFlatSubsetAtLayer(int layer) {
		MethodCallSet setAtLayer = new MethodCallSet(min, max, avgNumOfCallsPerThread);
		setAtLayer.addAllCalls(getIsolatedCallsOfLayer(layer));
		return setAtLayer;
	}

	/**
	 * Returns all method calls of the given operation which are in the given
	 * layer.
	 * 
	 * @param methodName
	 *            Name of the operation
	 * @param layer
	 *            Layer at which the call is to be searched
	 * @return All method calls of the given operation in the given layer
	 */
	public Set<MethodCall> getCallsOfMethodAtLayer(String methodName, int layer) {
		MethodCallSet setAtLayer = getSubsetAtLayer(layer);

		Set<MethodCall> callsOfMethod = new HashSet<>();

		for (MethodCall call : setAtLayer.getMethodCalls()) {
			if (call.getOperation().equals(methodName)) {
				callsOfMethod.add(call);
			}
		}

		return callsOfMethod;
	}

	/**
	 * Returns all calls in the given layer but removes all recursive calls.
	 * 
	 * @param layer
	 *            Layer of which the calls are to be returned
	 * @return All calls in the given layer without recursive calls
	 */
	public Set<MethodCall> getIsolatedCallsOfLayer(int layer) {
		Set<MethodCall> isolatedCalls = new HashSet<>();

		for (MethodCall call : getSubsetAtLayer(layer).getMethodCalls()) {
			isolatedCalls.add(new MethodCall(call.getOperation(), call.getEnterTime(), call.getExitTime(), call
					.getThreadId()));
		}

		return isolatedCalls;
	}

	/**
	 * Removes a call.
	 * 
	 * @param call
	 *            Call to be removed
	 */
	public void removeCall(MethodCall call) {
		MethodCallSetPerTid callsPerTid = methodCallsPerThreadId.get(call.getThreadId());

		if (callsPerTid == null) {
			return;
		} else {
			callsPerTid.remove(call);
		}
	}

	/**
	 * Removes all given calls.
	 * 
	 * @param calls
	 *            Calls to be removed
	 */
	public void removeAllCalls(Collection<? extends MethodCall> calls) {
		for (MethodCall call : calls) {
			removeCall(call);
		}
	}

	public void removeAllCallsWithName(String name) {
		for (Entry<Long, MethodCallSetPerTid> callsPerTidEntry : methodCallsPerThreadId.entrySet()) {
			callsPerTidEntry.getValue().removeAllCallsWithName(name);
		}
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();

		builder.append("[");
		boolean first = true;

		for (MethodCall call : getMethodCalls()) {
			if (first) {
				first = false;
			} else {
				builder.append(", ");
			}
			builder.append(call);
		}

		builder.append("]");

		return builder.toString();
	}

}
