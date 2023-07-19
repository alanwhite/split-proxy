package xyz.arwhite.net.mux;

import java.nio.ByteBuffer;

import io.helidon.common.buffers.BufferData;
import xyz.arwhite.net.mux.StreamController.BufferIncrement;
import xyz.arwhite.net.mux.StreamController.ConnectConfirm;
import xyz.arwhite.net.mux.StreamController.ConnectFail;
import xyz.arwhite.net.mux.StreamController.ConnectRequest;
import xyz.arwhite.net.mux.StreamController.TransmitData;

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
	public static final byte DISCONNECT_REQUEST = 4;
	public static final byte DISCONNECT_CONFIRM = 5;
	public static final byte DATA = 6;
	public static final byte BUFFER_INCREMENT = 7;
	public static final byte PRIORITY_SHUTDOWN = 0;

	/**
	 * Inspects the the first byte to see if the priority is 0. As no stream can have a priority 
	 * of 0 this indicates that the underlying transport is closing down. No more data can be
	 * sent or received.
	 * 
	 * @param buffer
	 * @return the buffer type, or 0 if the transport is closing down.
	 */
	public static int getBufferType(BufferData buffer) {
		var priority = buffer.get(0);
		if ( priority == PRIORITY_SHUTDOWN )
			return PRIORITY_SHUTDOWN;
		
		return buffer.get(2);
	}

	public static int getStreamId(BufferData buffer) {
		return buffer.get(1);
	}

	public static BufferData createConnectRequest(int priority, int localStreamId, int port) {

		var connectRequest = BufferData.create(5);
		connectRequest.writeInt8(priority);
		connectRequest.writeInt8(localStreamId);
		connectRequest.writeInt8(CONNECT_REQUEST);
		connectRequest.writeInt16(port);
		return connectRequest;
	}

	public static ConnectRequest parseConnectRequest(BufferData buffer) {

		var priority = buffer.read();
		var remoteStreamId = buffer.read();
		var command = buffer.read();
		var port = buffer.readInt16();
		buffer.rewind();

		return new ConnectRequest(priority, remoteStreamId, port, buffer);
	}

	public static BufferData createConnectConfirm(int priority, int remoteStreamId, int localStreamId) {

		var connectResponse = BufferData.create(4);
		connectResponse.writeInt8(priority);
		connectResponse.writeInt8(remoteStreamId);
		connectResponse.writeInt8(CONNECT_CONFIRM);
		connectResponse.writeInt8(localStreamId);

		return connectResponse;
	}

	public static ConnectConfirm parseConnectConfirm(BufferData buffer) {

		var priority = buffer.read();
		var localStreamId = buffer.read();
		var command = buffer.read();
		var remoteStreamId = buffer.read();
		buffer.rewind();

		return new ConnectConfirm(priority, localStreamId, remoteStreamId);
	}

	public static BufferData createConnectFail(int priority, int remoteStreamId, int errorCode) {

		var connectResponse = BufferData.create(5);
		connectResponse.writeInt8(priority);
		connectResponse.writeInt8(remoteStreamId);
		connectResponse.writeInt8(CONNECT_FAIL);
		connectResponse.writeInt16(errorCode);

		return connectResponse;
	}

	public static ConnectFail parseConnectFail(BufferData buffer) {

		var priority = buffer.read();
		var localStreamId = buffer.read();
		var command = buffer.read();
		var errorCode = buffer.readInt16();
		buffer.rewind();

		return new ConnectFail(priority, localStreamId, errorCode);
	}

	public static BufferData createDisconnectRequest(int priority, int remoteStreamId) {

		var disconnectRequest = BufferData.create(3);
		disconnectRequest.writeInt8(priority);
		disconnectRequest.writeInt8(remoteStreamId);
		disconnectRequest.writeInt8(DISCONNECT_REQUEST);
		return disconnectRequest;
	}
	
	public static BufferData createDisconnectConfirm(int priority, int remoteStreamId) {

		var disconnectConfirm = BufferData.create(3);
		disconnectConfirm.writeInt8(priority);
		disconnectConfirm.writeInt8(remoteStreamId);
		disconnectConfirm.writeInt8(DISCONNECT_CONFIRM);
		return disconnectConfirm;
	}
	
	public static BufferData createBufferIncrement(int priority, int remoteStreamId, int size) {

		var bufferIncrement = BufferData.create(5);
		bufferIncrement.writeInt8(priority);
		bufferIncrement.writeInt8(remoteStreamId);
		bufferIncrement.writeInt8(BUFFER_INCREMENT);
		bufferIncrement.writeInt16(size);
		return bufferIncrement;
	}
	
	public static BufferIncrement parseBufferIncrement(BufferData buffer) {

		var priority = buffer.read();
		var localStreamId = buffer.read();
		var command = buffer.read();
		var size = buffer.readInt16();
		buffer.rewind();

		return new BufferIncrement(priority, localStreamId, size);
	
	}

	/**
	 * ByteBuffer provided must be in READ mode and positioned where to copy from.
	 * 
	 * @param priority
	 * @param remoteStreamId
	 * @param buffer
	 * @param size
	 * @return
	 */
	public static BufferData createTransmitData(int priority, int remoteStreamId, ByteBuffer buffer, int size) {

		var transmitData = BufferData.create(5 + size);
		transmitData.writeInt8(priority);
		transmitData.writeInt8(remoteStreamId);
		transmitData.writeInt8(DATA);
		transmitData.writeInt16(size);
		
		transmitData.write(buffer.array(), buffer.position(), size);
		buffer.position(buffer.position() + size);
		
		return transmitData;
	}
	
	/**
	 * WARNING: this does not rewind the buffer and leaves it at the
	 * read position for the data contents.
	 * 
	 * @param buffer
	 * @return
	 */
	public static TransmitData parseTransmitData(BufferData buffer) {

		// buffer.reset();
		var priority = buffer.read();
		var localStreamId = buffer.read();
		var command = buffer.read();
		var size = buffer.readInt16();

		return new TransmitData(priority, localStreamId, size, buffer);
	}
}
