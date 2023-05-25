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
	}

}
