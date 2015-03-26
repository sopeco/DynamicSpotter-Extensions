package org.spotter.ext.detection.edc.utils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;

public class MethodCallSetPerTid {

	private static final long AVG_ENTRIES_PER_BUCKET = 10;

	private final long tid;

	private final HashMap<Long, Set<MethodCall>> buckets = new HashMap<>();

	private final long min;
	private final double bucketsize;

	/**
	 * Constructor.
	 * 
	 * @param min
	 *            Minimum of used timestamps
	 * @param max
	 *            Maximum of used timestamps
	 * @param numEntries
	 *            Estimated number of calls inserted into this set
	 */
	public MethodCallSetPerTid(long min, long max, long numEntries, long tid) {
		this.tid = tid;
		this.min = min;
		double avgEntriesPerTimeslot = ((double) numEntries) / ((double) (max - min));
		double numberOfSlots = Math.max(1, (long) (AVG_ENTRIES_PER_BUCKET / avgEntriesPerTimeslot));
		this.bucketsize = (max - min) / numberOfSlots;
	}

	/**
	 * Inserts a new MethodCall.
	 * 
	 * @param call
	 *            MethodCall to be inserted
	 */
	public void insert(MethodCall call) {
		boolean isParent = false;
		long startIdx = getStartIdx(call.getEnterTime());
		long endIdx = getEndIdx(call.getExitTime());

		for (long i = startIdx; i < endIdx; i++) {
			Set<MethodCall> bucket = buckets.get(i);
			Set<MethodCall> toRemove = new HashSet<>();

			if (bucket == null) {
				continue;
			}

			for (MethodCall existingCall : bucket) {
				if (call.isParentOf(existingCall)) {
					isParent = true;
					call.addCall(existingCall);
					toRemove.add(existingCall);
				}
			}

			bucket.removeAll(toRemove);
		}

		boolean isChild = false;

		if (!isParent) {
			Set<MethodCall> firstBucket = buckets.get(startIdx);

			if (firstBucket != null) {
				for (MethodCall existingCall : firstBucket) {
					if (existingCall.isParentOf(call)) {
						isChild = true;
						existingCall.addCall(call);
						break;
					}
				}
			}
		}

		if (isParent || !isChild) {
			for (long i = startIdx; i < endIdx; i++) {
				Set<MethodCall> bucket = buckets.get(i);

				if (bucket == null) {
					bucket = new HashSet<>();
					buckets.put(i, bucket);
				}

				bucket.add(call);
			}
		}
	}

	/**
	 * Inserts a new MethodCall if it is a nested call of an existing call.
	 * 
	 * @param call
	 *            MethodCall to be inserted
	 * @return Iff the given call is a nested call
	 */
	public boolean insertIfNested(MethodCall call) {
		boolean isChild = false;

		Set<MethodCall> firstBucket = buckets.get(getStartIdx(call.getEnterTime()));

		if (firstBucket != null) {
			for (MethodCall existingCall : firstBucket) {
				if (existingCall.isParentOf(call)) {
					isChild = true;
					existingCall.addCall(call);
					break;
				}
			}
		}

		return isChild;
	}

	/**
	 * Removes a call.
	 * 
	 * @param call
	 *            MethodCall to be removed
	 */
	public void remove(MethodCall call) {
		for (long i = getStartIdx(call.getEnterTime()); i < getEndIdx(call.getExitTime()); i++) {
			Set<MethodCall> bucket = buckets.get(i);

			if (bucket == null) {
				continue;
			}

			boolean removed = bucket.remove(call);

			if (!removed) {
				for (MethodCall existingCall : bucket) {
					existingCall.removeCall(call);
				}
			}
		}
	}

	/**
	 * Removes all calls having the specified name.
	 * 
	 * @param name
	 *            Method name to be removed
	 */
	public void removeAllCallsWithName(String name) {
		for (Entry<Long, Set<MethodCall>> bucketEntry : buckets.entrySet()) {
			Set<MethodCall> toRemove = new HashSet<>();

			for (MethodCall call : bucketEntry.getValue()) {
				if (call.getOperation().equals(name)) {
					toRemove.add(call);
				} else {
					call.removeNestedCallsWithName(name);
				}
			}

			bucketEntry.getValue().removeAll(toRemove);
		}
	}

	/**
	 * Returns all calls.
	 * 
	 * @return All calls
	 */
	public Set<MethodCall> getAllCalls() {
		Set<MethodCall> allCalls = new HashSet<>();

		for (Entry<Long, Set<MethodCall>> bucketEntry : buckets.entrySet()) {
			allCalls.addAll(bucketEntry.getValue());
		}

		return allCalls;
	}

	/**
	 * Returns the common thread id of the MethodCalls saved in this set.
	 * 
	 * @return the common thread id of the MethodCalls saved in this set
	 */
	public long getThreadId() {
		return tid;
	}

	private long getStartIdx(long timestamp) {
		return (long) Math.floor((timestamp - min) / bucketsize);
	}

	private long getEndIdx(long timestamp) {
		return (long) Math.ceil((timestamp - min) / bucketsize);
	}

}
