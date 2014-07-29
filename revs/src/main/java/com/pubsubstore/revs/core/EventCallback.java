package com.pubsubstore.revs.core;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.ProducerTemplate;

public class EventCallback implements EventConsumer {
	private String callbackUrl;
	private String format;
	private ProducerTemplate pt;
	private EventAPI eventAPI;
	
	public EventCallback(String callbackUrl, String format, ProducerTemplate pt, EventAPI eventAPI) {
		this.callbackUrl = callbackUrl;
		this.format = format;
		this.pt = pt;
		this.eventAPI = eventAPI;
	}

	public void onEvent(Event event) {
		// post to callback url
		try {
			Map<String, Object> hdrs = new HashMap<String, Object>();
			hdrs.put("USERID", "admin");
			hdrs.put("PASSWORD", "admin");
			hdrs.put("Exchange.CONTENT_TYPE", format);
			String payload = eventAPI.serialize(event, format);
			pt.sendBodyAndHeaders(callbackUrl, payload, hdrs);
		}
		catch (Exception ex) {
			throw new RevsException(ex);
		}
	}

}
