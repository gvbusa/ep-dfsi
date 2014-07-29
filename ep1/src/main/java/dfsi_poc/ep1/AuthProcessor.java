package dfsi_poc.ep1;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.pubsubstore.revs.core.Event;
import com.pubsubstore.revs.core.EventAPI;
import com.pubsubstore.revs.core.RawEventConsumer;

public class AuthProcessor implements RawEventConsumer {

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
		eventAPI.subscribe(this, "ha.AuthProcessor", "authorizer.card.auth.raw", true, 4);

	}

	@SuppressWarnings("resource")
	public static void main(String[] args) throws Exception {
		new ClassPathXmlApplicationContext(
				"auth_processor_applicationContext.xml");
	}

	public void onEvent(String rawEvent) {
		// parse csv
		String[] fields = rawEvent.split(",");
		
		// build event
		Event e = new Event();
		e.setSelector(fields[5]);
		e.setTs(System.currentTimeMillis());
		Map<String,String> properties = new HashMap<String,String>();
		properties.put("acctNbr", fields[0]);
		properties.put("creditLimit", fields[1]);
		properties.put("posZipCode", fields[2]);
		properties.put("authCcyymmdd", fields[3]);
		properties.put("authHhmmss", fields[4]);
		properties.put("amountRollup", fields[5]);
		e.setProperties(properties);

		// lookup acctNbr
		String acctKey = acctMap.get(e.getProperties().get("acctNbr"));

		// enrich with acctKey
		e.getProperties().put("acctKey", acctKey);

		// publish auth event
		e.setClassifier("authorizer.card.auth");
		eventAPI.publish(e, EventAPI.XML, 600);

	}

}
