package xyz.arwhite.net.mux;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

import javax.naming.LimitExceededException;

import io.helidon.common.buffers.BufferData;

public class StreamController {

	/**
	 * The number of queued connect requests that have not been passed to a StreamServer
	 */
	private static final int NEW_STREAM_QUEUE_DEPTH = 64;
	
	private MessageBroker broker;
	private Thread messageReaderThread;

	public record ConnectRequest(int priority, int remoteId, int streamPort, BufferData buffer ) {};
	public record ConnectConfirm(int priority, int localId, int remoteId) {};
	public record ConnectFail(int priority, int localId, int errorCode) {};
	public record DisconnectRequest(int priority, int localId) {};
	public record DisconnectConfirm(int priority, int localId, int errorCode) {};
	public record BufferIncrement(int priority, int localId, int size) {};
	

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
	
	protected int registerStream(Stream stream) throws LimitExceededException {
		
		int localStreamId = streams.allocNewStreamId();

		// add entry to Streams map 
		streams.put(Integer.valueOf(localStreamId), stream); 
		
		return localStreamId;
	}
	
	protected boolean deregisterStream(int stream) {
		
		var result = streams.remove(stream) != null;
		if ( result )
			streams.freeStreamId(stream);
		
		return result;
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
							errorCode = StreamConstants.NO_LISTENER_ON_STREAM_PORT;
						} else {
							/*
							 * We are processing a Connect Request received from a remote
							 * peer. The Connect Request informs us of the peers localID
							 * which to us is the remoteID for the Stream created.
							 */
							int localStreamId = streams.allocNewStreamId();

							// create Stream object for this connection, containing the local and remote streamIds
							var stream = new Stream(StreamController.this, localStreamId, connectRequest.remoteId, connectRequest.priority);

							// add entry to Streams map 
							streams.put(Integer.valueOf(localStreamId), stream); 

							// pass the stream object to the listener
							if ( !listener.connectStream(stream) ) {
								streams.remove(Integer.valueOf(localStreamId));
								// TODO: log an error message
							}
						}
					} catch (LimitExceededException lee) {
						errorCode = StreamConstants.MAX_STREAMS_EXCEEDED;
					}

					if ( errorCode != 0 ) 
						broker.sendMessage(StreamBuffers.createConnectFail(connectRequest.priority, connectRequest.remoteId, errorCode));
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
	 * byte 1 is the local stream id
	 * byte 2 is the message type
	 * byte 3+ is message specific properties / data
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
					var command = StreamBuffers.getBufferType(buffer);

					// must dispatch without blocking
					if ( command == StreamBuffers.CONNECT_REQUEST ) {
						// TODO: log error if too many outstanding connect requests
						connectRequests.offer(StreamBuffers.parseConnectRequest(buffer));
					} else {
						var stream = streams.get(StreamBuffers.getStreamId(buffer));
						if ( stream != null ) {
							if ( !stream.getPeerIncoming().offer(buffer) ) {
								// TODO: Log dropping data for backed up stream
							}
						} else {
							// TODO: Log buffer received for non-existant stream
						}
					}

				}

			} catch (InterruptedException e) {
				// TODO: maybe interruption is OK, means terminate 
				throw new RuntimeException(e);
			}

		}

	}

	public boolean send(BufferData buffer) {
		return broker.sendMessage(buffer);
	}

}
