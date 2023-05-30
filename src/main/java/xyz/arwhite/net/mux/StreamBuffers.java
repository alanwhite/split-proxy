package xyz.arwhite.net.mux;

import java.nio.ByteBuffer;

import io.helidon.common.buffers.BufferData;
import xyz.arwhite.net.mux.StreamController.ConnectRequest;

public class StreamBuffers {

	/**
	 * Buffer Header
	 * =============
	 * Byte - priority - 0 is highest, 255 is lowest priority
	 * Byte - receivers stream ID (except in connect request where used to communicate callers stream id)
	 * Byte - buffer type, CONNECT_REQUEST etc ...
	 * 
	 * Connect Request
	 * ===============
	 * Header - buffer type set to CONNECT_REQUEST
	 * Int - stream port being connected to
	 * 
	 * Connect Confirm
	 * ===============
	 * Header - buffer type set to CONNECT_CONFIRM
	 * Byte - the stream ID the remote must use when sending data to identify this stream
	 * 
	 * Connect Fail
	 * ============
	 * Header - buffer type set to CONNECT_FAIL
	 * Int - error reason code
	 * 
	 */
	
	public static final byte CONNECT_REQUEST = 1;
	public static final byte CONNECT_CONFIRM = 2;
	public static final byte CONNECT_FAIL = 3;
	public static final byte CLOSE = 4;
	public static final byte DATA = 5;
	public static final byte BUFINC = 6;
	
	public static int getBufferType(BufferData buffer) {
		return buffer.get(2);
	}
	
	public static BufferData createConnectRequest(int priority, int localStreamId, int port) {
		
		var buffer = BufferData.create(16);
		buffer.writeInt8(priority);
		buffer.writeInt8(localStreamId);
		buffer.writeInt8(CONNECT_REQUEST);
		buffer.writeInt16(port);
		return BufferData.create(16);
	}
	
	public static ConnectRequest parseConnectRequest(BufferData buffer) {
		byte[] portBuf = {1,2};
		buffer.read(portBuf, 3, 2);
		
		return new ConnectRequest(buffer.get(0), buffer.get(1), ByteBuffer.wrap(portBuf).getInt(), buffer);
	}
	
	public static BufferData createConnectConfirm(int priority, int remoteStreamId, int localStreamId) {
		
		var connectResponse = BufferData.create(4);
		connectResponse.writeInt8(priority);
		connectResponse.writeInt8(remoteStreamId);
		connectResponse.writeInt8(CONNECT_CONFIRM);
		connectResponse.writeInt8(localStreamId);
		
		return connectResponse;
	}
	
	public static BufferData createConnectFail(int priority, int remoteStreamId, int errorCode) {
		
		var connectResponse = BufferData.create(5);
		connectResponse.writeInt8(priority);
		connectResponse.writeInt8(remoteStreamId);
		connectResponse.writeInt8(CONNECT_FAIL);
		connectResponse.writeInt16(errorCode);
		
		return connectResponse;
	}
}
