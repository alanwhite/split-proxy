package xyz.arwhite.net.mux;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Clock;
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

}
