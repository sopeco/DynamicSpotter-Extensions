package org.spotter.ext.detection.edc.utils;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class MethodCallSet {

	private final Map<Long, Set<MethodCall>> methodCallsPerThreadId = new HashMap<>();

	public void addCall(MethodCall call) {
		Set<MethodCall> callsForThidId = methodCallsPerThreadId.get(call.getThreadId());
		if (callsForThidId == null) {
			callsForThidId = new HashSet<>();
			methodCallsPerThreadId.put(call.getThreadId(), callsForThidId);
		}

		boolean newCallIsParentCall = false;
		for (MethodCall existingCall : callsForThidId) {
			if (call.addCall(existingCall)) {
				newCallIsParentCall = true;
				callsForThidId.remove(existingCall);
			}
		}

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

	public void addAllCalls(Collection<? extends MethodCall> callSet) {
		for (MethodCall call : callSet) {
			addCall(call);
		}
	}

	public Set<MethodCall> getCallsInRange(long enterTime, long exitTime, long threadId) {
		Set<MethodCall> callsInRange = new HashSet<>();

		for (MethodCall call : methodCallsPerThreadId.get(threadId)) {
			if (call.getEnterTime() >= enterTime && call.getExitTime() <= exitTime) {
				callsInRange.add(call);
			}
		}

		return callsInRange;
	}

	public Set<MethodCall> getMethodCalls() {
		Set<MethodCall> callSet = new HashSet<>();

		for (long tid : methodCallsPerThreadId.keySet()) {
			callSet.addAll(methodCallsPerThreadId.get(tid));
		}

		return callSet;
	}

	public Set<String> getUniqueMethods() {
		Set<String> uniqueMethods = new TreeSet<>();

		for (MethodCall call : getMethodCalls()) {
			uniqueMethods.add(call.getOperation());
		}

		return uniqueMethods;
	}

	public Set<String> getUniqueMethodsOfLayer(int layer) {
		return getSubsetAtLayer(layer).getUniqueMethods();
	}

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

	public Set<MethodCall> getCallsOfMethodAtLayer(String methodName, int layer) {
		MethodCallSet setToActWith = getSubsetAtLayer(layer);
		
		Set<MethodCall> callsOfMethod = new HashSet<>();

		for (MethodCall call : setToActWith.getMethodCalls()) {
			if (call.getOperation().equals(methodName)) {
				callsOfMethod.add(call);
			}
		}

		return callsOfMethod;
	}

	public Set<MethodCall> getIsolatedCallsOfLayer(int layer) {
		Set<MethodCall> isolatedCalls = new HashSet<>();

		for (MethodCall call : getSubsetAtLayer(layer).getMethodCalls()) {
			isolatedCalls.add(new MethodCall(call.getOperation(), call.getEnterTime(), call.getExitTime(), call
					.getThreadId()));
		}

		return isolatedCalls;
	}
	
	public void removeCall(MethodCall call) {
		methodCallsPerThreadId.get(call.getThreadId()).remove(call);
	}
	
	public void removeAllCalls(Collection<? extends MethodCall> calls) {
		for (MethodCall call : calls) {
			removeCall(call);
		}
	}

}
