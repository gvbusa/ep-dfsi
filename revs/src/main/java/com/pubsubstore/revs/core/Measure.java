package com.pubsubstore.revs.core;

import java.io.Serializable;

public class Measure implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -5209768275905315247L;

	private String cube;
	private String hhmm;
	private String dimkey;
	private String metric;
	private long count;
	private float value;

	public String getHhmm() {
		return hhmm;
	}

	public String getMetric() {
		return metric;
	}

	public void setHhmm(String hhmm) {
		this.hhmm = hhmm;
	}

	public void setMetric(String metric) {
		this.metric = metric;
	}

	public Float getValue() {
		return value;
	}

	public void setValue(Float value) {
		this.value = value;
	}

	public String getDimkey() {
		return dimkey;
	}

	public void setDimkey(String dimkey) {
		this.dimkey = dimkey;
	}

	public String getCube() {
		return cube;
	}

	public void setCube(String cube) {
		this.cube = cube;
	}

	public long getCount() {
		return count;
	}

	public void setCount(long count) {
		this.count = count;
	}

}
