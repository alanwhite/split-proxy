package xyz.arwhite.net.mux;

public class StreamConstants {

	// Error reason codes used in Connect Fail messages
	
	public static final int NO_LISTENER_ON_STREAM_PORT = 1001;
	public static final int MAX_STREAMS_EXCEEDED = 1002;
	public static final int UNABLE_TO_START_STREAM = 1003;
	
	public static final int PENDING_STREAM_PORT_CONNECTIONS_EXCEEDED = 2001;
	
	// Error reason raised in a Stream
	public static final int UNEXPECTED_CONNECT_CONFIRM = 3001;
	public static final int UNEXPECTED_DISCONNECT_CONFIRM = 3002;
	public static final int UNEXPECTED_BUFFER_INCREMENT = 3003;
}
