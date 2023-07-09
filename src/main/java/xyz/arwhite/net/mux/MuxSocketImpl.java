package xyz.arwhite.net.mux;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketImpl;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.LimitExceededException;

/**
 * Represents a Stream in the mux approach.
 * 
 * @author Alan R. White
 *
 */
public class MuxSocketImpl extends SocketImpl {

	static private final Logger logger = Logger.getLogger(MuxSocketImpl.class.getName());
			
	private StreamController streamController;
	private Stream stream;
	private StreamServer server;
	
	@Override
	public void setOption(int optID, Object value) throws SocketException {
		logger.log(Level.FINE,"setOption "+optID);

	}

	@Override
	public Object getOption(int optID) throws SocketException {
		logger.log(Level.FINE,"getOption "+optID);
		return null;
	}

	@Override
	protected void create(boolean stream) throws IOException {
		logger.log(Level.FINE,"create "+stream);
		if (!stream )
			throw(new IOException("Only creates Streams"));

		this.stream = new Stream();
		this.stream.setStreamController(streamController);
	}

	@Override
	protected void connect(String host, int port) throws IOException {
		logger.log(Level.FINE,"connect "+host+":"+port);
		try {
			stream.connect(new InetSocketAddress("127.0.0.1",port));
		} catch (LimitExceededException e) {
			throw(new IOException(e));
		} 

	}

	@Override
	protected void connect(InetAddress address, int port) throws IOException {
		logger.log(Level.FINE,"connect inet "+address.toString()+" "+port);
		try {
			stream.connect(new InetSocketAddress("127.0.0.1",port));
		} catch (LimitExceededException e) {
			throw(new IOException(e));
		} 

	}

	@Override
	protected void connect(SocketAddress address, int timeout) throws IOException {
		logger.log(Level.FINE,"connect sock "+address.toString()+" t:"+timeout);
		try {
//			stream.connect(new InetSocketAddress("127.0.0.1",port), timeout);
			stream.connect(address, timeout);
		} catch (LimitExceededException e) {
			throw(new IOException(e));
		} 

	}

	/**
	 * Here we are binding to a local port, presumably to be a server.
	 * A client in the TCP paradigm can bind to a local port and that
	 * will ve the source port in all comms. In the Streams paradigm
	 * a localId is allocated. It should only be set internally.
	 * 
	 * So here we have to register this socket to receive calls on
	 * the port specified.
	 */
	@Override
	protected void bind(InetAddress host, int port) throws IOException {
		logger.log(Level.FINE,"bind "+port);
		if ( server != null ) 
			throw(new IOException("MuxSocket already bound"));

		server = new StreamServer(streamController,port);
		this.localport = server.getPort();
	}

	/**
	 * Set's how many pending connect requests there can be on a port.
	 * We have a global max on a mux of 64 across all ports, not very much.
	 * An individual StreamServer can hold 16, even less!
	 * 
	 * We are a no-op on this for now.
	 */
	@Override
	protected void listen(int backlog) throws IOException {
		logger.log(Level.FINE,"listen "+backlog);
		// TODO: implement support for variable length list of outstanding connects on a port
	}

	@Override
	protected void accept(SocketImpl s) throws IOException {
		logger.log(Level.FINE,"accept");
		
		if ( !(s instanceof MuxSocketImpl ))
			throw(new IOException("Socket passed to MuxSocketImpl.accept() not a MuxSocketImpl"));

		var msi = (MuxSocketImpl) s;

		try {
			msi.setStreamController(streamController);
			msi.setStream(server.accept());
		} catch (InterruptedException | ExecutionException e) {
			throw(new IOException(e));
		}
	}

	@Override
	protected InputStream getInputStream() throws IOException {
		return stream.getInputStream();
	}

	@Override
	protected OutputStream getOutputStream() throws IOException {
		return stream.getOutputStream();
	}

	@Override
	protected int available() throws IOException {
		return stream.getInputStream().available();
	}

	@Override
	protected void close() throws IOException {
		logger.log(Level.FINE,"close");
		
		if ( stream != null )
			stream.close();	
		
		if ( server != null )
			server.close();
	}

	@Override
	protected void sendUrgentData(int data) throws IOException {
		throw(new IOException("Urgent data not supported in Streams"));
	}

	protected StreamController getStreamController() {
		return streamController;
	}

	protected void setStreamController(StreamController streamController) {
		this.streamController = streamController;
	}

	protected Stream getStream() {
		return stream;
	}

	protected void setStream(Stream stream) {
		this.stream = stream;
	}

}
