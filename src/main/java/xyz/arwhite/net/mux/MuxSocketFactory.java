package xyz.arwhite.net.mux;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import javax.net.SocketFactory;

import xyz.arwhite.net.mux.MuxServerSocketFactory.Builder;

public class MuxSocketFactory extends SocketFactory {

	private StreamController streamController;
	
	private MuxSocketFactory(Builder builder) {
		this.streamController = builder.streamController;
	}
	
	@Override
	public Socket createSocket() throws IOException {
		log("createSocket");
		var i = new MuxSocketImpl();
		i.setStreamController(streamController);
		var s = new MuxSocket(i);
		return s;
	}
	
	@Override
	public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
		log("createSocket(h,p)");
		var i = new MuxSocketImpl();
		i.setStreamController(streamController);
		var s = new MuxSocket(i);
		
		s.connect(new InetSocketAddress("127.0.0.1",port));
		return s;
	}

	@Override
	public Socket createSocket(InetAddress host, int port) throws IOException {
		log("createSocket(ia,p)");
		var i = new MuxSocketImpl();
		i.setStreamController(streamController);
		var s = new MuxSocket(i);
		
		System.err.println("WARNING: MuxSockets ignore InetAddress");
		s.connect(new InetSocketAddress("127.0.0.1",port));
		return s;
	}

	@Override
	public Socket createSocket(String host, int port, InetAddress localAddress, int localPort)
			throws IOException, UnknownHostException {
		log("createSocket(h,p,ia,lp");
		var i = new MuxSocketImpl();
		i.setStreamController(streamController);
		var s = new MuxSocket(i);
		
		System.err.println("WARNING: MuxSockets ignore InetAddress");
		s.connect(new InetSocketAddress("127.0.0.1",port));
		return s;
	}

	@Override
	public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
		log("createSocket(ia,p,lia,lp");
		var i = new MuxSocketImpl();
		i.setStreamController(streamController);
		var s = new MuxSocket(i);
		
		System.err.println("WARNING: MuxSockets ignore InetAddress");
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

	private void log(String m) {
		System.out.println("MuxSocketFactory: "+Integer.toHexString(this.hashCode())+" :"+ m);
	}

}
