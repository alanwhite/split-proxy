package xyz.arwhite.net.mux;

import java.util.Queue;

import io.helidon.common.buffers.BufferData;

public interface MessageBroker {

	/**
	 * Provides access to the Queue that consumers must subscribe to for incoming messages
	 * @return
	 */
	public Queue<?> getRxQueue();
	
	/**
	 * Provides raw access to the Queue that consumers can submit messages to
	 * @return
	 */
	public Queue<?> getTxQueue();
	
	/**
	 * Method for submitting messages to the queue
	 * @param buffer
	 * @return
	 */
	public boolean sendMessage(BufferData buffer);
}
