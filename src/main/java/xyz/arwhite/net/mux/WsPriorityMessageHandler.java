package xyz.arwhite.net.mux;

import java.time.Clock;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.PriorityBlockingQueue;

import io.helidon.common.buffers.BufferData;
import io.helidon.nima.websocket.WsSession;

public class WsPriorityMessageHandler extends MessageLinkAdapter {

	private Clock clock;
	
	public WsPriorityMessageHandler() {
		this(Clock.systemDefaultZone());
	}
	
	protected WsPriorityMessageHandler(Clock clock) {
		this.clock = clock;
	}
	
	private WsSession session;
	private Thread txQueueSender;
	private CompletableFuture<Void> txStopped;
	
	@Override
	public void onOpen(WsSession session) {
		this.session = session;
		
		CompletableFuture<Void> started = new CompletableFuture<>();
		txStopped = new CompletableFuture<>();

		txQueueSender = Thread.ofVirtual().start(() -> {
			try {
				started.complete(null);
				while(true) {
					var qe = txQueue.take();
					
					if ( qe.priority() == 0 ) {
						
						// drain the queue
						while( !txQueue.isEmpty() ) {
							var de = txQueue.poll();
							WsPriorityMessageHandler.this.session.send(de.message(), true);
						}
						
						// inform the world
						txStopped.complete(null);
						
					} else
						WsPriorityMessageHandler.this.session.send(qe.message(), true);
				} 
			} catch(InterruptedException e) {}
		});
			
        try {
            started.toCompletableFuture().get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
	}
	
	@Override
	public void onClose(WsSession session, int status, String reason) {
		if ( txQueueSender != null )
			txQueueSender.interrupt();
		
		txQueueSender = null;
	}

	/*
	 * Receiving messages from the WebSocket
	 */
	
	private PriorityBlockingQueue<PriorityQueueEntry> rxQueue = new PriorityBlockingQueue<PriorityQueueEntry>(64);
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
	
	private PriorityBlockingQueue<PriorityQueueEntry> txQueue = new PriorityBlockingQueue<PriorityQueueEntry>(64);
	private long lastTxMessageTime = 0;
	private int txSequence = 0;
	private boolean draining = false;
	
	/**
	 * Used to queue a message to be transmitted. Priority is specified in the first byte of the BufferData.
	 * Priority 0 is reserved for system use, this method will reject messages with priority 0.
	 */
	@Override
	public boolean sendMessage(BufferData buffer) {
		
		if ( draining )
			return false;
		
		if ( buffer.get(0) == 0 )
			return false;
		
		var now = clock.millis();
		
		if ( now == lastTxMessageTime ) 
			++txSequence;
		else {
			lastTxMessageTime = now;
			txSequence = 0;
		}
		
		return txQueue.add(new PriorityQueueEntry((byte) buffer.get(0),now,txSequence,buffer));
	}
	
	/**
	 * Prevents submission of new messages to the transmit queue, and waits for the queue to be drained
	 */
	public void drainTxQueue() {
		draining = true;

		if ( txStopped != null ) {
			var command = BufferData.create(1);
			command.writeInt8(0);
			txQueue.add(new PriorityQueueEntry((byte) 0,clock.millis(),0,command));

			try {
				txStopped.get();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	}

	@Override
	/**
	 * Exposes the underlying transmit message queue. Messages will be in priority and received order
	 * @return the prioritized receive message queue
	 */
	public Queue<PriorityQueueEntry> getTxQueue() {
		return txQueue;
	}

	@Override
	public void stop() {
		// TODO Auto-generated method stub
		
	}

}
