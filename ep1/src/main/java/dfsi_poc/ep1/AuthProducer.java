package dfsi_poc.ep1;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.spring.SpringCamelContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.pubsubstore.revs.core.Event;
import com.pubsubstore.revs.core.EventAPI;

public class AuthProducer {

	@Autowired
	protected EventAPI eventAPI;

	private HazelcastInstance hz;

	private IMap<String,Long> dupMap;
	private boolean isFirst = true;
	private long offset = 0;

	@SuppressWarnings("resource")
	public static void main(String[] args) throws Exception {
		ApplicationContext ctx = new ClassPathXmlApplicationContext(
				"auth_producer_applicationContext.xml");
		SpringCamelContext camel = (SpringCamelContext) ctx.getBean("camel");

		// Get exclusive lock and start route
		((EventAPI) ctx.getBean("EventAPI")).getHzClient().getLock("AuthProducer").lock();
		camel.startRoute("fileToBus");
	}
	
	public void init() {
		
		// setup duplicate checking
		hz = eventAPI.getHzClient();
		dupMap = hz.getMap("dupMap");
		dupMap.put("AuthProducer", 0L);
	}
	
	public void onEvent(String row) throws JsonProcessingException {
		// restore state
/*		if (isFirst) {
			if (dupMap.get("AuthProducer") == 0)
				isFirst = false;
			else {
				if (dupMap.get("AuthProducer") > offset) {
					offset++;
					return;
				}
				else
					isFirst = false;
			}
		}
*/
		// parse csv
		String[] fields = row.split(",");
		
		// build event
		Event e = new Event();
		e.setClassifier("authorizer.card.auth.raw");
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
		
		// publish event
		try {
			eventAPI.publish(e, EventAPI.JSON, 600);
			// store state
			//offset++;
			//dupMap.put("AuthProducer", offset);
			//Thread.sleep(10);
		}
		catch (Exception ex) {
		}
		
	}

}
