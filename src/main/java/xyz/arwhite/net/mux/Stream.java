package xyz.arwhite.net.mux;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.concurrent.ArrayBlockingQueue;
import io.helidon.common.buffers.BufferData;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Stream {

	/**
	 * Known states of a Stream:
	 * UNCONNECTED: This stream has never attempted a connect
	 * CONNECTING: This stream has received, or issued a connect request and awaits a connect confirm
	 * CONNECTED: This stream is connected and data can flow in either direction
	 * DISCONNECTING: This stream has issued a disconnect request and awaits a disconnect confirm
	 * DISCONNECTED: This stream has received a disconnect request or received a disconnect request
	 * @author Alan R. White
	 *
	 */
	public enum StreamState { UNCONNECTED, CONNECTING, CONNECTED, DISCONNECTING, DISCONNECTED };

	/**
	 * The current connection state of this stream
	 */
	private StreamState state = StreamState.UNCONNECTED;

	/**
	 * The StreamController that holds the WebSocket over which Streams are multiplexed
	 */
	private StreamController controller;

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

	private CompletableFuture<Void> connectCompleted = new CompletableFuture<>();

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
		this.setLocalId(localId);
		this.setRemoteId(remoteId);
		this.setPriority(priority);

		// Set up consumer thread for peerIncoming
		setupIncoming(peerIncoming);
	}

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
						connectCompleted.complete(null);

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

						state = StreamState.DISCONNECTED;
						connectCompleted.complete(null);
					}

					case StreamBuffers.DISCONNECT_REQUEST -> {}


					}
				}
			}
			catch(InterruptedException e) {

			}
		}

	}

	private void requestConnection(SocketAddress endpoint) throws IOException {

		if ( !(endpoint instanceof StreamSocketAddress) )
			throw(new IOException("endpoint must be of type StreamSocketAddress") );

		if ( state != StreamState.UNCONNECTED )
			throw(new IOException("Invalid state transition - Stream must be in an unconnected state"));

		//		if ( controller != null ) 
		//			throw(new IOException("cannot override initial StreamController during connect"));
		//
		//		if ( streamPort != -1 )
		//			throw(new IOException("cannot override already specified stream port"));



		StreamSocketAddress ssa = (StreamSocketAddress) endpoint;
		streamPort = ssa.getStreamPort();
		controller = ssa.getStreamController();

		state = StreamState.CONNECTING;
		controller.connect(streamPort);


	}

	public void connect(SocketAddress endpoint) throws IOException {
		this.requestConnection(endpoint);
		try {
			connectCompleted.get();
			if ( state != StreamState.CONNECTED )
				throw(new IOException("error connecting Stream"));
			
		} catch (InterruptedException | ExecutionException e) {
			throw(new IOException("error connecting Stream",e));
		}
	}

	public void connect(SocketAddress endpoint, int timeout) throws IOException {

		if ( timeout == 0 ) 
			this.connect(endpoint);
		else {
			try {
				connectCompleted.orTimeout(timeout, TimeUnit.MILLISECONDS).get();
				if ( state != StreamState.CONNECTED )
					throw(new IOException("error connecting Stream"));
			
			} catch (InterruptedException e) {
				throw(new IOException("interrupted waiting for connection",e));
			} catch (ExecutionException e) {
				throw(new IOException("error executing connection",e));
			}
		}
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


}
