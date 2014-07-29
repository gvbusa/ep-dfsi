package com.pubsubstore.revs.core;

import java.util.Collection;

import com.hazelcast.core.HazelcastInstance;

public interface EventAPI {
	
	public static final String JSON = "application/json";
	public static final String XML = "application/xml";
	public static final String RAW = "application/text";
	
	public void publish(String rawEvent, String classifier, String format, long ttl);

	public void publish(Event event, String format, long ttl);
	
	public void subscribe(EventConsumer eventConsumer, String queue, String binding, boolean durable, int numThreads);

	public void subscribe(RawEventConsumer rawEventConsumer, String queue, String binding, boolean durable, int numThreads);

	public Collection<Event> getEvents(String filter);
	
	public HazelcastInstance getHzClient();
	
	public void authorize(String role);

	public void addUser(String userId, Security user);

	public void deleteUser(String userId);

	public void authenticate(String userId, String password);

	public Event deserialize(String payload, String format);

	public String serialize(Event event, String format);

}
