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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Trace representation.
 * 
 * @author Alexander Wert
 * 
 */
public class Trace implements Iterable<Trace> {
	private static final int HASH_CONSTANT_2 = 1237;
	private static final int HASH_CONSTANT_1 = 1231;
	private List<Trace> subTraces;
	private Trace parent;
	private String methodName;
	private long startTime;
	private long exitTime;
	private boolean sendMethod;
	private long overhead;
	private long payload;

	/**
	 * Constructor.
	 */
	public Trace() {
		setParent(null);
	}

	/**
	 * Constructor.
	 * 
	 * @param parent
	 *            parent method
	 */
	public Trace(Trace parent) {
		setParent(parent);
	}

	/**
	 * Constructor.
	 * 
	 * @param methodName
	 *            name of the current method
	 */
	public Trace(String methodName) {
		setParent(null);
		setMethodName(methodName);
	}

	/**
	 * Constructor.
	 * 
	 * @param methodName
	 *            name of the current method
	 * @param parent
	 *            parent method
	 */
	public Trace(Trace parent, String methodName) {
		setParent(parent);
		setMethodName(methodName);
	}

	/**
	 * @return the subTraces
	 */
	public List<Trace> getSubTraces() {
		if (subTraces == null) {
			subTraces = new ArrayList<>();
		}
		return subTraces;
	}

	/**
	 * @return the methodName
	 */
	public String getMethodName() {
		return methodName;
	}

	/**
	 * @param methodName
	 *            the methodName to set
	 */
	public void setMethodName(String methodName) {
		this.methodName = methodName;
	}

	/**
	 * @return the startTime
	 */
	public long getStartTime() {
		return startTime;
	}

	/**
	 * @param startTime
	 *            the startTime to set
	 */
	public void setStartTime(long startTime) {
		this.startTime = startTime;
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
	 * @return the parent
	 */
	public Trace getParent() {
		return parent;
	}

	/**
	 * @param parent
	 *            the parent to set
	 */
	public void setParent(Trace parent) {
		this.parent = parent;
		if (parent != null && !parent.getSubTraces().contains(this)) {
			parent.getSubTraces().add(this);
		}
	}

	@Override
	public String toString() {
		int depth = 0;
		Trace parent = getParent();
		while (parent != null) {
			depth++;
			parent = parent.getParent();
		}
		StringBuilder indention = new StringBuilder();
		for (int i = 0; i < depth; i++) {
			indention.append("   ");
		}

		StringBuilder strBuilder = new StringBuilder();
		strBuilder.append(indention.toString());
		strBuilder.append(getMethodName());

		strBuilder.append("\n");
		for (Trace child : getSubTraces()) {
			strBuilder.append(child.toString());
		}

		return strBuilder.toString();
	}

	/**
	 * 
	 * @param other
	 *            trace to compare with
	 * @return returns true if other trace represents the same operation
	 *         sequence
	 */
	public boolean similarTrace(Trace other) {
		if (!getMethodName().equals(other.getMethodName())) {
			return false;
		}

		for (int i = 0; i < getSubTraces().size(); i++) {
			if (!getSubTraces().get(i).similarTrace(other.getSubTraces().get(i))) {
				return false;
			}
		}

		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((methodName == null) ? 0 : methodName.hashCode());
		result = prime * result + (sendMethod ? HASH_CONSTANT_1 : HASH_CONSTANT_2);
		result = prime * result + ((subTraces == null) ? 0 : subTraces.hashCode());
		return result;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		Trace other = (Trace) obj;
		if (methodName == null) {
			if (other.methodName != null) {
				return false;
			}
		} else if (!methodName.equals(other.methodName)) {
			return false;
		}
		if (sendMethod != other.sendMethod) {
			return false;
		}
		if (subTraces == null) {
			if (other.subTraces != null) {
				return false;
			}
		} else if (!subTraces.equals(other.subTraces)) {
			return false;
		}
		return true;
	}

	@Override
	public Iterator<Trace> iterator() {
		return new TraceIterator(this);
	}

	/**
	 * @return the sendMethod
	 */
	public boolean isSendMethod() {
		return sendMethod;
	}

	/**
	 * @param sendMethod
	 *            the sendMethod to set
	 */
	public void setSendMethod(boolean sendMethod) {
		this.sendMethod = sendMethod;
	}

	/**
	 * @return the overhead
	 */
	public long getOverhead() {
		return overhead;
	}

	/**
	 * @param overhead
	 *            the overhead to set
	 */
	public void setOverhead(long overhead) {
		this.overhead = overhead;
	}

	/**
	 * @return the payload
	 */
	public long getPayload() {
		return payload;
	}

	/**
	 * @param payload
	 *            the payload to set
	 */
	public void setPayload(long payload) {
		this.payload = payload;
	}

	/**
	 * Iterator for the trace object.
	 * 
	 * @author Alexander Wert
	 * 
	 */
	public class TraceIterator implements Iterator<Trace> {

		private Trace originTrace;
		private Trace currentTrace;
		private Map<Integer, Integer> levelChildMapping;
		private int currentLevel = 0;
		private boolean finished = false;

		/**
		 * Constructor.
		 * 
		 * @param trace
		 *            trace to iterate over.
		 */
		public TraceIterator(Trace trace) {
			this.originTrace = trace;
			this.currentTrace = trace;
			levelChildMapping = new HashMap<>();
		}

		@Override
		public boolean hasNext() {
			if (finished) {
				return false;
			}
			return currentTrace != null;
		}

		@Override
		public Trace next() {
			if (finished) {
				return null;
			}
			Trace result = currentTrace;
			prepareNext();
			return result;
		}

		private void prepareNext() {
			if (currentTrace != null) {
				if (!levelChildMapping.containsKey(currentLevel)) {
					levelChildMapping.put(currentLevel, 0);
				}
				int currentChildIndex = levelChildMapping.get(currentLevel);
				Trace tempTrace = currentTrace;

				while (tempTrace.getSubTraces().size() <= currentChildIndex) {
					if (tempTrace == originTrace) {
						break;
					}
					levelChildMapping.remove(currentLevel);
					currentLevel--;
					tempTrace = tempTrace.getParent();

					currentChildIndex = levelChildMapping.get(currentLevel);

				}
				if (tempTrace.getSubTraces().size() <= currentChildIndex) {
					finished = true;
					currentTrace = null;
					return;
				}

				currentTrace = tempTrace.getSubTraces().get(currentChildIndex);
				levelChildMapping.put(currentLevel, currentChildIndex + 1);
				currentLevel++;
				return;

			}
		}

		@Override
		public void remove() {
			// not supported
		}

	}

}
