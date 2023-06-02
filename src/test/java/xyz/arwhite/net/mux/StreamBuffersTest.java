package xyz.arwhite.net.mux;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StreamBuffersTest {

	@Test
	void testConnectRequestBuffers() {
		
		var connBuff = StreamBuffers.createConnectRequest(12, 17, 8080);
		var cr = StreamBuffers.parseConnectRequest(connBuff);
		
		assertEquals(StreamBuffers.CONNECT_REQUEST,(byte) StreamBuffers.getBufferType(connBuff));
		assertEquals(17,StreamBuffers.getStreamId(connBuff));
		
		assertEquals(12,cr.priority());
		assertEquals(17,cr.remoteId());
		assertEquals(8080,cr.streamPort());
		

	}

	@Test
	void testConnectConfirmBuffers() {
		
		// used by responder to a connect request
		var connBuff = StreamBuffers.createConnectConfirm(12, 17, 23);
		
		// used by the initiator of the connect request
		var cc = StreamBuffers.parseConnectConfirm(connBuff);
		
		assertEquals(StreamBuffers.CONNECT_CONFIRM,(byte) StreamBuffers.getBufferType(connBuff));
		assertEquals(17,StreamBuffers.getStreamId(connBuff));
		
		assertEquals(12,cc.priority());
		assertEquals(17,cc.localId()); // note perspective of what is local and remote
		assertEquals(23,cc.remoteId());
		
	}
	
	@Test
	void testConnectFailBuffers() {
		
		// used by responder to a connect request
		var connBuff = StreamBuffers.createConnectFail(12, 17, 10010);
		
		// used by the initiator of the connect request
		var cf = StreamBuffers.parseConnectFail(connBuff);
		
		assertEquals(StreamBuffers.CONNECT_FAIL,(byte) StreamBuffers.getBufferType(connBuff));
		assertEquals(17,StreamBuffers.getStreamId(connBuff));
		
		assertEquals(12,cf.priority());
		assertEquals(17,cf.localId()); // note perspective of what is local and remote
		assertEquals(10010,cf.errorCode());
		
	}
}
