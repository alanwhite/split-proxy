package xyz.arwhite.net.mux;

import io.helidon.common.buffers.BufferData;

public class StreamBuffers {

	public static BufferData createConnectRequest(int priority, int localStreamId, int port) {
		
		var buffer = BufferData.create(16);
		buffer.writeInt8(priority);
		buffer.writeInt8(localStreamId);
		buffer.writeInt8(StreamController.CONNECT_REQUEST);
		buffer.writeInt16(port);
		return BufferData.create(16);
	}
	
	public static BufferData createConnectConfirm(int priority, int remoteStreamId, int localStreamId) {
		
		var connectResponse = BufferData.create(4);
		connectResponse.writeInt8(priority);
		connectResponse.writeInt8(remoteStreamId);
		connectResponse.writeInt8(StreamController.CONNECT_CONFIRM);
		connectResponse.writeInt8(localStreamId);
		
		return connectResponse;
	}
	
	public static BufferData createConnectFail(int priority, int remoteStreamId, int errorCode) {
		
		var connectResponse = BufferData.create(5);
		connectResponse.writeInt8(priority);
		connectResponse.writeInt8(remoteStreamId);
		connectResponse.writeInt8(StreamController.CONNECT_FAIL);
		connectResponse.writeInt16(errorCode);
		
		return connectResponse;
	}
}
