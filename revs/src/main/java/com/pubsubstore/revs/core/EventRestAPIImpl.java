package com.pubsubstore.revs.core;

import java.io.IOException;
import java.util.Collection;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.http.HttpMessage;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class EventRestAPIImpl implements Processor {

	@Autowired
	protected EventAPI eventAPI;

	@Autowired
	protected ProducerTemplate pt;

	public void init() {
	}

	public void process(Exchange exchange) throws Exception {
		HttpMessage hm = exchange.getIn().getBody(HttpMessage.class);
		if (hm.getRequest().getMethod().equals("OPTIONS")) {
			hm.getResponse().addHeader("Access-Control-Allow-Origin", "*");
			hm.getResponse().addHeader("Access-Control-Allow-Methods", "GET");
			hm.getResponse().addHeader("Access-Control-Allow-Headers", "ACCEPT, USERID,PASSWORD");
			return;
		}
		eventAPI.authenticate((String) hm.getHeader("USERID"),
				(String) hm.getHeader("PASSWORD"));
		String path = hm.getRequest().getPathInfo();
		HttpServletRequest request = hm.getRequest();
		HttpServletResponse response = hm.getResponse();

		response.addHeader("Access-Control-Allow-Origin", "*");

		if (path.endsWith("/events"))
			events(request, response);
		else if (path.endsWith("/publish"))
			publish(exchange, request, response);
		else if (path.endsWith("/subscribe"))
			subscribe(exchange, request, response);
		else if (path.endsWith("/user/add"))
			addUser(exchange, request, response);
		else if (path.endsWith("/user/del"))
			delUser(request, response);
		else if (path.endsWith("/callback"))
			callback(exchange, request, response);

		return;
	}


	private void delUser(HttpServletRequest request,
			HttpServletResponse response) {
		String userId = request.getParameter("userid");
		eventAPI.deleteUser(userId);
	}

	private void addUser(Exchange exchange, HttpServletRequest request,
			HttpServletResponse response) throws JsonParseException,
			JsonMappingException, IOException {
		String rawdata = exchange.getIn().getBody(String.class);
		ObjectMapper mapper = new ObjectMapper();
		Security user = mapper.readValue(rawdata, Security.class);
		String userId = user.getUserId();
		eventAPI.addUser(userId, user);
	}

	private void publish(Exchange exchange, HttpServletRequest request,
			HttpServletResponse response) throws JsonParseException,
			JsonMappingException, IOException, InterruptedException {
		// build event
		String rawdata = exchange.getIn().getBody(String.class);
		ObjectMapper mapper = new ObjectMapper();
		Event e = mapper.readValue(rawdata, Event.class);
		eventAPI.publish(e, EventAPI.JSON, 600);
	}

	private void subscribe(Exchange exchange, HttpServletRequest request,
			HttpServletResponse response) {
		String callback = request.getParameter("callback");
		String format = request.getParameter("format");
		String queue = request.getParameter("queue");
		String binding = request.getParameter("binding");
		EventConsumer ec = new EventCallback(callback, format, pt, eventAPI);
		eventAPI.subscribe(ec, queue, binding, false, 1);
	}

	private void events(HttpServletRequest request, HttpServletResponse response)
			throws IOException {
		String filter = request.getParameter("filter");
		if (filter != null) {
			Collection<Event> events = eventAPI.getEvents(filter);
			ObjectMapper mapper = new ObjectMapper();
			String json = mapper.writeValueAsString(events);
			response.getWriter().print(json);
		}
	}

	private void callback(Exchange exchange, HttpServletRequest request,
			HttpServletResponse response) {
		String rawdata = exchange.getIn().getBody(String.class);
		System.out.println(rawdata);
	}

}
