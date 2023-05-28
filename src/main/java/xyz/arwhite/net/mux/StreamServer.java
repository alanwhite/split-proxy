package xyz.arwhite.net.mux;

import java.util.concurrent.ArrayBlockingQueue;

// Analagous to a ServerSocket in the sockets world

public class StreamServer {

	private StreamController controller;
	private int port;

	private ArrayBlockingQueue<Stream> streams = new ArrayBlockingQueue<>(16);

	public StreamServer(StreamController controller, int port) {

		this.controller = controller;
		this.port = port;

		if ( !controller.registerStreamServer(port, this) )
			throw (new IllegalArgumentException("port already in use"));

	}

	/**
	 * Called by the StreamController when it's created a new Stream as a result of 
	 * a request received from remote peer
	 * @param stream
	 * @return
	 */
	public boolean executeStream(Stream stream) {
		// TODO: have this attempt to add to queue (locking it), send the CC or CF if full, unlock finally.
		return streams.offer(stream);
	}

	/**
	 * Blocks awaiting a new stream connected via the controller
	 * @throws InterruptedException 

	 */
	public Stream accept() throws InterruptedException {
		var stream = streams.take();
		return stream;
	}

	/**
	 * Stop new Streams being received on this StreamServers StreamPort
	 */
	public void close() {
		if ( !controller.deregisterStreamServer(port) ) 
			throw (new IllegalArgumentException("port not in use"));
	}
}
