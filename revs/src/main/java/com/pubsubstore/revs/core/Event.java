package com.pubsubstore.revs.core;

import java.io.Serializable;
import java.util.Map;

import org.apache.commons.lang3.builder.HashCodeBuilder;

public class Event implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 6501506265598905722L;

	private String classifier;
	private String selector;
	private long ts;
	private Map<String, String> properties;

	public String getClassifier() {
		return classifier;
	}

	public void setClassifier(String classifier) {
		this.classifier = classifier;
	}

	public String getSelector() {
		return selector;
	}

	public void setSelector(String selector) {
		this.selector = selector;
	}

	public long getTs() {
		return ts;
	}

	public void setTs(long ts) {
		this.ts = ts;
	}

	public Map<String, String> getProperties() {
		return properties;
	}

	public void setProperties(Map<String, String> properties) {
		this.properties = properties;
	}

	public int hashCode() {
		return HashCodeBuilder.reflectionHashCode(this);
	}

}
