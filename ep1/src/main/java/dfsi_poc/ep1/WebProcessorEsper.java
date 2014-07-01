package dfsi_poc.ep1;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.concurrent.locks.Lock;

import javax.servlet.http.HttpServletResponse;

import org.antlr.stringtemplate.StringTemplate;
import org.apache.camel.Exchange;
import org.apache.camel.component.http.HttpMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.espertech.esper.client.Configuration;
import com.espertech.esper.client.EPAdministrator;
import com.espertech.esper.client.EPRuntime;
import com.espertech.esper.client.EPServiceProvider;
import com.espertech.esper.client.EPServiceProviderManager;
import com.espertech.esper.client.EPStatement;
import com.espertech.esper.client.EventBean;
import com.espertech.esper.client.UpdateListener;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hazelcast.core.HazelcastInstance;
import com.pubsubstore.revs.core.Event;
import com.pubsubstore.revs.core.EventAPI;
import com.pubsubstore.revs.core.EventConsumer;
import com.pubsubstore.revs.core.Measure;

public class WebProcessorEsper implements EventConsumer {

	@Autowired
	protected EventAPI eventAPI;

	private HazelcastInstance hz;
	private String esperq;

	private EPRuntime runtime;
	private EPStatement paymentBreakage;
	private EPStatement eventAgg, sessionAgg, breakageAgg;
	

	public void init() {
		// get hazelcast client for distributed locking
		hz = eventAPI.getHzClient();

		// subscribe to events of interest
		eventAPI.subscribe(this, esperq, "web.card.webactivity.raw");

		// esper engine
		Configuration config = new Configuration();
		config.addEventTypeAutoName("com.pubsubstore.revs.core");
		EPServiceProvider epService = EPServiceProviderManager
				.getDefaultProvider(config);
		EPAdministrator admin = epService.getEPAdministrator();
		runtime = epService.getEPRuntime();

		// define pattern
		StringTemplate st = new StringTemplate(
				"select * from pattern [every a=$paymentStarted$ -> ($loggedOut$ or $timedOut$) and not $paymentCompleted$ and not $paymentStartedRepeated$]");

		st.setAttribute("paymentStarted", "Event(selector='PaymentStarted')");
		st.setAttribute(
				"paymentStartedRepeated",
				"Event(selector='PaymentStarted',properties('sessionId')=a.properties('sessionId'))");
		st.setAttribute(
				"paymentCompleted",
				"Event(selector='PaymentCompleted',properties('sessionId')=a.properties('sessionId'))");
		st.setAttribute("loggedOut",
				"Event(selector='LoggedOut',properties('sessionId')=a.properties('sessionId'))");
		st.setAttribute("timedOut", "timer:interval(60 sec)");
		
		String pattern = st.toString();
		System.out.println(pattern);
		paymentBreakage = admin.createEPL(pattern);
		
		// setup pattern listener
		paymentBreakage.addListener(new PaymentBreakageListener());
		
		// define aggregations
		eventAgg = admin.createEPL("select properties('hhmm') as hhmm, properties('custId') as custId, count(*) as ecount from Event.win:time(10 min) group by properties('hhmm'), properties('custId')");
		sessionAgg = admin.createEPL("select properties('hhmm') as hhmm, properties('custId') as custId, count(*) as scount from Event(selector='LoggedIn').win:time(10 min) group by properties('hhmm'), properties('custId')");
		breakageAgg = admin.createEPL("select properties('hhmm') as hhmm, properties('custId') as custId, count(*) as bcount from Event(selector='PaymentBroke').win:time(10 min) group by properties('hhmm'), properties('custId')");
		
	}

	// main
	@SuppressWarnings("resource")
	public static void main(String[] args) throws Exception {
		new ClassPathXmlApplicationContext(
				"web_processor_esper_applicationContext.xml");
	}

	// subscription callback
	public void onEvent(Event event) {
		// send to esper engine
		event.getProperties().put("hhmm", getHhmm());
		runtime.sendEvent(event);
	}

	// format timestamp
	private String getHhmm() {
		SimpleDateFormat sdf = new SimpleDateFormat("H:m");
		String hhmm = sdf.format(new Date());
		return hhmm;
	}

	// when payment breakage detected, publish event and send to esper engine
	public class PaymentBreakageListener implements UpdateListener {
		public void update(EventBean[] newData, EventBean[] oldData) {

			Lock lock = hz.getLock("esperlock");

			for (EventBean eb : newData) {
				Event e = (Event) eb.get("a");
				e.getProperties().put("action", "PaymentBroke");
				e.setSelector("PaymentBroke");
				e.setClassifier("web.card.payment.breakage");
				e.setTs(System.currentTimeMillis());

				// use distributed lock to ensure that only one instance publishes
				if (lock.tryLock()) {
					try {
						eventAPI.publish(e, EventAPI.XML, 600);
					}
					finally {
						lock.unlock();
					}
				}

				// send to esper engine
				e.getProperties().put("hhmm", getHhmm());
				runtime.sendEvent(e);

			}
		}
	}

	// convert the aggregates into cube for frontend
	public Collection<Measure> getCube() {
		Collection<Measure> cube = new ArrayList<Measure>();
		cube.addAll(getMeasures(eventAgg.iterator(), "events", "ecount"));
		cube.addAll(getMeasures(sessionAgg.iterator(), "sessions", "scount"));
		cube.addAll(getMeasures(breakageAgg.iterator(), "breakages", "bcount"));
		return cube;
	}

	// convert each aggregate into cube
	private Collection<Measure> getMeasures(Iterator<EventBean> i, String metric, String countvar) {		
		Collection<Measure> measures = new ArrayList<Measure>();
		for (Iterator<EventBean> iter = i; iter.hasNext();) {
			EventBean eb = iter.next();
			Measure measure = new Measure();
			measure.setCube("payment");
			measure.setHhmm((String) eb.get("hhmm"));
			measure.setDimkey((String) eb.get("custId"));
			measure.setMetric(metric);
			measure.setCount((Long) eb.get(countvar));
			measures.add(measure);
		}
		return measures;
	}

	// rest api
	public void process(Exchange exchange) throws Exception {
		HttpMessage hm = exchange.getIn().getBody(HttpMessage.class);
		if (hm.getRequest().getMethod().equals("OPTIONS")) {
			hm.getResponse().addHeader("Access-Control-Allow-Origin", "*");
			hm.getResponse().addHeader("Access-Control-Allow-Methods", "GET");
			hm.getResponse().addHeader("Access-Control-Allow-Headers", "ACCEPT, USERID,PASSWORD");
			return;
		}
		String path = hm.getRequest().getPathInfo();
		HttpServletResponse response = hm.getResponse();
		response.addHeader("Access-Control-Allow-Origin", "*");

		if (path.endsWith("/cube")) {
			ObjectMapper mapper = new ObjectMapper();
			String json = mapper.writeValueAsString(getCube());
			response.getWriter().print(json);
		}
		
		return;
	}

	public String getEsperq() {
		return esperq;
	}

	public void setEsperq(String esperq) {
		this.esperq = esperq;
	}

}
