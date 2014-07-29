package dfsi_poc.ep1;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.pubsubstore.revs.core.EventAPI;
import com.pubsubstore.revs.core.RevsException;
//import com.hazelcast.core.HazelcastInstance;
//import com.hazelcast.core.IMap;

public class WebProducer {

	@Autowired
	protected EventAPI eventAPI;

	@SuppressWarnings("resource")
	public static void main(String[] args) throws Exception {
		new ClassPathXmlApplicationContext(
				"web_producer_applicationContext.xml");
	}
	
	public void init() {
	}
	
	public void onEvent(String row) throws JsonProcessingException, InterruptedException {
		// publish raw event
		try {
			eventAPI.publish(row, "web.card.webactivity.raw", EventAPI.RAW, 600);
			Thread.sleep(10);
		}
		catch (Exception ex) {
			throw new RevsException(ex);
		}
		
	}

}
