package xyz.arwhite.net.mux;

import java.io.IOException;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.naming.LimitExceededException;

import io.helidon.common.buffers.BufferData;

public class Stream {

	/**
	 * Default timeout on stream
	 */
	private static final long DEFAULT_TIMEOUT = 1000 * 60; 
	
	/**
	 * A Stream object can only be used once. It must be removed from the StreamController
	 * as soon as it's known to be of no further use.
	 * 
	 * Known states of a Stream:
	 * UNCONNECTED: This stream has never attempted a connect
	 * CONNECTING: This stream has received, or issued a connect request and awaits a connect confirm
	 * CONNECTED: This stream is connected and data can flow in either direction
	 * CLOSING: This stream has sent a disconnect request and not yet receive a confirm
	 * CLOSED: This stream has closed
	 * ERROR: This stream is in an error state and is unusable
	 * 
	 * @author Alan R. White
	 *
	 */
	public enum StreamState { UNCONNECTED, CONNECTING, CONNECTED, CLOSING, CLOSED, ERROR };

	/**
	 * The current connection state of this stream. Enums are thread-safe, volatile
	 * ensures all threads receive updates made by other threads.
	 */
	private volatile StreamState state = StreamState.UNCONNECTED;

	/**
	 * The StreamController that holds the WebSocket over which Streams are multiplexed
	 */
	private StreamController streamController;

	/**
	 * The local identity of this Stream, unique key to the Stream map held in the StreamController
	 */
	private int localId;

	/**
	 * The remote identity the connected peer uses to uniquely identify this Stream. This must be
	 * provided in every message sent to the peer.
	 */
	private int remoteId;

	/**
	 * The queue on which all incoming messages destined for this Stream are placed.
	 */
	private ArrayBlockingQueue<BufferData> peerIncoming = new ArrayBlockingQueue<>(16);

	/**
	 * The relative priority on the WebSocket of messages for this Stream.
	 */
	private int priority;

	/**
	 * The remote streamPort to which this Stream is connected 
	 */
	private int streamPort = -1;

	/**
	 * Completion signals by the receiver thread 
	 */
	private CompletableFuture<Integer> connectCompleted = new CompletableFuture<>();
	private CompletableFuture<Integer> disconnectCompleted = new CompletableFuture<>();

	/**
	 * Terminate stream if no activity for the streamTimeout value 
	 */
	private long streamTimeout = DEFAULT_TIMEOUT;
	
	/**
	 * Constructor used by a StreamController to create a Stream, either as a result of a request
	 * to connect a Stream across the WebSocket, or receiving a connect request from the peer.
	 * 
	 * @param controller the StreamController managing the WebSocket this stream is multiplexed on
	 * 
	 * @param localId set by the StreamController when issuing/receiving a connect request
	 * 
	 * @param remoteId set to zero when created as a result of an issued connect request, 
	 * otherwise the id received in an incoming connect request
	 * 
	 * @param priority
	 */
	public Stream(StreamController controller, int localId, int remoteId, int priority) {
		this();
		setStreamController(controller);
		setLocalId(localId);
		setRemoteId(remoteId);
		setPriority(priority);
		
		// Set up consumer thread for peerIncoming
		// setupIncoming(peerIncoming);
		// maybe needed for when invoked from a listen / accept
	}

	public Stream(StreamController controller) throws LimitExceededException {
		this();
		setStreamController(controller);
		setLocalId(streamController.registerStream(this));
	}

	/**
	 * Plain constructor, all properties must be set individually
	 */
	public Stream() {
		// state.set(StreamState.UNCONNECTED);
	}

	private void startReceiver(ArrayBlockingQueue<BufferData> peerIncoming) {
		Thread.ofVirtual().start(
				new Incoming(peerIncoming));
	}

	class Incoming implements Runnable {

		private ArrayBlockingQueue<BufferData> peerIncoming;

