package com.pubsubstore.revs.core;

public class RevsException extends RuntimeException {

	public RevsException(Throwable t) {
		this.addSuppressed(t);
	}

	public RevsException(String msg) {
		this.addSuppressed(new Exception(msg));
	}

	public RevsException(String msg, Throwable t) {
		this.addSuppressed(t);
		this.addSuppressed(new Exception(msg));
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = -5954672794619650449L;

}
