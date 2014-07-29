package dfsi_poc.ep1;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.pubsubstore.revs.core.EventAPI;
import com.pubsubstore.revs.core.RevsException;

public class AuthProducer {

	@Autowired
	protected EventAPI eventAPI;

	private static Logger logger = Logger.getLogger(AuthProducer.class.getName());

	@SuppressWarnings("resource")
	public static void main(String[] args) throws Exception {
		new ClassPathXmlApplicationContext(
				"auth_producer_applicationContext.xml");
		logger.info("Started Auth Producer");
	}
	
	public void init() {
	}
	
	public void onEvent(String row) {
		// publish raw event
		try {
			eventAPI.publish(row, "authorizer.card.auth.raw", EventAPI.RAW, 600);
			//Thread.sleep(10);
		}
		catch (Exception ex) {
			throw new RevsException(ex);
		}
		
	}

}
