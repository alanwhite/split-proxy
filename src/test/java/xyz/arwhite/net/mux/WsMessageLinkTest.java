package xyz.arwhite.net.mux;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.helidon.common.buffers.BufferData;

class WsMessageLinkTest {

	@Test
	void testConnections() throws InterruptedException {
		
		// build server
		var serverHandler = new WsPriorityMessageHandler();
		var server = new WsMessageLink.Builder()
				.withMessageBroker(serverHandler)
				.withEndpoint("127.0.0.1")
				.withPort(3258)
				.listen();
		
		assertNotNull(server);
		
		// build client
		
		var clientHandler = new WsPriorityMessageHandler();
		var client = new WsMessageLink.Builder()
				.withMessageBroker(clientHandler)
				.withEndpoint("127.0.0.1")
				.withPort(3258)
				.connect();
		
		// hook up to server incoming
		var rq = serverHandler.getRxQueue();
	
		// send a message from client to server
		var buffer = BufferData.create(12);
		buffer.writeInt8(1);
		buffer.writeInt16(258);
		assertTrue(clientHandler.sendMessage(buffer));
		
		// check it arrives on the server
		int i = 0;
		var msg = rq.poll();
		while ( msg == null ) {
			if ( ++i > 4 )
				fail("message took too long to arrive in server rq");
			Thread.sleep(Duration.ofSeconds(1));
			msg = rq.poll();
		}
		assertNotNull(msg);
		
		// check it's content
		assertEquals(1, msg.priority());
		msg.message().read();
		assertEquals(258,msg.message().readInt16());
		
		// check other way round, we can write from the server to the client
		
		// hook up to client incoming
		var cq = clientHandler.getRxQueue();
		
		// send a message from server to client
		var sbuffer = BufferData.create(12);
		sbuffer.writeInt8(1);
		sbuffer.writeInt16(248);
		assertTrue(serverHandler.sendMessage(sbuffer));

		// check it arrives on the client
		int j = 0;
		var cmsg = cq.poll();
		while ( cmsg == null ) {
			if ( ++j > 4 )
				fail("message took too long to arrive in client rq");
			Thread.sleep(Duration.ofSeconds(1));
			cmsg = cq.poll();
		}
		assertNotNull(cmsg);

		// check it's content
		assertEquals(1, cmsg.priority());
		cmsg.message().read();
		assertEquals(248,cmsg.message().readInt16());
		
		client.stop();
		server.stop();
		
	}

}
