package xyz.arwhite.net.mux;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;

import com.mercateo.test.clock.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.time.ZoneId;
import java.time.Instant;
import io.helidon.common.buffers.BufferData;

class WsMuxMessageBrokerTest {

	@Test
	@DisplayName("RxQueue Test Priority Order")
	void testRxPriorityOrder() {

		var mb = new WsMuxMessageBroker();

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
		var mb = new WsMuxMessageBroker(moclock);
		
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
		var mb = new WsMuxMessageBroker(moclock);
		
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
		var mb = new WsMuxMessageBroker(moclock);
		
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

}
