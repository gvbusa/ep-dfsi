package com.pubsubstore.revs.core;

import org.apache.camel.spring.SpringCamelContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class EventServer 
{
	@SuppressWarnings("resource")
	public static void main(String[] args) throws Exception {
		ApplicationContext ctx = new ClassPathXmlApplicationContext("eventserver_applicationContext.xml");
        SpringCamelContext camel = (SpringCamelContext) ctx.getBean("camel");
        camel.start();
	}
}
