package xyz.arwhite.net.mux;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.logging.Level;
import java.util.logging.Logger;

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

	static private final Logger logger = Logger.getLogger(MuxServerSocketFactory.class.getName());
			
	private StreamController streamController;
	
	private MuxServerSocketFactory(Builder builder) {
		this.streamController = builder.streamController;
	}

	@Override
	public ServerSocket createServerSocket(int port) throws IOException {
		logger.log(Level.FINE,"createServerSocket(p)");
		var s = new MuxServerSocket(new MuxSocketImpl());
		s.bind(new StreamSocketAddress(streamController, port));
		return s;
	}

	@Override
	public ServerSocket createServerSocket(int port, int backlog) throws IOException {
		logger.log(Level.FINE,"createServerSocket(p,b)");
		
		var i = new MuxSocketImpl();
		i.setStreamController(streamController);
		var s = new MuxServerSocket(i);
		
		s.bind(new InetSocketAddress("127.0.0.1",port), backlog);
		return s;
	}

	@Override
	public ServerSocket createServerSocket(int port, int backlog, InetAddress ifAddress) throws IOException {
		logger.log(Level.FINE,"createServerSocket(p,b,ia)");
		
		var s = new MuxServerSocket(new MuxSocketImpl());
		s.bind(new StreamSocketAddress(streamController,port), backlog);

		// TODO: what to do about the ifAddress, not meaningful here
		
		return s;
	}
	
//	public static ServerSocketFactory getDefault() {
//		// TODO: implement a default WebSocket & StreamHandler 
//		// that attempts to connect to ws://localhost:3258
//		return null; // new MuxServerSocketFactory(this);
//	}
	
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
