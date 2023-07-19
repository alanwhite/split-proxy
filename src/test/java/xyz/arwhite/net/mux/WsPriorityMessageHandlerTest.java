package xyz.arwhite.net.mux;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;

import com.mercateo.test.clock.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.time.ZoneId;
import java.util.ArrayList;
import java.time.Instant;
import io.helidon.common.buffers.BufferData;
import io.helidon.nima.websocket.WsSession;

class WsPriorityMessageHandlerTest {

	@Test
	@DisplayName("RxQueue Test Priority Order")
	void testRxPriorityOrder() {

		var mb = new WsPriorityMessageHandler();

		for (byte i = 0; i < 3; i++ ) {
			var buffer = BufferData.create(16);
			buffer.writeInt8(i); // priority
			mb.onMessage(null, buffer, true);
		}
		
		var pbq = mb.getRxQueue();
		
		for (byte i = 0; i < 3; i++ ) {
			var qe = pbq.poll();
			assertNotNull(qe);
			assertEquals(i,qe.priority());
		}

	}

	@Test
	@DisplayName("RxQueue Test Timestamp Order")
	void testRxTimestampOrder() {
		
		var moclock = TestClock.fixed(Instant.EPOCH, ZoneId.systemDefault());
		var mb = new WsPriorityMessageHandler(moclock);
		
		for (byte i = 0; i < 3; i++ ) {
			var buffer = BufferData.create(16);
			buffer.writeInt8(1); // priority
			moclock.fastForward(Duration.ofMillis(1));
			mb.onMessage(null, buffer, true);
		}
		
		var pbq = mb.getRxQueue();
		
		long lastTime = 0;
		for (byte i = 0; i < 3; i++ ) {
			var qe = pbq.poll();
			assertNotNull(qe);
			assertTrue(qe.timestamp() > lastTime);
			
			lastTime = qe.timestamp();
		}

	}

	@Test
	@DisplayName("RxQueue Test Sequence Order")
	void testRxSequenceOrder() {

		var moclock = TestClock.fixed(Instant.EPOCH, ZoneId.systemDefault());
		var mb = new WsPriorityMessageHandler(moclock);
		
		for (byte i = 0; i < 3; i++ ) {
			var buffer = BufferData.create(16);
			buffer.writeInt8(1); // priority
			mb.onMessage(null, buffer, true);
		}
		
		var pbq = mb.getRxQueue();
		
		long lastSeq = -1;
		for (byte i = 0; i < 3; i++ ) {
			var qe = pbq.poll();
			assertNotNull(qe);
			assertTrue(qe.sequence() > lastSeq);
			
			lastSeq = qe.sequence();
		}
		
	}
	
	@Test
	@DisplayName("RxQueue Test Complex Priority Order")
	void testRxComplexOrder() {
		
		var moclock = TestClock.fixed(Instant.EPOCH, ZoneId.systemDefault());
		var mb = new WsPriorityMessageHandler(moclock);
		
		var msg1 = BufferData.create(16);
		msg1.writeInt8(3);
		msg1.writeInt8(3); // expect 3rd
		mb.onMessage(null, msg1, true);
		
		var msg2 = BufferData.create(16);
		msg2.writeInt8(3);
		msg2.writeInt8(4); // expect 4th
		mb.onMessage(null, msg2, true);
		
		moclock.fastForward(Duration.ofMillis(1));
		var msg3 = BufferData.create(16);
		msg3.writeInt8(1);
		msg3.writeInt8(1); // expect 1st
		mb.onMessage(null, msg3, true);
		
		moclock.fastForward(Duration.ofMillis(1));
		var msg4 = BufferData.create(16);
		msg4.writeInt8(2);
		msg4.writeInt8(2); // expect 2nd
		mb.onMessage(null, msg4, true);
		
		var pbq = mb.getRxQueue();
		
		var qe = pbq.poll();
		assertNotNull(qe);
		assertEquals(1,qe.priority());
		assertEquals(1,qe.message().get(1));
		
		qe = pbq.poll();
		assertNotNull(qe);
		assertEquals(2,qe.priority());
		assertEquals(2,qe.message().get(1));
		
		qe = pbq.poll();
		assertNotNull(qe);
		assertEquals(3,qe.priority());
		assertEquals(3,qe.message().get(1));
		
		qe = pbq.poll();
		assertNotNull(qe);
		assertEquals(3,qe.priority());
		assertEquals(4,qe.message().get(1));
		
	}
	
