package com.pubsubstore.revs.persistence;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import jdbm.PrimaryHashMap;
import jdbm.RecordManager;

import com.hazelcast.core.QueueStore;

public class JDBM2QueueStore<T> implements QueueStore<T> {

	private RecordManager recMan;
	
	private PrimaryHashMap<Long, T> phm;
	private String queueName;
	public void setQueueName(String queueName) {
		this.queueName = queueName;
	}

	public void init(RecordManager recMan) throws IOException {
		this.recMan = recMan;
		System.out.println("Init " + queueName);
		phm = recMan.hashMap(queueName);
	}

	public void delete(Long key) {
		phm.remove(key);
		try {
			recMan.commit();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void deleteAll(Collection<Long> keys) {
		for (Long key : keys) {
			phm.remove(key);
		}
		try {
			recMan.commit();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public T load(Long key) {
		return phm.get(key);
	}

	public Map<Long, T> loadAll(Collection<Long> keys) {
		return phm;
	}

	public Set<Long> loadAllKeys() {
		return phm.keySet();
	}

	public void store(Long key, T value) {
		//System.out.println("Stored in " + queueName + ":" + key);
		phm.put(key, value);
		try {
			recMan.commit();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void storeAll(Map<Long, T> map) {
		for (Long key : map.keySet()) {
			phm.put(key, map.get(key));
		}
		try {
			recMan.commit();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
