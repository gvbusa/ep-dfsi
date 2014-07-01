package com.pubsubstore.revs.persistence;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import jdbm.PrimaryHashMap;
import jdbm.RecordManager;

import org.springframework.beans.factory.annotation.Autowired;

import com.hazelcast.core.MapStore;

public class JDBM2MapStore<K, V> implements MapStore<K, V> {
	
	@Autowired
	protected RecordManager recMan;
	
	private PrimaryHashMap<K, V> phm;
	private String mapName;
	
	
	public void setMapName(String mapName) {
		this.mapName = mapName;
	}

	public void init() throws IOException {
		System.out.println("Init " + mapName);
		phm = recMan.hashMap(mapName);
	}
	
	public V load(K key) {
		return phm.get(key);
	}

	public Map<K, V> loadAll(Collection<K> keys) {
		return phm;
	}

	public Set<K> loadAllKeys() {
		return phm.keySet();
	}

	public void delete(K key) {
		phm.remove(key);
		try {
			recMan.commit();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void deleteAll(Collection<K> keys) {
		for (K key : keys) {
			phm.remove(key);
		}
		try {
			recMan.commit();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void store(K key, V value) {
		//System.out.println("Stored in " + mapName + ":" + key);
		phm.put(key, value);
		try {
			recMan.commit();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void storeAll(Map<K, V> map) {
		for (K key : map.keySet()) {
			phm.put(key, map.get(key));
		}
		try {
			recMan.commit();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
