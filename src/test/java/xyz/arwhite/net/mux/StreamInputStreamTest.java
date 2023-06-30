package xyz.arwhite.net.mux;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.Thread.State;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.helidon.common.buffers.BufferData;
import xyz.arwhite.net.mux.StreamController.TransmitData;

class StreamInputStreamTest {

	@Test
	void testWritingDataChangesAvailable() {
		var inp = new StreamInputStream(4096);

		assertDoesNotThrow(() -> {
			assertEquals(0,inp.available());
		});


		var buff = BufferData.create(12);
		byte[] bytes = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12 };
		buff.write(bytes);
		
		var td = new TransmitData(0, 0, 12, buff);
		inp.writeFromPeer(td);

		assertDoesNotThrow(() -> {
			assertEquals(12,inp.available());
		});

	}

	@Test
	void testReadByte() {
		var inp = new StreamInputStream(4096);

		var buff = BufferData.create(12);
		byte[] bytes = { 83, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12 };
		buff.write(bytes);
		
		var td = new TransmitData(0, 0, 12, buff);
		inp.writeFromPeer(td);
		
		assertDoesNotThrow(() -> {
			assertEquals(83,inp.read());
		});
		
		assertDoesNotThrow(() -> {
			assertEquals(11,inp.available());
		});

	}

	@Test
	void testInterleavedReadWrite() {
		var inp = new StreamInputStream(4096);

		var buff = BufferData.create(12);
		byte[] bytes = { 83, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12 };
		buff.write(bytes);
		
		var td = new TransmitData(0, 0, 12, buff);
		inp.writeFromPeer(td);
		
		assertDoesNotThrow(() -> {
			assertEquals(83,inp.read());
		});

		bytes[0] = 76;
		buff.reset();
		buff.write(bytes);
		
		inp.writeFromPeer(td);

		assertDoesNotThrow(() -> {
			assertEquals(23,inp.available());
		});

		byte[] data = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };

		assertDoesNotThrow(() -> {
			inp.read(data);
		});

		assertEquals(2, data[0]);
		assertEquals(76, data[11]);
	}

	@Test
	void testWaitForData() {
		var inp = new StreamInputStream(4096);

		var x = new CompletableFuture<Integer>();
		var y = new CompletableFuture<Integer>();
		var t = Thread.ofVirtual().start(() -> {
			assertDoesNotThrow(() -> {
				x.complete(1);
				assertEquals(83,inp.read());
				y.complete(1);
			});
		});

		// wait for thread to be started
		assertDoesNotThrow(() -> {
			assertEquals(1,x.get(3, TimeUnit.SECONDS));
		});
		
		assertEquals(false,y.isDone());
		
		// give thread a chance to move to waiting for IO, and check it has
		assertDoesNotThrow(() -> Thread.sleep(Duration.ofSeconds(1)) );
		assertEquals(State.WAITING, t.getState());

		var buff = BufferData.create(12);
		byte[] bytes = { 83, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12 };
		buff.write(bytes);
		
		var td = new TransmitData(0, 0, 12, buff);
		inp.writeFromPeer(td);
		
		assertDoesNotThrow(() -> {
			assertEquals(1,y.get(3, TimeUnit.SECONDS));
		});
	}
	
	@Test
	void testFreeNotification() {
		var inp = new StreamInputStream(4096);

		var x = new CompletableFuture<Integer>();
		var y = new CompletableFuture<Integer>();
		var t = Thread.ofVirtual().start(() -> {
			assertDoesNotThrow(() -> {
				var q = inp.getFreeNotificationQueue();
				x.complete(1);
				var f = q.take();
				assertEquals(1,f);
				y.complete(1);
			});
		});
		
		// wait for thread to be started
		assertDoesNotThrow(() -> {
			assertEquals(1,x.get(3, TimeUnit.SECONDS));
		});
		
		assertEquals(false,y.isDone());
		
		assertDoesNotThrow(() -> Thread.sleep(Duration.ofSeconds(1)));
		assertEquals(State.WAITING, t.getState());

		var buff = BufferData.create(12);
		byte[] bytes = { 83, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12 };
		buff.write(bytes);
		
		var td = new TransmitData(0, 0, 12, buff);
		inp.writeFromPeer(td);
		
		assertDoesNotThrow(() -> {
			assertEquals(83,inp.read());
		});
		
		assertDoesNotThrow(() -> {
			assertEquals(1,y.get(3, TimeUnit.SECONDS));
		});

	}
}
