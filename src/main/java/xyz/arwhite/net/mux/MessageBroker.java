package xyz.arwhite.net.mux;

import java.util.Queue;

public interface MessageBroker {

	/**
	 * Provides access to the Queue that consumers can subscribe to for incoming messages
	 * @return
	 */
	public Queue<?> getRxQueue();
	
	/**
	 * Provides access to the Queue that consumers can submit messages to
	 * @return
	 */
	public Queue<?> getTxQueue();
}
