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
package org.spotter.ext.detection.edc.utils;

import java.util.HashSet;
import java.util.Set;

/**
 * This class represents an instantiation of a method which may call another
 * method.
 * 
 * @author Henning Schulz
 * @see MethodCallSet
 * 
 */
public class MethodCall {

	private String operation;
	private Set<MethodCall> calledOperations;
	private long enterTime;
	private long exitTime;
	private long threadId;

	/**
	 * Constructor.
	 * 
	 * @param operation
	 *            Method name
	 * @param enterTime
	 *            Enter time
	 * @param exitTime
	 *            Exit time
	 * @param threadId
	 *            Thread ID
	 */
	public MethodCall(String operation, long enterTime, long exitTime, long threadId) {
		super();
		this.operation = operation;
		this.enterTime = enterTime;
		this.exitTime = exitTime;
		this.threadId = threadId;
	}

	/**
	 * @return the operation
	 */
	public String getOperation() {
		return operation;
	}

	/**
	 * @param operation
	 *            the operation to set
	 */
	public void setOperation(String operation) {
		this.operation = operation;
	}

	/**
	 * @return the enterTime
	 */
	public long getEnterTime() {
		return enterTime;
	}

	/**
	 * @param enterTime
	 *            the enterTime to set
	 */
	public void setEnterTime(long enterTime) {
		this.enterTime = enterTime;
	}

	/**
	 * @return the exitTime
	 */
	public long getExitTime() {
		return exitTime;
	}

	/**
	 * @param exitTime
	 *            the exitTime to set
	 */
	public void setExitTime(long exitTime) {
		this.exitTime = exitTime;
	}

	/**
	 * @return the threadId
	 */
	public long getThreadId() {
		return threadId;
	}

	/**
	 * @param threadId
	 *            the threadId to set
	 */
	public void setThreadId(long threadId) {
		this.threadId = threadId;
	}

	/**
	 * @return the calledOperations
	 */
	public Set<MethodCall> getCalledOperations() {
		if (calledOperations == null) {
			calledOperations = new HashSet<>();
		}

		return calledOperations;
	}

	/**
	 * Returns the response time.
	 * 
	 * @return the response time
	 */
	public double getResponseTime() {
		return getExitTime() - getEnterTime();
	}

	public Set<MethodCall> getFinalCalls() {
		Set<MethodCall> finalCalls = new HashSet<>();
		Set<MethodCall> nonFinalCalls = new HashSet<>();
		nonFinalCalls.add(this);

		while (!nonFinalCalls.isEmpty()) {
			Set<MethodCall> tmp = new HashSet<>();

			for (MethodCall call : nonFinalCalls) {
				if (call.getCalledOperations() == null || call.getCalledOperations().isEmpty()) {
					finalCalls.add(call);
				} else {
					tmp.addAll(call.getCalledOperations());
				}
			}

			nonFinalCalls = tmp;
		}

		return finalCalls;
	}

	/**
	 * Returns iff the given call is a nested call of this.
	 * 
	 * @param call
	 *            Method call to be tested
	 * @return Iff the given call is a nested call
	 */
	public boolean isParentOf(MethodCall call) {
		if (this.getEnterTime() == call.getEnterTime() && call.getEnterTime() == call.getExitTime()) {
			return false;
		}

		return this.getThreadId() == call.getThreadId() && this.getEnterTime() <= call.getEnterTime()
				&& this.getExitTime() >= call.getExitTime();
	}

	/**
	 * Adds a call as nested method call.
	 * 
	 * @param operation
	 *            Method name
	 * @param enterTime
	 *            Enter time
	 * @param exitTime
	 *            Exit time
	 * @param threadId
	 *            Thread ID
	 * @return Iff the call could be added as subcall
	 */
	public boolean addCall(String operation, long enterTime, long exitTime, long threadId) {
		MethodCall call = new MethodCall(operation, enterTime, exitTime, threadId);
		return addCall(call);
	}

	/**
	 * Adds a call as nested method call.
	 * 
	 * @param newCall
	 *            Method call to be added
	 * @return Iff the call could be added as subcall
	 */
	public boolean addCall(MethodCall newCall) {
		if (this.equals(newCall)) {
			return true;
		}

		if (this.isParentOf(newCall)) {
			if (this.calledOperations == null) {
				this.calledOperations = new HashSet<>();
			}

			boolean addedInChild = false;
			for (MethodCall childCall : getCalledOperations()) {
				addedInChild = childCall.addCall(newCall);
				if (addedInChild) {
					break;
				}
			}

			if (!addedInChild) {
				Set<MethodCall> toRemove = new HashSet<>();
				for (MethodCall childCall : getCalledOperations()) {
					if (newCall.addCall(childCall)) {
						toRemove.add(childCall);

					}
				}
				getCalledOperations().removeAll(toRemove);

				this.calledOperations.add(newCall);
			}

			return true;
		} else {
			return false;
		}
	}

	public boolean removeCall(MethodCall call) {
		if (this.equals(call)) {
			return false;
		}

		if (this.isParentOf(call)) {
			for (MethodCall subCall : getCalledOperations()) {
				if (subCall.equals(call)) {
					calledOperations.remove(call);
					return true;
				}
			}

			for (MethodCall subCall : getCalledOperations()) {
				if (subCall.isParentOf(call)) {
					return subCall.removeCall(call);
				}
			}
		}

		return false;
	}

	public void removeNestedCallsWithName(String name) {
		Set<MethodCall> toRemove = new HashSet<>();

		for (MethodCall nestedCall : getCalledOperations()) {
			if (nestedCall.getOperation().equals(name)) {
				toRemove.add(nestedCall);
			} else {
				nestedCall.removeNestedCallsWithName(name);
			}
		}

		getCalledOperations().removeAll(toRemove);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null || !obj.getClass().equals(this.getClass())) {
			return false;
		}

		MethodCall other = (MethodCall) obj;

		return this.getOperation().equals(other.getOperation()) && this.getEnterTime() == other.getEnterTime()
				&& this.getExitTime() == other.getExitTime() && this.getThreadId() == other.getThreadId();
	}

	@Override
	public int hashCode() {
		int hashCode = 11;
		int multi = 29;

		hashCode = hashCode * multi + getOperation().hashCode();
		hashCode = hashCode * multi + (int) (getEnterTime() & 0xFFFFFFFF);
		hashCode = hashCode * multi + (int) (getExitTime() & 0xFFFFFFFF);
		hashCode = hashCode * multi + (int) (getThreadId() & 0xFFFFFFFF);

		return hashCode;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append(operation);
		builder.append("[tid ");
		builder.append(threadId);
		builder.append("]: ");
		builder.append(enterTime);
		builder.append("-");
		builder.append(exitTime);
		builder.append(" => {");

		boolean first = true;
		for (MethodCall call : getCalledOperations()) {
			if (first) {
				first = false;
			} else {
				builder.append(", ");
			}

			builder.append(call);
		}

		builder.append("}");

		return builder.toString();
	}

}
