package com.pubsubstore.revs.core;

public interface RawEventConsumer {
	
	public void onEvent(String rawEvent);

}