		public Incoming(ArrayBlockingQueue<BufferData> peerIncoming) {
			this.peerIncoming = peerIncoming;
		}

		@Override
		public void run() {
			try {
				boolean halt_receiver = false;
				while(!halt_receiver) {
					var buffer = peerIncoming.take();

					var command = StreamBuffers.getBufferType(buffer);
					
					switch( command ) {
					// what about connect requests .....
					case StreamBuffers.CONNECT_CONFIRM -> {
						/*
						 * If we receive a Connect Confirm while not in a state
						 * where we're waiting for one this is a sequence error
						 * of some form. Only viable action is to terminate the 
						 * Stream. 
						 */
						if ( state != StreamState.CONNECTING ) {
							streamController.deregisterStream(localId);
							state = StreamState.ERROR;
							connectCompleted.complete(StreamConstants.UNEXPECTED_CONNECT_CONFIRM);
							throw(new IllegalStateException("Invalid state change UC to CC"));
						}

						/*
						 * We have received a Connect Confirm in response
						 * to a Connect Request we sent. When we sent it
						 * we told the peer what our localID is, and in 
						 * response the peer tells it's localID, which to 
						 * us, is it's remoteID we must provide whenever we 
						 * send data to it.
						 */

						var cc = StreamBuffers.parseConnectConfirm(buffer);
						Stream.this.setRemoteId(cc.remoteId());
						state = StreamState.CONNECTED;
						connectCompleted.complete(0);

					}
					case StreamBuffers.CONNECT_FAIL -> {
						/*
						 * We have received a Connect Fail in response
						 * to a Connect Request we sent. The connection
						 * has not been established and the state of 
						 * this Stream is it is now unusable. We must 
						 * terminate and inform the StreamController that
						 * our slot and local id must be freed up.
						 *
						 * When we've been freed up we must exit the run loop
						 */

						streamController.deregisterStream(localId);
						state = StreamState.CLOSED;
						connectCompleted.complete(0);
					}

					case StreamBuffers.DISCONNECT_REQUEST -> {
						/*
						 * Need to shut down and send confirm
						 */
						state = StreamState.CLOSED;
						
						streamController.send(
								StreamBuffers.createDisconnectConfirm(priority, remoteId));
						
						streamController.deregisterStream(localId);
						
						halt_receiver = true;
						
					}

					case StreamBuffers.DISCONNECT_CONFIRM -> {
						/*
						 * If we receive a Disconnect Confirm while not in a state
						 * where we're waiting for one this is a sequence error
						 * of some form. Only viable action is to terminate the 
						 * Stream. 
						 */
						if ( state != StreamState.CLOSING ) {
							streamController.deregisterStream(localId);
							state = StreamState.ERROR;
							disconnectCompleted.complete(StreamConstants.UNEXPECTED_DISCONNECT_CONFIRM);
							throw(new IllegalStateException("Invalid state change DC and not Closing"));
						}
						
						/*
						 * We have received a Disconnect Confirmation so we can tidily
						 * close down.
						 */
						streamController.deregisterStream(localId);
						state = StreamState.CLOSED;	
						disconnectCompleted.complete(0);
						
						halt_receiver = true;
					}

					} // switch
				} // while
				
				log("peerIncoming receiver tidily closed");
				
			} catch(InterruptedException | IllegalStateException e) {
				streamController.deregisterStream(localId);
				state = StreamState.ERROR;
			} 
		}

	}

