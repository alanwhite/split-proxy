package xyz.arwhite.net.mux;

import io.helidon.common.buffers.BufferData;
import io.helidon.nima.websocket.WsListener;
import io.helidon.nima.websocket.WsSession;
import java.util.concurrent.PriorityBlockingQueue;
import java.time.Clock;
import java.util.Queue;

public class WsMuxMessageBroker implements WsListener, MessageBroker {

	private Clock clock;
	
	public WsMuxMessageBroker() {
		this(Clock.systemDefaultZone());
	}
	
	protected WsMuxMessageBroker(Clock clock) {
		this.clock = clock;
	}
	
	private WsSession session;
	
	@Override
	public void onOpen(WsSession session) {
		this.session = session;
	}

	/*
	 * Receiving messages from the WebSocket
	 */
	
	private Queue<PriorityQueueEntry> rxQueue = new PriorityBlockingQueue<PriorityQueueEntry>(64);
	private long lastRxMessageTime = 0;
	private int rxSequence = 0;
	
	@Override
	/**
	 * Receives messages from the WebSocket
	 * Creates and stores a PriorityBasedQueue entry and submits
	 */
	public void onMessage(WsSession session, BufferData buffer, boolean last) {

		var now = clock.millis();
		
		if ( now == lastRxMessageTime ) 
			++rxSequence;
		else {
			lastRxMessageTime = now;
			rxSequence = 0;
		}
		
		rxQueue.add(new PriorityQueueEntry((byte) buffer.get(0),now,rxSequence,buffer));
	}

	@Override
	/**
	 * Exposes the received message queue. Messages will be in priority and received order
	 * @return the prioritized receive message queue
	 */
	public Queue<PriorityQueueEntry> getRxQueue() {
		return rxQueue;
	}

	/*
	 * Sending messages to the WebSocket
	 */
	
	private Queue<PriorityQueueEntry> txQueue = new PriorityBlockingQueue<PriorityQueueEntry>(64);
	private long lastTxMessageTime = 0;
	private int txSequence = 0;
	
	public boolean sendMessage(BufferData buffer) {
		
		var now = clock.millis();
		
		if ( now == lastTxMessageTime ) 
			++txSequence;
		else {
			lastTxMessageTime = now;
			txSequence = 0;
		}
		
		return txQueue.add(new PriorityQueueEntry((byte) buffer.get(0),now,txSequence,buffer));
	}
	
	// need something to be listening on the txq, and using session.send, should be started when the session is open, stopped when closed
	
	@Override
	/**
	 * Exposes the underlying transmit message queue. Messages will be in priority and received order
	 * @return the prioritized receive message queue
	 */
	public Queue<PriorityQueueEntry> getTxQueue() {
		return txQueue;
	}





}
