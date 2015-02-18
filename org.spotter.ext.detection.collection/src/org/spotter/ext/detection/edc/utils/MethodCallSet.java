package org.spotter.ext.detection.edc.utils;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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

	private final Map<Long, Set<MethodCall>> methodCallsPerThreadId = new HashMap<>();

	/**
	 * Adds a new method call recursively.
	 * 
	 * @param call
	 *            {@link MethodCall} to be added
	 */
	public void addCall(MethodCall call) {
		Set<MethodCall> callsForThidId = methodCallsPerThreadId.get(call.getThreadId());
		if (callsForThidId == null) {
			callsForThidId = new HashSet<>();
			methodCallsPerThreadId.put(call.getThreadId(), callsForThidId);
		}

		boolean newCallIsParentCall = false;
		Set<MethodCall> callsToRemove = new HashSet<>();
		for (MethodCall existingCall : callsForThidId) {
			if (call.addCall(existingCall)) {
				newCallIsParentCall = true;
				callsToRemove.add(existingCall);
			}
		}
		callsForThidId.removeAll(callsToRemove);

		if (newCallIsParentCall) {
			callsForThidId.add(call);
		} else {
			boolean newCallIsChildCall = false;
			for (MethodCall existingCall : callsForThidId) {
				newCallIsChildCall = existingCall.addCall(call);
				if (newCallIsChildCall) {
					break;
				}
			}

			if (!newCallIsChildCall) {
				callsForThidId.add(call);
			}
		}

		callsForThidId.add(call);
	}

	public boolean addCallIfNested(MethodCall call) {
		boolean added = false;

		for (MethodCall existingCall : getMethodCalls()) {
			if (existingCall.addCall(call)) {
				added = true;
				break;
			}
		}

		return added;
	}

	public boolean addCallIfNotNested(MethodCall call) {
		boolean nestedCall = false;

		for (MethodCall existingCall : getMethodCalls()) {
			if (existingCall.isParentOf(call)) {
				nestedCall = true;
				break;
			}
		}

		if (!nestedCall) {
			Set<MethodCall> callsPerId = methodCallsPerThreadId.get(call.getThreadId());
			if (callsPerId == null) {
				callsPerId = new HashSet<>();
				methodCallsPerThreadId.put(call.getThreadId(), callsPerId);
			}
			callsPerId.add(call);
		}

		return nestedCall;
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
	 * Returns all method calls in the given range.
	 * 
	 * @param enterTime
	 *            Minimum enter time
	 * @param exitTime
	 *            Maximum exit time
	 * @param threadId
	 *            thread id
	 * @return All method calls in the given range
	 */
	public Set<MethodCall> getCallsInRange(long enterTime, long exitTime, long threadId) {
		Set<MethodCall> callsInRange = new HashSet<>();

		for (MethodCall call : methodCallsPerThreadId.get(threadId)) {
			if (call.getEnterTime() >= enterTime && call.getExitTime() <= exitTime) {
				callsInRange.add(call);
			}
		}

		return callsInRange;
	}

	/**
	 * Returns all stored method calls.
	 * 
	 * @return All stored method calls
	 */
	public Set<MethodCall> getMethodCalls() {
		Set<MethodCall> callSet = new HashSet<>();

		for (long tid : methodCallsPerThreadId.keySet()) {
			callSet.addAll(methodCallsPerThreadId.get(tid));
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

		MethodCallSet subset = new MethodCallSet();
		subset.addAllCalls(callsOfLayer);
		return subset;
	}

	public MethodCallSet getSubsetOfLowestLayer() {
		MethodCallSet finalSet = new MethodCallSet();

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
		MethodCallSet setAtLayer = new MethodCallSet();
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
		Set<MethodCall> calls = methodCallsPerThreadId.get(call.getThreadId());

		if (calls.contains(call)) {
			methodCallsPerThreadId.remove(call);
		} else {
			for (MethodCall parentCall : calls) {
				parentCall.removeCall(call);
			}
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
		for (long tid : methodCallsPerThreadId.keySet()) {
			Set<MethodCall> toRemove = new HashSet<>();

			for (MethodCall call : methodCallsPerThreadId.get(tid)) {
				if (call.getOperation().equals(name)) {
					toRemove.add(call);
				}
			}

			methodCallsPerThreadId.get(tid).removeAll(toRemove);
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