	/**
	 * Connects this stream to the provided endpoint. Blocks calling thread until
	 * connection completes or times out.
	 * 
	 * @param endpoint
	 * @param timeout overrides the default stream timeout during connection
	 * @throws IOException
	 * @throws LimitExceededException
	 */
	public void connect(SocketAddress endpoint, int timeout) 
			throws IOException, LimitExceededException {

		if ( !(endpoint instanceof StreamSocketAddress) )
			throw(new IOException("endpoint must be of type StreamSocketAddress") );

		if ( state != StreamState.UNCONNECTED )
			throw(new IOException("Invalid state transition - Stream must be in an unconnected state"));

		StreamSocketAddress ssa = (StreamSocketAddress) endpoint;
		streamPort = ssa.getStreamPort();
		streamController = ssa.getStreamController();

		try {
			this.localId = streamController.registerStream(this);

			state = StreamState.CONNECTING;

			// become able to receive any responses 
			startReceiver(peerIncoming);

			streamController.send(StreamBuffers.createConnectRequest(priority, this.getLocalId(), streamPort));

			int result = connectCompleted.get(
					timeout > 0 ? timeout : getStreamTimeout(), TimeUnit.MILLISECONDS);
			
			if ( result != 0 || state != StreamState.CONNECTED )
				throw(new IOException("error connecting Stream"));

		} catch (InterruptedException | ExecutionException | TimeoutException e) {
			state = StreamState.ERROR;
			streamController.deregisterStream(localId);
			
			if ( e instanceof TimeoutException)
				throw(new SocketTimeoutException());
			
			throw(new IOException("error connecting stream",e));

		} catch (LimitExceededException e) {
			state = StreamState.ERROR;
			streamController.deregisterStream(localId);
			throw(new IOException("stream limit reached",e));
		}
	}

	public void connect(SocketAddress endpoint) throws IOException, LimitExceededException {
		connect(endpoint, 0);
	}
	
	/**
	 * Closes this stream. Blocks the calling thread until the operation completes
	 * or times out.
	 * 
	 * @throws IOException 
	 */
	public void close() throws IOException {
		// send a disconnect request message
		// await a disconnect confirm or timeout
		state = StreamState.CLOSING;
		
		streamController.send(
				StreamBuffers.createDisconnectRequest(priority, remoteId));
		
		try {
			int result = disconnectCompleted.get(getStreamTimeout(), TimeUnit.MILLISECONDS);
			
			if ( result != 0 || state != StreamState.CLOSED )
				throw(new IOException("error disconnecting Stream "+state.toString()));
			
		} catch (InterruptedException | ExecutionException | TimeoutException e) {
			state = StreamState.ERROR;
			streamController.deregisterStream(localId);
			
			if ( e instanceof TimeoutException) 
				throw(new SocketTimeoutException());
			
			throw(new IOException("error disconnecting stream",e));
		}
	}
	
	/**
	 * Returns the closed state of the Stream
	 * 
	 * @return true if the Stream has been closed
	 */
	public boolean isClosed() {
		return state == StreamState.CLOSED || state == StreamState.CLOSING || state == StreamState.ERROR;
	}

	private static void log(String text) {
		System.out.println(Thread.currentThread().isVirtual()+
				":"+Thread.currentThread().threadId()+":"+text);
	}

	public int getLocalId() {
		return localId;
	}

	public void setLocalId(int localId) {
		this.localId = localId;
	}

	public int getRemoteId() {
		return remoteId;
	}

	public void setRemoteId(int remoteId) {
		this.remoteId = remoteId;
	}

	public int getPriority() {
		return priority;
	}

	public void setPriority(int priority) {
		this.priority = priority;
	}

	public ArrayBlockingQueue<BufferData> getPeerIncoming() {
		return peerIncoming;
	}

	public void setPeerIncoming(ArrayBlockingQueue<BufferData> peerIncoming) {
		this.peerIncoming = peerIncoming;
	}

	public int getStreamPort() {
		return streamPort;
	}

	public void setStreamPort(int streamPort) {
		this.streamPort = streamPort;
	}

	public StreamController getStreamController() {
		return streamController;
	}

	public void setStreamController(StreamController streamController) {
		this.streamController = streamController;
	}

	public long getStreamTimeout() {
		return streamTimeout;
	}

	public void setStreamTimeout(long streamTimeout) {
		this.streamTimeout = streamTimeout;
	}


}
