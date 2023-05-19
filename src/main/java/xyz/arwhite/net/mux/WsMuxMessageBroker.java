package xyz.arwhite.net.mux;

import io.helidon.common.buffers.BufferData;
import io.helidon.nima.websocket.WsListener;
import io.helidon.nima.websocket.WsSession;
import java.util.concurrent.PriorityBlockingQueue;
import java.time.Clock;
import java.util.Queue;

public class WsMuxMessageBroker implements WsListener, MessageBroker {

	private Clock clock;
	
	private Queue<PriorityQueueEntry> rcvQueue = new PriorityBlockingQueue<PriorityQueueEntry>(64);
	private long lastMessageTime = 0;
	private int sequence = 0;
	
	public WsMuxMessageBroker() {
		this(Clock.systemDefaultZone());
	}
	
	protected WsMuxMessageBroker(Clock clock) {
		this.clock = clock;
	}
	
	@Override
	/**
	 * Receives messages from the WebSocket
	 * Creates and stores a PriorityBasedQueue entry and submits
	 */
	public void onMessage(WsSession session, BufferData buffer, boolean last) {

		var now = clock.millis();
		
		if ( now == lastMessageTime ) 
			++sequence;
		else {
			lastMessageTime = now;
			sequence = 0;
		}
		
		rcvQueue.add(new PriorityQueueEntry((byte) buffer.get(0),now,sequence,buffer));
	}

	@Override
	/**
	 * Exposes the received message queue. Messages will be in priory and received order
	 * @return the prioritised receive message queue
	 */
	public Queue<PriorityQueueEntry> getRxQueue() {
		return rcvQueue;
	}

	@Override
	public Queue<?> getTxQueue() {
		// TODO Auto-generated method stub
		return null;
	}



}
