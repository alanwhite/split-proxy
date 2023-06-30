package xyz.arwhite.net.mux;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;

import javax.net.ServerSocketFactory;

/**
 * A MuxServerSocketFactory creates MuxServerSockets that listen on a StreamController.
 * The StreamController is connected to a WebSocket.
 * 
 * The getDefault() static method assumes you want to create a new StreamController,
 * on a new WebSocket, that initiates a connection to ws://localhost:3258.
 * 
 * Otherwise a StreamController needs to be provided.
 * 
 * 
 * @author Alan R. White
 *
 */
public class MuxServerSocketFactory extends ServerSocketFactory {

	private StreamController streamController;
	
	private MuxServerSocketFactory(Builder builder) {
		this.streamController = builder.streamController;
	}

	@Override
	public ServerSocket createServerSocket(int port) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ServerSocket createServerSocket(int port, int backlog) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ServerSocket createServerSocket(int port, int backlog, InetAddress ifAddress) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}
	
	public static ServerSocketFactory getDefault() {
		// TODO: implement a default WebSocket & StreamHandler 
		// that attempts to connect to ws://localhost:3258
		return null; // new MuxServerSocketFactory(this);
	}
	
	public static class Builder {
		StreamController streamController;
		
		public Builder withMux(StreamController streamController) {
			this.streamController = streamController;
			return this;
		}
		
		public MuxServerSocketFactory build() {
			return new MuxServerSocketFactory(this);
		}
	}

}
