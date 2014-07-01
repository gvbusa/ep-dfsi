package dfsi_poc.ep1;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.spring.SpringCamelContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.fasterxml.jackson.core.JsonProcessingException;
//import com.hazelcast.core.HazelcastInstance;
//import com.hazelcast.core.IMap;
import com.pubsubstore.revs.core.Event;
import com.pubsubstore.revs.core.EventAPI;

public class WebProducer {

	@Autowired
	protected EventAPI eventAPI;

	//private HazelcastInstance hz;

	//private IMap<String,Long> dupMap;
	//private boolean isFirst = true;
	//private long offset = 0;

	@SuppressWarnings("resource")
	public static void main(String[] args) throws Exception {
		ApplicationContext ctx = new ClassPathXmlApplicationContext(
				"web_producer_applicationContext.xml");
		SpringCamelContext camel = (SpringCamelContext) ctx.getBean("camel");

		// Get exclusive lock and start route
		((EventAPI) ctx.getBean("EventAPI")).getHzClient().getLock("WebProducer").lock();
		camel.startRoute("fileToBus");
	}
	
	public void init() {
		
		// setup duplicate checking
		//hz = eventAPI.getHzClient();
		//dupMap = hz.getMap("dupMap");
		//dupMap.put("WebProducer", 0L);
	}
	
	public void onEvent(String row) throws JsonProcessingException, InterruptedException {
		// restore state
/*		if (isFirst) {
			if (dupMap.get("WebProducer") == 0)
				isFirst = false;
			else {
				if (dupMap.get("WebProducer") > offset) {
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
		e.setClassifier("web.card.webactivity.raw");
		e.setSelector(fields[5]);
		e.setTs(System.currentTimeMillis());
		Map<String,String> properties = new HashMap<String,String>();
		properties.put("sessionId", fields[3]);
		properties.put("custId", fields[4]);
		properties.put("action", fields[5]);
		e.setProperties(properties);
		
		// publish event
		eventAPI.publish(e, EventAPI.JSON, 600);
		// store state
		// offset++;
		// dupMap.put("WebProducer", offset);
		Thread.sleep(10);
		
	}

}
