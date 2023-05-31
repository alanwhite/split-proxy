package xyz.arwhite.net.mux;

import java.util.concurrent.ArrayBlockingQueue;
import io.helidon.common.buffers.BufferData;


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
			// TODO Auto-generated method stub
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
						
					}
					case StreamBuffers.CONNECT_FAIL -> {
						// nothing more can happen
						// state is borked
						// maybe throw an exception?
						// StreamController needs to know we're done and free the slot
					}
					
					case StreamBuffers.DISCONNECT_REQUEST -> {}
					

					}
				}
			}
			catch(InterruptedException e) {

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


}