	@Test
	@DisplayName("TxQueue Test Priority Order")
	void testTxPriorityOrder() {

		var mb = new WsPriorityMessageHandler();

		for (byte i = 1; i < 4; i++ ) {
			var buffer = BufferData.create(16);
			buffer.writeInt8(i); // priority
			assertTrue(mb.sendMessage(buffer));
		}
		
		var pbq = mb.getTxQueue();
		
		for (byte i = 1; i < 4; i++ ) {
			var qe = pbq.poll();
			assertNotNull(qe);
			assertEquals(i,qe.priority());
		}

	}

	@Test
	@DisplayName("TxQueue Test Timestamp Order")
	void testTxTimestampOrder() {
		
		var moclock = TestClock.fixed(Instant.EPOCH, ZoneId.systemDefault());
		var mb = new WsPriorityMessageHandler(moclock);
		
		for (byte i = 0; i < 3; i++ ) {
			var buffer = BufferData.create(16);
			buffer.writeInt8(1); // priority
			moclock.fastForward(Duration.ofMillis(1));
			assertTrue(mb.sendMessage(buffer));
		}
		
		var pbq = mb.getTxQueue();
		
		long lastTime = 0;
		for (byte i = 0; i < 3; i++ ) {
			var qe = pbq.poll();
			assertNotNull(qe);
			assertTrue(qe.timestamp() > lastTime);
			
			lastTime = qe.timestamp();
		}

	}

	@Test
	@DisplayName("TxQueue Test Sequence Order")
	void testTxSequenceOrder() {

		var moclock = TestClock.fixed(Instant.EPOCH, ZoneId.systemDefault());
		var mb = new WsPriorityMessageHandler(moclock);
		
		for (byte i = 0; i < 3; i++ ) {
			var buffer = BufferData.create(16);
			buffer.writeInt8(1); // priority
			assertTrue(mb.sendMessage(buffer));
		}
		
		var pbq = mb.getTxQueue();
		
		long lastSeq = -1;
		for (byte i = 0; i < 3; i++ ) {
			var qe = pbq.poll();
			assertNotNull(qe);
			assertTrue(qe.sequence() > lastSeq);
			
			lastSeq = qe.sequence();
		}
		
	}
	
	@Test
	@DisplayName("TxQueue Test Complex Priority Order")
	void testTxComplexOrder() {
		
		var moclock = TestClock.fixed(Instant.EPOCH, ZoneId.systemDefault());
		var mb = new WsPriorityMessageHandler(moclock);
		
		var msg1 = BufferData.create(16);
		msg1.writeInt8(3);
		msg1.writeInt8(3); // expect 3rd
		assertTrue(mb.sendMessage(msg1));
		
		var msg2 = BufferData.create(16);
		msg2.writeInt8(3);
		msg2.writeInt8(4); // expect 4th
		assertTrue(mb.sendMessage(msg2));
		
		moclock.fastForward(Duration.ofMillis(1));
		var msg3 = BufferData.create(16);
		msg3.writeInt8(1);
		msg3.writeInt8(1); // expect 1st
		assertTrue(mb.sendMessage(msg3));
		
		moclock.fastForward(Duration.ofMillis(1));
		var msg4 = BufferData.create(16);
		msg4.writeInt8(2);
		msg4.writeInt8(2); // expect 2nd
		assertTrue(mb.sendMessage(msg4));
		
		var pbq = mb.getTxQueue();
		
		var qe = pbq.poll();
		assertNotNull(qe);
		assertEquals(1,qe.priority());
		assertEquals(1,qe.message().get(1));
		
		qe = pbq.poll();
		assertNotNull(qe);
		assertEquals(2,qe.priority());
		assertEquals(2,qe.message().get(1));
		
		qe = pbq.poll();
		assertNotNull(qe);
		assertEquals(3,qe.priority());
		assertEquals(3,qe.message().get(1));
		
		qe = pbq.poll();
		assertNotNull(qe);
		assertEquals(3,qe.priority());
		assertEquals(4,qe.message().get(1));
		
	}
	
	// test that when we send messages in they get added to the WebSocket in the right order
	// we've tested various scenarios already so this is about the plumbing working
	
