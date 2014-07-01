package dfsi_poc.ep1;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.ProducerTemplate;
import org.apache.camel.spring.SpringCamelContext;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class Rabbit implements ApplicationContextAware {
	private ProducerTemplate pt;
	private Map<String,Object> hdrs;
	private ApplicationContext ctx;
	
	private long pubcount = 0;
	private long subcount = 0;

	@SuppressWarnings("resource")
	public static void main(String[] args) throws Exception {
		
		new ClassPathXmlApplicationContext(
				"rabbit_applicationContext.xml");
	}
	
	public void init() {
		SpringCamelContext camel = (SpringCamelContext) ctx.getBean("camel");
		pt = camel.createProducerTemplate();
		hdrs = new HashMap<String,Object>();
		hdrs.put("rabbitmq.ROUTING_KEY", "raw");
	}
	
	public void publish(String msg) throws Exception {
		try {
			pt.sendBodyAndHeaders("rabbitmq://localhost:5670/web.card.webactivity?username=guest&password=guest", msg, hdrs);
			pubcount++;
			System.out.println("Published:" + pubcount);
		}
		catch (Exception ex) {
			ex.printStackTrace();
		}
		Thread.sleep(100);
	}

	public void consume(String msg) {
		System.out.println(msg);
		subcount++;
		System.out.println("Consumed:" + subcount);
		//Thread.dumpStack();
	}

	public void setApplicationContext(ApplicationContext ctx)
			throws BeansException {
		this.ctx = ctx;
	}
}
