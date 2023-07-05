package xyz.arwhite.net.mux;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.SocketFactory;

import xyz.arwhite.net.mux.MuxServerSocketFactory.Builder;

public class MuxSocketFactory extends SocketFactory {

	static private final Logger logger = Logger.getLogger(MuxSocketFactory.class.getName());
	private StreamController streamController;
	
	private MuxSocketFactory(Builder builder) {
		this.streamController = builder.streamController;
	}
	
	@Override
	public Socket createSocket() throws IOException {
		logger.log(Level.FINE,"createSocket");
		var i = new MuxSocketImpl();
		i.setStreamController(streamController);
		var s = new MuxSocket(i);
		return s;
	}
	
	@Override
	public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
		logger.log(Level.FINE,"createSocket(h,p)");
		
		var i = new MuxSocketImpl();
		i.setStreamController(streamController);
		var s = new MuxSocket(i);
		
		s.connect(new InetSocketAddress("127.0.0.1",port));
		return s;
	}

	@Override
	public Socket createSocket(InetAddress host, int port) throws IOException {
		logger.log(Level.FINE,"createSocket(ia,p)");
		
		var i = new MuxSocketImpl();
		i.setStreamController(streamController);
		var s = new MuxSocket(i);
		
		logger.log(Level.WARNING,"MuxSockets ignore InetAddress");
		s.connect(new InetSocketAddress("127.0.0.1",port));
		return s;
	}

	@Override
	public Socket createSocket(String host, int port, InetAddress localAddress, int localPort)
			throws IOException, UnknownHostException {
		logger.log(Level.FINE,"createSocket(h,p,ia,lp");
		
		var i = new MuxSocketImpl();
		i.setStreamController(streamController);
		var s = new MuxSocket(i);
		
		logger.log(Level.WARNING,"MuxSockets ignore InetAddress");
		s.connect(new InetSocketAddress("127.0.0.1",port));
		return s;
	}

	@Override
	public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
		logger.log(Level.FINE,"createSocket(ia,p,lia,lp");
		
		var i = new MuxSocketImpl();
		i.setStreamController(streamController);
		var s = new MuxSocket(i);
		
		logger.log(Level.WARNING,"MuxSockets ignore InetAddress");
		s.connect(new InetSocketAddress("127.0.0.1",port));
		return s;
	}
	
	public static class Builder {
		StreamController streamController;
		
		public Builder withMux(StreamController streamController) {
			this.streamController = streamController;
			return this;
		}

		public MuxSocketFactory build() {
			return new MuxSocketFactory(this);
		}
	}

}
