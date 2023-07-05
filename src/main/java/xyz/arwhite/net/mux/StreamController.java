package xyz.arwhite.net.mux;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.LimitExceededException;

import io.helidon.common.buffers.BufferData;

public class StreamController {

	static private final Logger logger = Logger.getLogger(StreamController.class.getName());
			
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
	public record TransmitData(int priority, int localId, int size, BufferData buffer) {};


	private ArrayBlockingQueue<ConnectRequest> connectRequests = new ArrayBlockingQueue<>(NEW_STREAM_QUEUE_DEPTH);
	private StreamMap streams;

	private ConcurrentHashMap<Integer, StreamServer> streamPorts = new ConcurrentHashMap<>();

	/**
	 * Sets up receiver on provided message broker
	 * 
	 * @param broker
	 */
	private StreamController(MessageBroker broker) {
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
		logger.log(Level.FINE,"registerStream");
		
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
					logger.log(Level.FINER,"CR in");
					
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
								logger.log(Level.WARNING,"server stream not able to handle a connect request");
							}
						}
					} catch (LimitExceededException lee) {
						errorCode = StreamConstants.MAX_STREAMS_EXCEEDED;
					} catch (IOException e) {
						errorCode = StreamConstants.UNABLE_TO_START_STREAM;
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
		logger.log(Level.FINE,"setupBroker");
		messageReaderThread = Thread.ofVirtual().start(
				new MessageReader(
						(BlockingQueue<PriorityQueueEntry>) broker.getRxQueue(), // it's priority queue entries that come back here
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

		private BlockingQueue<PriorityQueueEntry> rxQueue;
		private BlockingQueue<ConnectRequest> connectRequests;

		public MessageReader(
				BlockingQueue<PriorityQueueEntry> rxQueue, 
				BlockingQueue<ConnectRequest> connectRequests) {

			this.rxQueue = rxQueue;
			this.connectRequests = connectRequests;
		}

		@Override
		public void run() {
			try {
				while(true) {
					var pqe = rxQueue.take();
					var buffer = pqe.message();
					
					logger.log(Level.FINER,"incoming");

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
								logger.log(Level.WARNING,"dropping due to backed up stream");
								
							} else {
								logger.log(Level.FINEST,"data buffer passed to stream on "+stream.getPeerIncoming().hashCode());
								logger.log(Level.FINEST,"peer incoming used = "+stream.getPeerIncoming().size());
							}
						} else {
							// TODO: Log buffer received for non-existant stream
							logger.log(Level.WARNING,"message for unknown stream discarded");
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
		logger.log(Level.FINE,"send");
		return broker.sendMessage(buffer);
	}

	/*
	 * We want the experience to be you create a MuxSocketFactory / MuxServerSocketFactory
	 * specifying the StreamController to use.
	 * 
	 * You can create a StreamController only when you have a MessageBroker for it to integrate with.
	 * 
	 * The MessageBroker we are using is a WsListener on a WebSocket.
	 * 
	 * Our choice is have a separate class for creating the suitable websocket, or integrate it
	 * with creating a StreamController.
	 * 
	 * If we have a separate class, e.g. MuxWsTunnel, we could provide it to the builder pattern 
	 * here, and have the option to create the MuxWsTunnel within the builder itself.
	 * 
	 * Well it's really just a WebSocket with a handler that has a MessageBroker interface.
	 * In the future we might have the MessageBroker implemented over a straight TCP connection.
	 * 
	 * We do need a separate WS based MessageBroker, it is a tunnel but that's a bad word to many.
	 * It's a MessageBroker based WS based connection, either client or server mode.
	 * It doesn't really do the Muxing, it just exchanges messages. It's suitable for Muxing over.
	 * with priorities.
	 * WS - MessageBroker - Suitable for Muxing. It's a WsMessageExchange .... point to point
	 * A WsMessageRelay endpoint.... a WsMessageLink that implements the MessageBroker interface 
	 * 
	 * So we have a WsMessageLink, in either client or server mode. When we build that we 
	 * have to supply a message handler so we will provide the WsPriorityMessageHandler.
	 * 
	 * Separate class.
	 * 
	 */
	public static class Builder {
		MessageBroker messageBroker;
		WsMessageLink messageLink;

		public Builder withMessageBroker(MessageBroker messageBroker) {
			this.messageBroker = messageBroker;
			return this;
		}

		public Builder withMessageLink(WsMessageLink wsml) {
			this.messageLink = wsml;
			messageBroker = wsml.getMessageBroker();
			return this;
		}

		public StreamController build() {
			return new StreamController(messageBroker);
		}
	}

}
