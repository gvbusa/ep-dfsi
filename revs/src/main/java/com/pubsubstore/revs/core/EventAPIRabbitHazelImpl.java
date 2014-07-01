package com.pubsubstore.revs.core;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spring.SpringCamelContext;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hazelcast.config.InMemoryFormat;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.core.MapStore;
import com.hazelcast.query.SqlPredicate;
import com.thoughtworks.xstream.XStream;

public class EventAPIRabbitHazelImpl implements EventAPI,
		ApplicationContextAware {

	@Autowired
	protected HazelcastInstance hz;

	public HazelcastInstance getHzClient() {
		// security check
		authorize("cache");
		return hz;
	}

	private boolean isServer;
	private IMap<String, Event> estore;
	private IMap<String, Security> secMap;
	private IMap<Dimension, Measure> cubeMap;
	private Security clientSecurity;

	private ProducerTemplate pt;
	private ApplicationContext ctx;
	private SpringCamelContext camel;

	private String userId;
	private String password;

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	private static final ThreadLocal<Security> secContext = new ThreadLocal<Security>();
	private static XStream xstream;

	private ExecutorService publisher = Executors.newFixedThreadPool(1);

	public void init() {
		estore = hz.getMap("event_store");
		secMap = hz.getMap("event_security");
		cubeMap = hz.getMap("event_cube");

		camel = (SpringCamelContext) ctx.getBean("camel");

		// setup rabbitmq
		pt = camel.createProducerTemplate();

		// setup xstream
		xstream = new XStream();
		xstream.registerConverter(new MapEntryConverter());
	}

	public void initServer() {
		isServer = true;
		init();

		// event_cube
		hz.getConfig().getMapConfig("event_cube")
				.setInMemoryFormat(InMemoryFormat.OBJECT);
		cubeMap.addIndex("cube", false);
		cubeMap.addIndex("hhmm", true);
		cubeMap.addIndex("dimkey", false);
		cubeMap.addIndex("metric", false);

		securityServer();
		load();
	}

	private void securityServer() {
		Security adminSecurity = new Security();
		adminSecurity.setUserId("admin");
		adminSecurity.setPassword("admin");
		Map<String, String> roleMap = new HashMap<String, String>();
		roleMap.put("admin", "admin");
		roleMap.put("publish", "admin");
		roleMap.put("subscribe", "admin");
		roleMap.put("query", "admin");
		roleMap.put("cache", "admin");
		adminSecurity.setRoleMap(roleMap);
		secMap.putIfAbsent("admin", adminSecurity);
	}

	public void initClient() {
		isServer = false;
		init();
		securityClient();
	}

	private void securityClient() {
		if (!secMap.containsKey(userId))
			throw new RevsException("UserId: " + userId + " does not exist");

		clientSecurity = secMap.get(userId);
		if (isServer)
			secContext.set(clientSecurity);

		if (!clientSecurity.getPassword().equals(password))
			throw new RevsException("UserId: " + userId
					+ " could not be authenticated");
	}

	@SuppressWarnings("unchecked")
	private void load() {
		MapStore<String, Event> ems = (MapStore<String, Event>) hz.getConfig()
				.getMapConfig("event_store").getMapStoreConfig()
				.getImplementation();
		Set<String> ekeys = ems.loadAllKeys();
		for (String key : ekeys) {
			estore.put(key, ems.load(key));
		}
		MapStore<String, Security> secms = (MapStore<String, Security>) hz
				.getConfig().getMapConfig("event_security").getMapStoreConfig()
				.getImplementation();
		Set<String> seckeys = secms.loadAllKeys();
		for (String key : seckeys) {
			secMap.put(key, secms.load(key));
		}

	}

	public void authenticate(String userId, String password) {
		if (userId == null || password == null)
			throw new RevsException("No credentials found in request header");

		this.userId = userId;
		this.password = password;

		securityClient();
	}

	public void publish(Event event, String format, long ttl) {
		authorize("publish");
		publisher.execute(new PublishTask(event, format, ttl));
	}

	private class PublishTask implements Runnable {
		Event event;
		String format;
		long ttl;
		
		public PublishTask(Event event, String format, long ttl) {
			this.event = event;
			this.format = format;
			this.ttl = ttl;
		}

		public void run() {
			// publish event
			try {

				Map<String, Object> hdrs = new HashMap<String, Object>();
				hdrs.put("rabbitmq.ROUTING_KEY", event.getClassifier());
				hdrs.put("rabbitmq.DELIVERY_MODE", 2);
				hdrs.put("rabbitmq.CONTENT_TYPE", format);
				hdrs.put("rabbitmq.EXPIRATION", ttl * 1000);

				// serialize
				String key = UUID.randomUUID().toString();
				event.getProperties().put("sourceId", key);
				String payload = serialize(event, format);

				// send to rabbitmq
				pt.sendBodyAndHeaders(
						"rabbitmq://localhost:5670/ha.EventBus?autoDelete=false&exchangeType=topic&username=guest&password=guest",
						payload, hdrs);

				// send to event store
				//estore.put(key, event, ttl, TimeUnit.SECONDS);

			} catch (Exception ex) {
				throw new RevsException("Unable to publish event", ex);
			}
			
		}
		
	}
	
	
	public void authorize(String role) {
		if (isServer)
			clientSecurity = secContext.get();
		if (!clientSecurity.getRoleMap().containsKey(role))
			throw new RevsException("UserId: " + clientSecurity.getUserId()
					+ " not authorised for role: " + role);
	}

	public void subscribe(EventConsumer eventConsumer, String queue,
			String binding) {
		// security check
		authorize("subscribe");
		
		String from = "";
		from = "rabbitmq://localhost:5670/ha.EventBus?routingKey="
			+ binding
			+ "&username=guest&password=guest&exchangeType=topic&autoDelete=false&threadPoolSize=1&autoAck=false&queue="
			+ queue;

		try {
			camel.addRoutes(new MyDynamcRouteBuilder(camel, from, eventConsumer));
		} catch (Exception e) {
			throw new RevsException(e);
		}

	}

	public void addUser(String userId, Security user) {
		authorize("admin");
		secMap.put(userId, user);
	}

	public void deleteUser(String userId) {
		authorize("admin");
		secMap.delete(userId);
	}

	public void setApplicationContext(ApplicationContext ctx)
			throws BeansException {
		this.ctx = ctx;
	}

	private static final class MyDynamcRouteBuilder extends RouteBuilder {
		private final String from;
		private final EventConsumer eventConsumer;

		private MyDynamcRouteBuilder(CamelContext camel, String from,
				EventConsumer eventConsumer) {
			super(camel);
			this.from = from;
			this.eventConsumer = eventConsumer;
		}

		@Override
		public void configure() throws Exception {
			from(from).process(new Processor() {
				public void process(Exchange exchange) throws Exception {
					String payload = exchange.getIn().getBody(String.class);
					String format = (String) exchange.getIn().getHeader(
							"rabbitmq.CONTENT_TYPE");
					Event event = deserialize(payload, format);
					eventConsumer.onEvent(event);
				}

			});
		}
	}

	public static Event deserialize(String payload, String format)
			throws JsonParseException, JsonMappingException, IOException {
		if (format.equals(JSON)) {
			ObjectMapper mapper = new ObjectMapper();
			return mapper.readValue(payload, Event.class);
		} else if (format.equals(XML)) {
			return (Event) xstream.fromXML(payload);
		}

		return null;
	}

	public static String serialize(Event event, String format)
			throws JsonProcessingException {
		if (format.equals(JSON)) {
			ObjectMapper mapper = new ObjectMapper();
			return mapper.writeValueAsString(event);
		} else if (format.equals(XML)) {
			return xstream.toXML(event);
		}

		return null;
	}

	public void incrCubeValue(String cube, long ts, String metric,
			String dimkey, float value, long ttl) {
		authorize("cache");

		SimpleDateFormat sdf = new SimpleDateFormat("H:m");
		String hhmm = sdf.format(new Date(ts));

		Dimension dim = new Dimension();
		dim.setCube(cube);
		dim.setHhmm(hhmm);
		dim.setDimkey(dimkey);
		dim.setMetric(metric);

		Measure measure = new Measure();
		measure.setCube(cube);
		measure.setHhmm(hhmm);
		measure.setDimkey(dimkey);
		measure.setMetric(metric);

		cubeMap.lock(dim);
		if (cubeMap.containsKey(dim)) {
			float sum = cubeMap.get(dim).getValue();
			sum = sum + value;
			measure.setValue(sum);
			cubeMap.put(dim, measure);
		} else {
			measure.setValue(value);
			cubeMap.put(dim, measure, ttl, TimeUnit.SECONDS);
		}
		cubeMap.unlock(dim);

	}

	public void incrCubeCount(String cube, long ts, String metric,
			String dimkey, long count, long ttl) {
		authorize("cache");

		SimpleDateFormat sdf = new SimpleDateFormat("H:m");
		String hhmm = sdf.format(new Date(ts));

		Dimension dim = new Dimension();
		dim.setCube(cube);
		dim.setHhmm(hhmm);
		dim.setDimkey(dimkey);
		dim.setMetric(metric);

		Measure measure = new Measure();
		measure.setCube(cube);
		measure.setHhmm(hhmm);
		measure.setDimkey(dimkey);
		measure.setMetric(metric);

		cubeMap.lock(dim);
		if (cubeMap.containsKey(dim)) {
			long sum = cubeMap.get(dim).getCount();
			sum = sum + count;
			measure.setCount(sum);
			cubeMap.put(dim, measure);
		} else {
			measure.setCount(count);
			cubeMap.put(dim, measure, ttl, TimeUnit.SECONDS);
		}
		cubeMap.unlock(dim);

	}

	public Collection<Measure> getCube(String filter) {
		authorize("query");
		return cubeMap.values(new SqlPredicate(filter));
	}

	public Collection<Event> getEvents(String filter) {
		authorize("query");
		return estore.values(new SqlPredicate(filter));
	}

}
