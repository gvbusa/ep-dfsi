package com.pubsubstore.revs.core;

import java.util.Collection;

import com.hazelcast.core.HazelcastInstance;

public interface EventAPI {
	
	public static final String JSON = "application/json";
	public static final String XML = "application/xml";
	
	public void publish(Event event, String format, long ttl);
	
	public void subscribe(EventConsumer eventConsumer, String queue, String binding);
	
	public Collection<Event> getEvents(String filter);
	
	public HazelcastInstance getHzClient();
	
	public void incrCubeValue(String cube, long ts, String metric, String dimkey, float value, long ttl);

	public void incrCubeCount(String cube, long ts, String metric, String dimkey, long count, long ttl);
	
	public Collection<Measure> getCube(String filter);

	public void authorize(String role);

	public void addUser(String userId, Security user);

	public void deleteUser(String userId);

	public void authenticate(String userId, String password);

}
