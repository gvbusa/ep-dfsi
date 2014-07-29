package dfsi_poc.ep1;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.ProducerTemplate;
import org.apache.camel.spring.SpringCamelContext;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.pubsubstore.revs.core.Event;
import com.pubsubstore.revs.core.EventAPI;
import com.pubsubstore.revs.core.EventConsumer;
import com.pubsubstore.revs.core.RevsException;

public class SettlementsSubscriber implements EventConsumer, ApplicationContextAware {

	@Autowired
	protected EventAPI eventAPI;
	
	private ProducerTemplate pt;
	private ApplicationContext ctx;
	private SpringCamelContext camel;

	public void init() {

		// set up producer template
		camel = (SpringCamelContext) ctx.getBean("camel");
		pt = camel.createProducerTemplate();

		// subscribe to ha.ods queue
		eventAPI.subscribe(this, "ha.settlements", "authorizer.card.auth", true, 4);

	}

	@SuppressWarnings("resource")
	public static void main(String[] args) throws Exception {
		new ClassPathXmlApplicationContext(
				"settlements_subscriber_applicationContext.xml");
	}

	public void onEvent(Event event) {
		// http post
		try {
			Map<String, Object> hdrs = new HashMap<String, Object>();
			hdrs.put("USERID", "admin");
			hdrs.put("PASSWORD", "admin");
			hdrs.put("Exchange.CONTENT_TYPE", EventAPI.JSON);
			String payload = eventAPI.serialize(event, EventAPI.JSON);
			pt.sendBodyAndHeaders("http://localhost:9090/core/callback", payload, hdrs);
		}
		catch (Exception ex) {
			throw new RevsException(ex);
		}
	}

	public void setApplicationContext(ApplicationContext ctx)
			throws BeansException {
		this.ctx =ctx;
	}

}
