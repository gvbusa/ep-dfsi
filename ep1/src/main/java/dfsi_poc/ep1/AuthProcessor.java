package dfsi_poc.ep1;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.pubsubstore.revs.core.Event;
import com.pubsubstore.revs.core.EventAPI;
import com.pubsubstore.revs.core.EventConsumer;
import com.pubsubstore.revs.core.Measure;

public class AuthProcessor implements EventConsumer {

	@Autowired
	protected EventAPI eventAPI;

	private HazelcastInstance hz;

	IMap<String, String> acctMap;

	public void init() {
		hz = eventAPI.getHzClient();
		acctMap = hz.getMap("account_map");

		// populate acctMap
		for (int i = 10000; i < 20000; i++) {
			acctMap.put(Integer.toString(i), Integer.toString(i + 10000));
		}

		// subscribe to ha.AuthProcessor queue
		eventAPI.subscribe(this, "ha.AuthProcessor", "authorizer.card.auth.raw");

	}

	@SuppressWarnings("resource")
	public static void main(String[] args) throws Exception {
		new ClassPathXmlApplicationContext(
				"auth_processor_applicationContext.xml");
	}

	public void onEvent(Event event) {
		// lookup acctNbr
		String acctKey = acctMap.get(event.getProperties().get("acctNbr"));

		// enrich with acctKey
		event.getProperties().put("acctKey", acctKey);

		// publish auth event
		event.setClassifier("authorizer.card.auth");
		eventAPI.publish(event, EventAPI.JSON, 600);

		// increment cube
		eventAPI.incrCubeValue("authsum", event.getTs(), "authsum", acctKey, Float.parseFloat((String) event.getProperties().get("amountRollup")), 600);

		// check for auth sum exceeding 1000 in past 10 minutes
		float authSum = getAuthSum(acctKey);
		if (authSum > 1000.0) {
			event.setClassifier("authorizer.card.auth.exceeded");
			event.getProperties().put("authSum", Float.toString(authSum));
			eventAPI.publish(event, EventAPI.XML, 600);
		}

	}

	private float getAuthSum(String acctKey) {
		float authSum = 0.0F;
		for (Measure measure : eventAPI.getCube("cube='authsum' AND metric='authsum' AND dimkey='" + acctKey + "'")) {
			authSum += measure.getValue();
		}
		return authSum;
	}


}
