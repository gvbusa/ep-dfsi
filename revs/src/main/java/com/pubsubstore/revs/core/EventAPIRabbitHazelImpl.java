package com.pubsubstore.revs.core;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spring.SpringCamelContext;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.core.MapStore;
import com.hazelcast.query.SqlPredicate;
import com.thoughtworks.xstream.XStream;

public class EventAPIRabbitHazelImpl implements EventAPI,
		ApplicationContextAware {

	private String userId;
	private String password;
	protected HazelcastInstance hz;
	protected String rabbitHost;
	protected String rabbitPort;
	protected String rabbitUser;
	protected String rabbitPass;

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public void setHz(HazelcastInstance hz) {
		this.hz = hz;
	}

	public void setRabbitHost(String rabbitHost) {
		this.rabbitHost = rabbitHost;
	}

	public void setRabbitPort(String rabbitPort) {
		this.rabbitPort = rabbitPort;
	}

	public void setRabbitUser(String rabbitUser) {
		this.rabbitUser = rabbitUser;
	}

	public void setRabbitPass(String rabbitPass) {
		this.rabbitPass = rabbitPass;
	}


	public HazelcastInstance getHzClient() {
		// security check
		authorize("cache");
		return hz;
	}

	private boolean isServer;
	private IMap<String, Event> estore;
	private IMap<String, Security> secMap;
	private Security clientSecurity;

	private ProducerTemplate pt;
	private ApplicationContext ctx;
	private SpringCamelContext camel;


	private static final ThreadLocal<Security> secContext = new ThreadLocal<Security>();
	private static XStream xstream;

	private ExecutorService publisher = Executors.newFixedThreadPool(1);
	private String rabbitUrl;
	
	public void init() {
		estore = hz.getMap("event_store");
		secMap = hz.getMap("event_security");

		camel = (SpringCamelContext) ctx.getBean("camel");

		// setup rabbitmq
		pt = camel.createProducerTemplate();
		rabbitUrl = "rabbitmq://" + rabbitHost + ":" + rabbitPort + "/ha.EventBus?exchangeType=topic&username=" + rabbitUser + "&password=" + rabbitPass;

		// setup xstream
		xstream = new XStream();
		xstream.registerConverter(new MapEntryConverter());
	}

	public void initServer() {
		isServer = true;
		init();

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

	public void publish(String rawEvent, String classifier, String format, long ttl) {
		authorize("publish");
		publisher.execute(new RawPublishTask(rawEvent, classifier, format, ttl));
	}

	private class RawPublishTask implements Runnable {
		String rawEvent;
		String classifier;
		String format;
		long ttl;
		
		public RawPublishTask(String rawEvent, String classifier, String format, long ttl) {
			this.rawEvent = rawEvent;
			this.classifier = classifier;
			this.format = format;
			this.ttl = ttl;
		}

		public void run() {
			// publish event
			try {

				Map<String, Object> hdrs = new HashMap<String, Object>();
				hdrs.put("rabbitmq.ROUTING_KEY", classifier);
				hdrs.put("rabbitmq.DELIVERY_MODE", 1);
				hdrs.put("rabbitmq.CONTENT_TYPE", format);
				hdrs.put("rabbitmq.EXPIRATION", ttl * 1000);

				// send to rabbitmq
				pt.sendBodyAndHeaders(
						rabbitUrl + "&autoDelete=false",
						rawEvent, hdrs);

				// send to event store
				//estore.put(key, event, ttl, TimeUnit.SECONDS);

			} catch (Exception ex) {
				throw new RevsException("Unable to publish event", ex);
			}
			
		}
		
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
						rabbitUrl + "&autoDelete=false",
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
			String binding, boolean durable, int numThreads) {
		// security check
		authorize("subscribe");
		
		String from = "";
		from = rabbitUrl + "&routingKey="
			+ binding
			+ "&threadPoolSize=" + numThreads + "&autoAck=false&queue="
			+ queue + "&autoDelete=" + Boolean.toString(!durable);

		try {
			camel.addRoutes(new EventConsumerRouteBuilder(camel, from, eventConsumer, this));
		} catch (Exception e) {
			throw new RevsException(e);
		}

	}

	public void subscribe(RawEventConsumer rawEventConsumer, String queue,
			String binding, boolean durable, int numThreads) {
		// security check
		authorize("subscribe");
		
		String from = "";
		from = rabbitUrl + "&routingKey="
			+ binding
			+ "&threadPoolSize=" + numThreads + "&autoAck=false&queue="
			+ queue + "&autoDelete=" + Boolean.toString(!durable);

		try {
			camel.addRoutes(new RawEventConsumerRouteBuilder(camel, from, rawEventConsumer));
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

	private static final class EventConsumerRouteBuilder extends RouteBuilder {
		private final String from;
		private final EventConsumer eventConsumer;
		private final EventAPI eventAPI;

		private EventConsumerRouteBuilder(CamelContext camel, String from,
				EventConsumer eventConsumer, EventAPI eventAPI) {
			super(camel);
			this.from = from;
			this.eventConsumer = eventConsumer;
			this.eventAPI = eventAPI;
		}

		@Override
		public void configure() throws Exception {
			from(from).process(new Processor() {
				public void process(Exchange exchange) throws Exception {
					String payload = exchange.getIn().getBody(String.class);
					String format = (String) exchange.getIn().getHeader(
							"rabbitmq.CONTENT_TYPE");
					Event event = eventAPI.deserialize(payload, format);
					eventConsumer.onEvent(event);
				}

			});
		}
	}

	private static final class RawEventConsumerRouteBuilder extends RouteBuilder {
		private final String from;
		private final RawEventConsumer rawEventConsumer;

		private RawEventConsumerRouteBuilder(CamelContext camel, String from,
				RawEventConsumer rawEventConsumer) {
			super(camel);
			this.from = from;
			this.rawEventConsumer = rawEventConsumer;
		}

		@Override
		public void configure() throws Exception {
			from(from).process(new Processor() {
				public void process(Exchange exchange) throws Exception {
					String payload = exchange.getIn().getBody(String.class);
					rawEventConsumer.onEvent(payload);
				}

			});
		}
	}

	public Event deserialize(String payload, String format) {
		if (format.equals(JSON)) {
			ObjectMapper mapper = new ObjectMapper();
			try {
				return mapper.readValue(payload, Event.class);
			} catch (Exception e) {
				throw new RevsException(e);
			}
		} else if (format.equals(XML)) {
			return (Event) xstream.fromXML(payload);
		}

		return null;
	}

	public String serialize(Event event, String format) {
		if (format.equals(JSON)) {
			ObjectMapper mapper = new ObjectMapper();
			try {
				return mapper.writeValueAsString(event);
			} catch (Exception e) {
				throw new RevsException(e);
			}
		} else if (format.equals(XML)) {
			return xstream.toXML(event);
		}

		return null;
	}

	public Collection<Event> getEvents(String filter) {
		authorize("query");
		return estore.values(new SqlPredicate(filter));
	}

}
