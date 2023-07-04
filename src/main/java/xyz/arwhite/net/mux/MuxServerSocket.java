package xyz.arwhite.net.mux;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketImpl;

public class MuxServerSocket extends ServerSocket {

	private StreamController streamController;
	
	protected MuxServerSocket(SocketImpl impl) {
		super(impl);
		
		if ( impl instanceof MuxSocketImpl ) {
			var i = (MuxSocketImpl) impl;
			this.streamController = i.getStreamController();
		}
	}

	@Override
	public Socket accept() throws IOException {
		if (isClosed())
			throw new SocketException("Socket is closed");
		if (!isBound())
			throw new SocketException("Socket is not bound yet");
		
		System.out.println("Awaiting connection on port "+this.getLocalPort());
		
		var i = new MuxSocketImpl();
		i.setStreamController(streamController);
		
		MuxSocket s = new MuxSocket(i);
		implAccept(s);
		
		return s;
	}

}
