package xyz.arwhite.net.mux;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

import javax.naming.LimitExceededException;

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

	private record ConnectRequest(int priority, int remoteId, int streamPort, BufferData buffer ) {};
	private record ConnectResponse(int priority, int localId, int remoteId) {};

	private ArrayBlockingQueue<ConnectRequest> connectRequests = new ArrayBlockingQueue<>(NEW_STREAM_QUEUE_DEPTH);
	private StreamMap streams;

	private ConcurrentHashMap<Integer, StreamServer> streamPorts;

	/**
	 * Sets up receiver on provided message broker
	 * 
	 * @param broker
	 */
	public StreamController(MessageBroker broker) {
		this.broker = broker;

		streams = new StreamMap();

		setupConnectDispatcher(connectRequests);
		setupBroker(this.broker);
	}
	
	// Testing purposes only .... Grrrr
	public StreamController() {
		// TODO Auto-generated constructor stub
	}

	/**
	 * Registers a StreamServer on a StreamPort
	 * @param port to listen on
	 * @param server that handles connections on the specified port
	 * @return true if successfully registered, false if the port is already in use 
	 */
	public boolean registerStreamServer(int port, StreamServer server) {
		return streamPorts.putIfAbsent(port, server) == null;
	}
	
	/**
	 * Deregisters any listener on the supplied StreamPort
	 * @param port the port that is to longer receive connections
	 * @return true if a StreamServer was removed, false if the port is not in use
	 */
	public boolean deregisterStreamServer(int port) {
		return streamPorts.remove(port) != null;
	}

	private void setupConnectDispatcher(ArrayBlockingQueue<ConnectRequest> connectRequests) {
		Thread.ofVirtual().start(
				new ConnectDispatcher(connectRequests));
	}

	/**
	 * Dispatches any connect requests to the registered listener for the stream port
	 * specified in the connect request
	 * @author Alan R. White
	 *
	 */
	private class ConnectDispatcher implements Runnable {

		private BlockingQueue<ConnectRequest> connectRequests;

		public ConnectDispatcher(BlockingQueue<ConnectRequest> connectRequests) {
			this.connectRequests = connectRequests;
		}

		@Override
		public void run() {
			try {
				while(true) {
					var connectRequest = connectRequests.take();
					
					int errorCode = 0; 
					
					try { 
						
						var listener = streamPorts.get(connectRequest.streamPort);
						
						if ( listener == null ) {
							// respond to connect request with connect fail as no listener on port
							errorCode = 1;
						} else {
							// respond with connect confirm
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

							// pass the stream object to the listener
							if ( !listener.executeStream(stream) ) {
								streams.remove(Integer.valueOf(localStreamId));
								// TODO: log an error message
							}
							
							// it's possible the StreamServer won't process the stream as it's pending queue is full
							// some form of timeout will need to tell the other end the Confirm should have been a Fail
							// we could own the queue here, but the server socket sets the queue depth
							// TODO: resolve this before migrating to a ServerSocket implementation for a StreamServer
						}
					} catch (LimitExceededException lee) {
						errorCode = 2;
					}
					
					if ( errorCode != 0 ) {
						var connectResponse = BufferData.create(16);
						connectResponse.writeInt8(connectRequest.priority);
						connectResponse.writeInt8(connectRequest.remoteId);
						connectResponse.writeInt8(CONNECT_FAIL);
						connectResponse.writeInt16(errorCode);
						broker.sendMessage(connectResponse);
					}
				}

			} catch (InterruptedException e) {
				// TODO: maybe interruption is OK, means terminate 
				throw new RuntimeException(e);
			}

		}
	}

	@SuppressWarnings("unchecked")
	/**
	 * Note: assumes that the Queue in the MessageBroker is a BlockingQueue
	 * @param broker
	 */
	private void setupBroker(MessageBroker broker) {

		messageReaderThread = Thread.ofVirtual().start(
				new MessageReader(
						(BlockingQueue<BufferData>) broker.getRxQueue(),
						connectRequests));

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
		private BlockingQueue<ConnectRequest> connectRequests;

		public MessageReader(
				BlockingQueue<BufferData> rxQueue, 
				BlockingQueue<ConnectRequest> connectRequests) {

			this.rxQueue = rxQueue;
			this.connectRequests = connectRequests;
		}

		@Override
		public void run() {
			try {
				while(true) {
					var buffer = rxQueue.take();
					var command = buffer.get(2);

					// must dispatch without blocking
					if ( command == CONNECT_REQUEST ) {
						// on Connect Request, bytes 3 & 4 contain the stream port being connected to
						byte[] portBuf = {1,2};
						var bytesRead = buffer.read(portBuf, 3, 2);
						connectRequests.add(
								new ConnectRequest(buffer.get(0), buffer.get(1), ByteBuffer.wrap(portBuf).getInt(), buffer));
					} else {
						// TODO: dispatch to stream
					}

				}

			} catch (InterruptedException e) {
				// TODO: maybe interruption is OK, means terminate 
				throw new RuntimeException(e);
			}

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
