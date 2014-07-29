package dfsi_poc.ep1;

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

public class ODSSubscriber implements EventConsumer, ApplicationContextAware {

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
		eventAPI.subscribe(this, "ha.ods", "authorizer.card.auth", true, 4);

	}

	@SuppressWarnings("resource")
	public static void main(String[] args) throws Exception {
		new ClassPathXmlApplicationContext(
				"ods_subscriber_applicationContext.xml");
	}

	public void onEvent(Event event) {
		// send to ods
		pt.sendBody("sql:insert into auths (acctNbr, acctKey, amountRollup) values (:#acctNbr, :#acctKey, :#amountRollup)", event.getProperties());
	}

	public void setApplicationContext(ApplicationContext ctx)
			throws BeansException {
		this.ctx =ctx;
	}

}
