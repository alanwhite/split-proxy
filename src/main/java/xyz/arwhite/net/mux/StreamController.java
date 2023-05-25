package xyz.arwhite.net.mux;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import io.helidon.common.buffers.BufferData;

public class StreamController {

	private static final int NEW_STREAM_QUEUE_DEPTH = 16;
	
	public static final byte CONNECT_REQUEST = 1;
	public static final byte CONNECT_CONFIRM = 2;
	public static final byte CONNECT_FAIL = 3;
	public static final byte CLOSE = 4;
	public static final byte DATA = 5;
	public static final byte BUFINC = 6;

	private MessageBroker broker;
	private Thread messageReaderThread;
	
	private record ConnectRequest(int priority, int remoteId, BufferData buffer ) {};
	private record ConnectResponse(int priority, int localId, int remoteId) {};
	
	private ArrayBlockingQueue<ConnectRequest> newStreamQueue;
	private StreamMap streams;
	
	/**
	 * Sets up receiver on provided message broker
	 * 
	 * @param broker
	 */
	public StreamController(MessageBroker broker) {
		this.broker = broker;
		
		streams = new StreamMap();
		
		setupBroker(this.broker);
	}

	@SuppressWarnings("unchecked")
	/**
	 * Note: assumes that the Queue in the MessageBroker is a BlockingQueue
	 * @param broker
	 */
	private void setupBroker(MessageBroker broker) {

		newStreamQueue = new ArrayBlockingQueue<>(NEW_STREAM_QUEUE_DEPTH);

		messageReaderThread = Thread.ofVirtual().start(
				new MessageReader(
						(BlockingQueue<BufferData>) broker.getRxQueue(),
						newStreamQueue));

	}

	/**
	 * Assumed message format:
	 * byte 0 is priority
	 * byte 1 is the stream id
	 * byte 2 is the message type
	 * byte 3+ is data
	 * 
	 * @author Alan R. White
	 *
	 */
	private class MessageReader implements Runnable {

		private BlockingQueue<BufferData> rxQueue;
		private BlockingQueue<ConnectRequest> newStreamQueue;

		public MessageReader(
				BlockingQueue<BufferData> rxQueue, 
				BlockingQueue<ConnectRequest> newStreamQueue) {
			
			this.rxQueue = rxQueue;
			this.newStreamQueue = newStreamQueue;
		}

		@Override
		public void run() {
			try {
				while(true) {
					var buffer = rxQueue.take();
					var command = buffer.get(2);
					
					if ( command == CONNECT_REQUEST ) 
						newStreamQueue.add(new ConnectRequest(buffer.get(0), buffer.get(1), buffer));
					else {
						// dispatch to stream
					}
					
					
				}
				
			} catch (InterruptedException e) {
				// TODO: maybe interruption is OK, means terminate 
				throw new RuntimeException(e);
			}

		}

	}

	/*
	 * Protocol is when connecting, create the local id, send it in the CR
	 * Receiver sees this as the remote id, creates a local id and sends it back
	 * in the CC.
	 * Each side then supplies their view of the remote id in messages sent
	 * Receiver uses this to look up the Stream object.
	 * 	 
	 */
	
	/**
	 * Blocks awaiting a new stream request from the message broker
	 * @throws IOException 
	 */
	public Stream accept() throws IOException {
		
		try {
			var connectRequest = newStreamQueue.take();
					
			int localStreamId = streams.allocNewStreamId();
					
			// create Stream object for this connection, containing the local and remote streamIds
			var stream = new Stream(localStreamId, connectRequest.remoteId, connectRequest.priority);
			
			// add entry to Streams map 
			streams.put(Integer.valueOf(localStreamId), stream); 
			
			// send a response to the originator saying connect accepted, here's my streamId
			var connectResponse = BufferData.create(16);
			connectResponse.writeInt8(connectRequest.priority);
			connectResponse.writeInt8(connectRequest.remoteId);
			connectResponse.writeInt8(CONNECT_CONFIRM);
			connectResponse.writeInt8(localStreamId);
			broker.sendMessage(connectResponse);
			
			// create Stream object for this connection, containing the local and remote streamIds
			
			// add entry to Streams map 
			
			
			return stream;
			
		} catch (InterruptedException e) {
			throw ( new RuntimeException(e) );
		}

	}

	/**
	 * Creates a new stream across the WebSocket
	 * Specify required priority
	 * 
	 * @return
	 */
	public Stream connect() {
		
		// create a stream object, file it with local id
		// make CR
		// get remote id from CC, update stream
		
		/// CC recvd over ws msg queue for this Stream, self-updates the stream object
		
		return null;
	}

}