	@Test
	@DisplayName("TxQueue Test Consumption")
	void testTxConsumption() throws InterruptedException {

		var moclock = TestClock.fixed(Instant.EPOCH, ZoneId.systemDefault());
		var mb = new WsPriorityMessageHandler(moclock);
		ArrayList<BufferData> bufList = new ArrayList<BufferData>();
		
		mb.onOpen(new WsSession() {

			@Override
			public WsSession send(String text, boolean last) {
				return null;
			}

			@Override
			public WsSession send(BufferData bufferData, boolean last) {
				bufList.add(bufferData);
				return this;
			}

			@Override
			public WsSession ping(BufferData bufferData) {
				return null;
			}

			@Override
			public WsSession pong(BufferData bufferData) {
				return null;
			}

			@Override
			public WsSession close(int code, String reason) {
				return null;
			}

			@Override
			public WsSession terminate() {
				return null;
			}
			
		});

		Thread.sleep(Duration.ofSeconds(1));
		
		var msg1 = BufferData.create(16);
		msg1.writeInt8(3);
		assertTrue(mb.sendMessage(msg1));
		
		var msg2 = BufferData.create(16);
		msg2.writeInt8(3);
		assertTrue(mb.sendMessage(msg2));
		
		moclock.fastForward(Duration.ofMillis(1));
		var msg3 = BufferData.create(16);
		msg3.writeInt8(1);
		assertTrue(mb.sendMessage(msg3));
		
		moclock.fastForward(Duration.ofMillis(1));
		var msg4 = BufferData.create(16);
		msg4.writeInt8(2);
		assertTrue(mb.sendMessage(msg4));
		
		var msg5 = BufferData.create(16);
		msg5.writeInt8(3);
		assertTrue(mb.sendMessage(msg5));
		
		var msg6 = BufferData.create(16);
		msg6.writeInt8(3);
		assertTrue(mb.sendMessage(msg6));
		
		moclock.fastForward(Duration.ofMillis(1));
		var msg7 = BufferData.create(16);
		msg7.writeInt8(1);
		assertTrue(mb.sendMessage(msg7));
		
		moclock.fastForward(Duration.ofMillis(1));
		var msg8 = BufferData.create(16);
		msg8.writeInt8(2);
		assertTrue(mb.sendMessage(msg8));
	
		mb.drainTxQueue();
		
		mb.onClose(null, 0, null);
		assertEquals(8,bufList.size());
		
		// order is unpredictable due to timing between threads
	}
	
	
	// ensure that when stop() is called locally that it informs all
	
	@Test
	@DisplayName("Controlled Stop")
	void testStop() throws InterruptedException {

		var moclock = TestClock.fixed(Instant.EPOCH, ZoneId.systemDefault());
		var mb = new WsPriorityMessageHandler(moclock);
		ArrayList<BufferData> bufList = new ArrayList<>();
		ArrayList<Boolean> closeList = new ArrayList<>();
		
		mb.onOpen(new WsSession() {

			@Override
			public WsSession send(String text, boolean last) {
				return null;
			}

			@Override
			public WsSession send(BufferData bufferData, boolean last) {
				bufList.add(bufferData);
				return this;
			}

			@Override
			public WsSession ping(BufferData bufferData) {
				return null;
			}

			@Override
			public WsSession pong(BufferData bufferData) {
				return null;
			}

			@Override
			public WsSession close(int code, String reason) {
				closeList.add(true);
				return null;
			}

			@Override
			public WsSession terminate() {
				return null;
			}
			
		});

		Thread.sleep(Duration.ofSeconds(1));
		
		var msg1 = BufferData.create(16);
		msg1.writeInt8(3);
		assertTrue(mb.sendMessage(msg1));
		
		var msg2 = BufferData.create(16);
		msg2.writeInt8(3);
		assertTrue(mb.sendMessage(msg2));
		
		moclock.fastForward(Duration.ofMillis(1));
		var msg3 = BufferData.create(16);
		msg3.writeInt8(1);
		assertTrue(mb.sendMessage(msg3));
		
		moclock.fastForward(Duration.ofMillis(1));
		var msg4 = BufferData.create(16);
		msg4.writeInt8(2);
		assertTrue(mb.sendMessage(msg4));
		
		var msg5 = BufferData.create(16);
		msg5.writeInt8(3);
		assertTrue(mb.sendMessage(msg5));
		
		var msg6 = BufferData.create(16);
		msg6.writeInt8(3);
		assertTrue(mb.sendMessage(msg6));
		
		moclock.fastForward(Duration.ofMillis(1));
		var msg7 = BufferData.create(16);
		msg7.writeInt8(1);
		assertTrue(mb.sendMessage(msg7));
		
		moclock.fastForward(Duration.ofMillis(1));
		var msg8 = BufferData.create(16);
		msg8.writeInt8(2);
		assertTrue(mb.sendMessage(msg8));
	
		mb.stop();
		
		assertEquals(1,closeList.size());
		assertEquals(8,bufList.size());
		
		// order is unpredictable due to timing between threads
	}
	
}
