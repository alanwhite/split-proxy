package xyz.arwhite.net.mux;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

// Analogous to a ServerSocket in the sockets world

public class StreamServer {

	/**
	 * The controller of all IO on the underlying connection supporting the multiplexed streams.
	 */
	private StreamController controller;
	
	/**
	 * The port that this {@link xyz.arwhite.net.mux.StreamServer StreamServer} is listening on. 
	 * 
	 * Analogous to a TCP port in the TCP world.
	 * 
	 * Allows multiple StreamServers, each listening for connections to different logical services 
	 * over the underlying multiplexed connection.
	 */
	private int port;

	/**
	 * When the controller has identified a connection request destined for the port served by
	 * this StreamServer, it is wrapped in a {@link #ConnectionEntry ConnectionEntry} and stored 
	 * in this queue. 
	 * 
	 * Any caller of the {@link #accept Accept} method of the StreamServer is suspended waiting 
	 * for an entry in this queue.
	 */
	private ArrayBlockingQueue<ConnectionEntry> connections = new ArrayBlockingQueue<>(16);
	
	/**
	 * Defines an entry in the connections queue. The {@link java.util.concurrent.CompletableFuture 
	 * CompletableFuture} is used by the {@link #accept Accept} method to ensure it does not return 
	 * to a caller until the connect confirmation has been sent by the {@link #connectStream 
	 * connectStream()} method to the remote peer Stream.
	 * 
	 * Note that the {@link #connectStream connectStream()} does not wait to ensure the remote 
	 * {@link xyz.arwhite.net.mux.Stream Stream} has received the connect confirmation, it only waits
	 * until it has been sent.
	 *
	 */
	private record ConnectionEntry(CompletableFuture<Void> connected, Stream stream) {};
	
	/**
	 * Direct constructor.
	 * 
	 * @param controller the controller of the underlying connection over which Streams are multiplexed
	 * @param port the logical stream port that this StreamServer listens on
	 */
	public StreamServer(StreamController controller, int port) {

		this.controller = controller;
		this.port = port;

		if ( !controller.registerStreamServer(port, this) )
			throw (new IllegalArgumentException("port already in use"));

	}

	/**
	 * Called by the {@link xyz.arwhite.net.mux.StreamController StreamController} when it's created a 
	 * new {@link xyz.arwhite.net.mux.Stream Stream} as a result of a request received from remote peer
	 * 
	 * @param stream
	 * @return
	 */
	public boolean connectStream(Stream stream) {
		
		var conn = new ConnectionEntry(new CompletableFuture<Void>(), stream);
				
		if ( connections.offer(conn) ) {
			// connect success
			controller.send(
					StreamBuffers.createConnectConfirm(
							stream.getPriority(), 
							stream.getRemoteId(), 
							stream.getLocalId()));
			
			conn.connected().complete(null);
			
			return true;
		} else {		
			// connect fail
			controller.send(
					StreamBuffers.createConnectFail(
							stream.getPriority(), 
							stream.getRemoteId(), 
							3));
			
			return false;
		}
	}

	/**
	 * Blocks awaiting a new {@link xyz.arwhite.net.mux.Stream Stream} connected via the controller
	 * @throws InterruptedException 
	 * @throws ExecutionException 

	 */
	public Stream accept() throws InterruptedException, ExecutionException {
		var conn = connections.take();
		
		// wait for the connect confirm to be sent
		if ( !conn.connected().isDone() ) 
			conn.connected.get();
		
		return conn.stream();
	}

	/**
	 * Stop new Streams being received on this StreamServers stream port
	 */
	public void close() {
		if ( !controller.deregisterStreamServer(port) ) 
			throw (new IllegalArgumentException("port not in use"));
	}
}
