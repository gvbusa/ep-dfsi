package com.pubsubstore.revs.persistence;

import java.util.Properties;

import jdbm.RecordManager;

import org.springframework.beans.factory.annotation.Autowired;

import com.hazelcast.core.QueueStore;
import com.hazelcast.core.QueueStoreFactory;

public class JDBM2QueueStoreFactory<T> implements QueueStoreFactory<T> {
	@Autowired
	protected RecordManager recMan;

	public QueueStore<T> newQueueStore(String queueName, Properties props) {
		JDBM2QueueStore<T> qs = new JDBM2QueueStore<T>();
		qs.setQueueName(queueName);
		try {
			qs.init(recMan);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return qs;
	}

}
