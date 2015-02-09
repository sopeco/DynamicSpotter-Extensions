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
		if (this.getThreadId() == newCall.getThreadId() && this.getEnterTime() <= newCall.getEnterTime()
				&& this.getExitTime() >= newCall.getExitTime()) {
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
				for (MethodCall childCall : getCalledOperations()) {
					if (newCall.addCall(childCall)) {
						getCalledOperations().remove(childCall);
					}
				}

				this.calledOperations.add(newCall);
			}

			return true;
		} else {
			return false;
		}
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null || !obj.getClass().getName().equals(this.getClass().getName())) {
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

}
