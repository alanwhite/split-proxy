package xyz.arwhite.net.mux;

import java.io.IOException;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.util.concurrent.ArrayBlockingQueue;
import io.helidon.common.buffers.BufferData;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.naming.LimitExceededException;

public class Stream {

	/**
	 * A Stream object can only be used once. It must be removed from the StreamController
	 * as soon as it's known to be of no further use.
	 * 
	 * Known states of a Stream:
	 * UNCONNECTED: This stream has never attempted a connect
	 * CONNECTING: This stream has received, or issued a connect request and awaits a connect confirm
	 * CONNECTED: This stream is connected and data can flow in either direction
	 * CLOSED: This stream has closed
	 * ERROR: This stream is in an error state and is unusable
	 * 
	 * @author Alan R. White
	 *
	 */
	public enum StreamState { UNCONNECTED, CONNECTING, CONNECTED, CLOSED, ERROR };

	/**
	 * The current connection state of this stream
	 */
	private StreamState state = StreamState.UNCONNECTED;

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
	 * Completed by the receiver thread 
	 */
	private CompletableFuture<Integer> connectCompleted = new CompletableFuture<>();

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
		this.setStreamController(controller);
		this.setLocalId(localId);
		this.setRemoteId(remoteId);
		this.setPriority(priority);

		// Set up consumer thread for peerIncoming
		// setupIncoming(peerIncoming);
	}

	public Stream(StreamController controller) throws LimitExceededException, IllegalArgumentException {
		this.setStreamController(controller);

		this.setLocalId(streamController.registerStream(this));
	}

	/**
	 * Plain constructor, all properties must be set individually
	 */
	public Stream() {};

	private void setupIncoming(ArrayBlockingQueue<BufferData> peerIncoming) {
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
				while(true) {
					var buffer = peerIncoming.take();

					var command = StreamBuffers.getBufferType(buffer);

					switch( command ) {
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
						 * 
						 */
						streamController.deregisterStream(localId);
						state = StreamState.CLOSED;	
					}



					}
				}
			} catch(InterruptedException e) {

			} catch(IllegalStateException e) {
				// throw(new IOException(e));
				// need to tell the Stream to die .....
			}
		}

	}

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
			setupIncoming(peerIncoming);

			streamController.send(StreamBuffers.createConnectRequest(priority, this.getLocalId(), streamPort));

			int result = -1;

			if ( timeout > 0 ) 
				result = connectCompleted.get(timeout, TimeUnit.MILLISECONDS);
			else
				result = connectCompleted.get();
			
			if ( result != 0 || state != StreamState.CONNECTED )
				throw(new IOException("error connecting Stream"));
			

		} catch (InterruptedException | ExecutionException e) {
			streamController.deregisterStream(localId);
			throw(new IOException("error connecting stream",e));
		} catch (TimeoutException e) {
			streamController.deregisterStream(localId);
			throw(new SocketTimeoutException());
		} catch (LimitExceededException e) {
			streamController.deregisterStream(localId);
			throw(new IOException("stream limit reached",e));
		}
	}

	public void connect(SocketAddress endpoint) throws IOException, LimitExceededException {
		connect(endpoint, 0);
	}

	private void log(String text) {
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


}
