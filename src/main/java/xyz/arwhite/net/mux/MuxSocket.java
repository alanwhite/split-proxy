package xyz.arwhite.net.mux;

import java.net.Socket;
import java.net.SocketException;
import java.net.SocketImpl;

public class MuxSocket extends Socket {

	public MuxSocket(SocketImpl impl) throws SocketException {
		super(impl);
	}

}
