package dfsi_poc.ep1;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.pubsubstore.revs.core.Event;
import com.pubsubstore.revs.core.EventAPI;
import com.pubsubstore.revs.core.EventConsumer;

public class WebProcessor implements EventConsumer {

	@Autowired
	protected EventAPI eventAPI;

	private HazelcastInstance hz;

	private IMap<String, Event> map;
	private IMap<String, String> dupMap;

	public void init() {
		hz = eventAPI.getHzClient();
		map = hz.getMap("sessions");
		dupMap = hz.getMap("WebProcessor_DupMap");

		// subscribe to ha.WebProcessor queue
		eventAPI.subscribe(this, "ha.WebProcessor", "web.card.webactivity.raw");
	}

	@SuppressWarnings("resource")
	public static void main(String[] args) throws Exception {
		new ClassPathXmlApplicationContext(
				"web_processor_applicationContext.xml");
	}

	public void timeout() {
		Set<String> keys = map.keySet();
		Event pb = null;
		long now = System.currentTimeMillis();
		for (String key : keys) {
			map.lock(key);
			pb = map.get(key);
			if (pb != null) {
				if ((now - pb.getTs()) > 60 * 1000) {
					pb.getProperties().put("action", "PaymentBroke");
					pb.setSelector("PaymentBroke");
					pb.setClassifier("web.card.payment.breakage");
					pb.setTs(System.currentTimeMillis());
					map.delete(pb.getProperties().get("sessionId"));
					System.out.println("Payment Breakage due to TimeOut!!! "
							+ pb.getProperties().get("sessionId"));
					eventAPI.publish(pb, EventAPI.JSON, 600);
					eventAPI.incrCubeCount("payment", pb.getTs(), "breakages",
							pb.getProperties().get("custId"), 1, 600);
				}
			}
			map.unlock(key);
		}

	}

	public void onEvent(Event event) {
		String sourceId = event.getProperties().get("sourceId");
		if (dupMap.containsKey(sourceId))
			return;

		Event pb = null;
		String sessionId = (String) event.getProperties().get("sessionId");
		String action = (String) event.getProperties().get("action");

		// increment cube
		eventAPI.incrCubeCount("payment", System.currentTimeMillis(), "events", event
				.getProperties().get("custId"), 1, 600);
		if (action.equals("LoggedIn")) {
			eventAPI.incrCubeCount("payment", System.currentTimeMillis(), "sessions", event
					.getProperties().get("custId"), 1, 600);
		}

		dupMap.put(sourceId, sourceId, 5, TimeUnit.MINUTES);

		map.lock(sessionId);

		if (action.equals("PaymentStarted")) {
			event.setTs(System.currentTimeMillis());
			map.put(sessionId, event);
		} else if (action.equals("PaymentCompleted")) {
			map.delete(sessionId);
		} else if (action.equals("LoggedOut")) {
			pb = (Event) map.get(sessionId);
			if (pb != null) {
				System.out.println("Payment Breakage!!! " + sessionId);
				map.delete(sessionId);
				pb.getProperties().put("action", "PaymentBroke");
				pb.setSelector("PaymentBroke");
				pb.setClassifier("web.card.payment.breakage");
				pb.setTs(System.currentTimeMillis());
				eventAPI.publish(pb, EventAPI.JSON, 600);
				eventAPI.incrCubeCount("payment", pb.getTs(), "breakages", pb
						.getProperties().get("custId"), 1, 600);
			}
		}

		map.unlock(sessionId);

	}

}
