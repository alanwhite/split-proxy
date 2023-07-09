package xyz.arwhite.net.mux;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.LimitExceededException;

import io.helidon.common.buffers.BufferData;

/**
 * Controller for all Streams on the underlying transport. All client and server streams are
 * registered and deregistered here, all IO is through here.
 * 
 * Logging strategy is general flow visible through FINE, method entry/exit is finer, debug is FINEST
 * 
 * @author Alan R. White
 *
 */
public class StreamController {

	static private final Logger logger = Logger.getLogger(StreamController.class.getName());
			
	/**
	 * The number of queued connect requests that have not been passed to a StreamServer
	 */
	private static final int NEW_STREAM_QUEUE_DEPTH = 64;

	private MessageBroker broker;
	private Thread messageReaderThread;
	private Thread connectDispatcherThread;

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
		logger.entering(this.getClass().getName(), "StreamController", broker);
		
		this.broker = broker;

		this.streams = new StreamMap();
		setupConnectDispatcher(this.connectRequests);
		setupBroker(this.broker);
		
		logger.fine("StreamController initialized");
		
		logger.exiting(this.getClass().getName(), "StreamController");	
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
		logger.entering(this.getClass().getName(), "registerStreamServer", 
				new Object[] { Integer.valueOf(port), server });
		
		var outcome = streamPorts.putIfAbsent(port, server) == null;
		
		if ( outcome )
			logger.fine("StreamServer now listening on StreamPort "+port);
		
		logger.exiting(this.getClass().getName(), "registerStreamServer", outcome);
		return outcome;
	}

	/**
	 * Deregisters any listener on the supplied StreamPort
	 * @param port the port that is to longer receive connections
	 * @return true if a StreamServer was removed, false if the port is not in use
	 */
	public boolean deregisterStreamServer(int port) {
		logger.entering(this.getClass().getName(), "deregisterStreamServer", port);
		
		var outcome = streamPorts.remove(port) != null;
		
		if ( outcome )
			logger.fine("StreamPort "+port+" now unused");
		
		logger.exiting(this.getClass().getName(), "deregisterStreamServer", outcome);		
		return outcome;
	}

	/**
	 * Registers a new Stream
	 *  
	 * @param stream
	 * @return
	 * @throws LimitExceededException if we've maxed number of possible Streams
	 */
	protected int registerStream(Stream stream) throws LimitExceededException {
		logger.entering(this.getClass().getName(), "registerStream", stream);
		
		int localStreamId = streams.allocNewStreamId();

		streams.put(Integer.valueOf(localStreamId), stream); 

		logger.fine("New Stream with local ID "+localStreamId);
		
		logger.exiting(this.getClass().getName(), "registerStream", localStreamId);
		return localStreamId;
	}

	protected boolean deregisterStream(int stream) {
		logger.entering(this.getClass().getName(), "deregisterStream", stream);
		
		var outcome = streams.remove(stream) != null;
		if ( outcome ) {
			streams.freeStreamId(stream);
			logger.fine("Stream "+stream+" now unused");
		}
		
		logger.exiting(this.getClass().getName(), "deregisterStream", outcome);
		return outcome;
	}

	private void setupConnectDispatcher(ArrayBlockingQueue<ConnectRequest> connectRequests) {
		logger.entering(this.getClass().getName(), "setupConnectDispatcher", connectRequests);
		
		connectDispatcherThread = Thread.ofVirtual().name("Connect Dispatcher").start(
				new ConnectDispatcher(connectRequests));
	
		logger.exiting(this.getClass().getName(), "registerStream", connectDispatcherThread);
	
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
			logger.entering(this.getClass().getName(), "Constructor", connectRequests);
			
			this.connectRequests = connectRequests;

			logger.exiting(this.getClass().getName(), "Constructor");
		}

		@Override
		public void run() {
			try {
				while(true) {
					var connectRequest = connectRequests.take();
					logger.fine("Processing Connect Request on Stream Port "+connectRequest.streamPort);
					
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
							 * 
							 * //TODO: refactor so we reuse registerStream above
							 */
							logger.finest("ConnectRequest: Priority="+connectRequest.priority+" RemoteId="+connectRequest.remoteId);
							
							int localStreamId = streams.allocNewStreamId();
							logger.finest("Allocated localStreamId "+localStreamId);

							// create Stream object for this connection, containing the local and remote streamIds
							var stream = new Stream(StreamController.this, localStreamId, connectRequest.remoteId, connectRequest.priority);

							// add entry to Streams map 
							streams.put(Integer.valueOf(localStreamId), stream); 

							// pass the stream object to the listener
							if ( listener.connectStream(stream) ) {
								logger.fine("New Stream with local ID "+localStreamId);
							} else {
								streams.remove(Integer.valueOf(localStreamId));
								logger.log(Level.WARNING,"server stream not able to handle incoming Connect Request");
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
	 * TODO: refactor for when underlying transport provides other queue type
	 * @param broker
	 */
	private void setupBroker(MessageBroker broker) {
		logger.entering(this.getClass().getName(), "setupBroker", broker);
		
		messageReaderThread = Thread.ofVirtual()
				.name("MessageReader")
				.start(
				new MessageReader(
						(BlockingQueue<PriorityQueueEntry>) broker.getRxQueue(), 
						connectRequests));

		logger.exiting(this.getClass().getName(), "setupBroker", messageReaderThread);
	}

	/**
	 * Dispatches all incoming messages from the underlying transport to the appropriate
	 * stream unless the message is a connect request, in which case that's queued for
	 * processing on the connect handler thread.
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

			logger.entering(this.getClass().getName(), "Constructor",
					new Object[] { rxQueue, connectRequests });
			
			this.rxQueue = rxQueue;
			this.connectRequests = connectRequests;
			
			logger.exiting(this.getClass().getName(), "Constructor");
		}

		@Override
		public void run() {
			logger.entering(this.getClass().getName(), "run");
			try {
				while(true) {
					var pqe = rxQueue.take();
					var buffer = pqe.message();
					
					var command = StreamBuffers.getBufferType(buffer);

					logger.finest("Incoming buffer of type "+command);
					
					// must dispatch without blocking
					if ( command == StreamBuffers.CONNECT_REQUEST ) {
						var outcome = connectRequests.offer(StreamBuffers.parseConnectRequest(buffer));
						
						if ( !outcome )
							logger.warning("Could not queue Connect Request due to overrun");
						
					} else {
						var localStreamId = StreamBuffers.getStreamId(buffer);
						var stream = streams.get(localStreamId);
						
						if ( stream != null ) {
							if ( !stream.getPeerIncoming().offer(buffer) ) {
								logger.warning("Dropping buffer due to Stream " + localStreamId + " being backed up");
								
							} else {
								logger.finest("Data buffer passed to Stream " + localStreamId);
								logger.finest("Peer incoming used = "+stream.getPeerIncoming().size());
							}
						} else {
							logger.warning("Buffer for unknown Stream " + localStreamId + " discarded");
						}
					}

				}
				
				// TODO: tidy exit .... logger.exiting(this.getClass().getName(), "run");

			} catch (InterruptedException e) {
				// TODO: maybe interruption is OK, means terminate 
				throw new RuntimeException(e);
			}
		}

	}

	public boolean send(BufferData buffer) {
		logger.entering(this.getClass().getName(), "send", buffer);
		
		var outcome = broker.sendMessage(buffer);
		
		logger.exiting(this.getClass().getName(), "send", outcome);
		return outcome ;
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
