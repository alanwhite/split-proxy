package xyz.arwhite.net.mux;

public class SplitProxy {

	public static void main(String[] args) {

		// parse parameters

		// ip - default 127.0.0.1 - WebSocket ip to listen on / connect to 
		// port - default 8258 - WebSocket port to listen on / connect to
		// listen - one of list/connect must be supplied - starts the WebSocket server
		// connect - connects to remote WebSocket server

		// proxy-ip - default 127.0.0.1 - the IP to listen for proxy requests on
		// proxy-port - default 8255 - the port to listen for proxy requests on

		/*
		 * need a StreamController instance that wraps the WsPriHandler on the WS
		 * it tracks all Streams
		 * A stream can be created because a call came across the ws 
		 * Or because we initiate a call across the ws

		 * If we initiate the stream we say to the other end here is where I want to connect to
		 * If the other end initiated we get a message saying hey connect to here
		 * 
		 * Proxy Connect -> Create Stream -> Forward Target -> Await Confirm -> relay all IO
		 * Stream Created -> Receive Target -> Connect Target -> Confirm -> relay all IO
		 *  
		 * The StreamController receives all IO from the ws, checks if it's for a known Stream
		 * invokes the callback registered on that Stream. If it's a Create Stream call, it needs
		 * to know how to create a Stream instance for it, i.e. what is the default callback

		 * Maybe we are saying hey StreamController, when you get a new stream request then
		 * let me know, and I'll accept it, passing in the callback to use. IE we call a listen 
		 * method in the StreamController, and it hangs until there's a new stream request received
		 * So there's a new stream request queue, listen is taking from it. StreamController code
		 * that's reading from the WS spots the new stream request and queues it.
		 * 
		 * So I'm creating a table of connections, StreamHandler has a table of Streams
		 * I need the table of connections to know when I get IO on an external socket (whether
		 * it was created by a local proxy request, or a remote relay request, which Stream to 
		 * pass the IO to, or when I receive IO on a stream which connection gets it.
		 * 
		 * We really want a virtual thread or two per connection, one for each IO direction
		 * 
		 * And a Stream is really a Socket, but let's get this working functionally and 
		 * then align with the Socket construct.
		 */

		/*
		 * First let's build and test the StreamController and Stream classes.
		 */
		
		// build the websocket
		
		// create a stream controller for the websocket
		
		// create a listening thread that listens on a stream port (we're going to assume 258 for this example)
		// it listens by getting a stream listener instance from the stream controller
		// we could create a stream listener factory (stream server factory) with the stream controller injected
		// this code would then use the stream listener factory (server stream factory) to create a stream listener (stream server) for a given stream port
		
		// the thread then loops on the accept method of the stream server object which suspends until a stream request comes in
	}

	/*
	 * Buffers....
	 * 
	 * We have a thread in the Stream object that receives data send from a remote peer
	 * We have a local app that is reading from the Stream via an InputStream.
	 * 
	 * So the Stream is writing data to a buffer, and an implementation of InputStream is reading 
	 * from it when requested to do so.
	 * 
	 * The buffer is not thread-safe, there can be no read operations while it's being written to
	 * and similarly it cannot be read while being written to.
	 * 
	 * When a buffer is compacted to be set back to write mode it adds write capacity to the buffer
	 * When a buffer is flipped to read mode, nothing can be written to it.
	 * 
	 * So we need to lock the buffer, and understand when we acquire the lock, what mode a buffer is in
	 * No class provides this synchronized state tracking for a byte buffer. We need to build it.
	 * 
	 * We could use channels I expected however we're trying to avoid async coding patterns as
	 * we're liberated from those by virtual threads.
	 * 
	 * Was thinking about how virtual threads liberate us from the complexity of asynchronous
	 * code patterns in Java. Virtual threads are cheap so we can spawn them and block them as
	 * needed.
	 * 
	 * Often we need to transfer data via bytebuffers, which are clearly marked as not thread-safe
	 * so they don't synchronize operations on the encapsulated data.
	 * Also we need to track the state of the bytebuffer, is it in a state suitable for appending 
	 * new data, or do we need to compact it back before doing so? Is it in a state to read more
	 * data from or do we need to flip it before reading?
	 * 
	 * These operations need to be synchronized we can't have the reader and write operating on
	 * the buffer at the same time. In the virtual thread world reentrant locks are recommended
	 * (REFERENCE) and provide benefit x,y,z.
	 * 
	 * Utility class to provide such a synchronized transit buffer.
	 * 
	 * We need our read methods on the InputStream to block until data is available.
	 * Our options for this are potentially a future, or a blocking queue.
	 * If we're using a blocking queue, what's the deal with the bytebuffer ...
	 * 
	 * Maybe we signal on the blocking queue that a write has happened, if there's
	 * multiple writes before a reader comes along then if they consume all data
	 * when taking the first off the queue then subsequent takes will result in
	 * zero reads.
	 * 
	 * Maybe it's a condition on a reentrant lock ... need to look at those.
	 * 
	 * So the reader acquires the reentrant lock, discovers there's no data so
	 * waits on a condition. When a writer adds data to the buffer, they signal
	 * data has been added.
	 * 
	 * Not sure why this then can't be a queue ... I guess we're trying to pool
	 * the data so one read could consume all the data from multiple writes.
	 * 
	 * Streams peer incoming readers are already on own threads, so blocking
	 * won't be an issue for them.
	 * 
	 * Some trial and error needed here, maybe the sync'd state tracked buffers
	 * aren't needed? Can we have a mechanism where we can apply flow control 
	 * back, ie don't send any more data, I've got nowhere to put it.
	 * 
	 * The buffer class could send buffer control messages back via the stream
	 * when it's read and compacted ready for writes, remote end knows the
	 * buffer size, so only sends until it knows it's full. Occasionally it 
	 * will receive a buffer control message saying x more bytes can be sent.
	 * 
	 * Maybe expose an AtomicInteger that can be used by available() to read
	 * to see how much data is waiting to be read. Writers and readers would
	 * need to add / subtract from it.
	 * 
	 * Might better for available to lock the buffer and return the readable
	 * data.
	 * 
	 */
	
}
