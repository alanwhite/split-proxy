package xyz.arwhite.net.mux;

import java.net.SocketAddress;

@SuppressWarnings("serial")
public class StreamSocketAddress extends SocketAddress {

	private StreamController streamController;
	private int streamPort;
	
	/**
	 * Identifies a stream port on a StreamController that has a connected
	 * multiplexed WebSocket
	 * 
	 * @param streamController
	 * @param streamPort
	 */
	public StreamSocketAddress(StreamController streamController, int streamPort) {
		
		this.setStreamController(streamController);
		this.setStreamPort(streamPort);
		
	}

	public StreamController getStreamController() {
		return streamController;
	}

	public void setStreamController(StreamController streamController) {
		this.streamController = streamController;
	}

	public int getStreamPort() {
		return streamPort;
	}

	public void setStreamPort(int streamPort) {
		this.streamPort = streamPort;
	}
	
	
}
