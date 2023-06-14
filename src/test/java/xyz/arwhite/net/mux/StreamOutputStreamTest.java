package xyz.arwhite.net.mux;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.lang.Thread.State;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class StreamOutputStreamTest {

	@Test
	void testSendBufferFlow() throws IOException {

		var sq = new ArrayBlockingQueue<Integer>(12);

		Stream s = new Stream() {

			@Override
			protected void sendData(ByteBuffer buffer, int size) {
				StreamBuffers.createTransmitData(0, 0, buffer, size);
				sq.offer(size);
			}

		};

		StreamController sc = new StreamController() {

		};

		// probably need to fake a connect somehow ... shall see

		// set up outputstream
		s.setStreamController(sc);
		StreamOutputStream o = (StreamOutputStream) s.getOutputStream();
		assertNotNull(o);

		// write 2048 bytes to outputstream
		// write will not stall as transit buffer will accommodate
		var b2048 = ByteBuffer.allocate(2048);

		assertDoesNotThrow(() -> o.write(b2048.array(), 0, 2048));

		// sender then offloads the 2048 leaving space for 4096 in the 
		// transit buffer

		AtomicInteger i = new AtomicInteger(0);
		assertDoesNotThrow(() -> i.addAndGet(sq.take()));
		assertEquals(2048,i.get());

		// quota = 2048 avbl, transit = 4096 free


		// write 4096 bytes
		// write won't stall as there's space in transit

		var b4096 = ByteBuffer.allocate(4096);
		assertDoesNotThrow(() -> o.write(b4096.array(), 0, 4096));
		i.set(0);
		assertDoesNotThrow(() -> i.addAndGet(sq.take()));
		assertEquals(2048,i.get());

		// but only 2048 will send
		// quota = 0, transit = 2048 free

		// next attempt to write to the transit buffer will stall
		// if > 2048, so send 4096, 2048 of it will get written
		// to the transit buffer, but because nothing gets offloaded
		// the write will pause as it still has 2048 to copy in

		var x = new CompletableFuture<Integer>();
		var y = new CompletableFuture<Integer>();
		var t = Thread.ofVirtual().start(() -> {
			assertDoesNotThrow(() -> {
				x.complete(1);
				assertDoesNotThrow(() -> o.write(b4096.array(), 0, 4096));
				y.complete(1);
			});
		});

		// wait for thread to be started
		assertDoesNotThrow(() -> {
			assertEquals(1,x.get(3, TimeUnit.SECONDS));
		});

		assertEquals(false,y.isDone());
		assertEquals(State.WAITING, t.getState());

		// send buffer increment of 1024
		// observe additional 1024 being sent
		// write still stalled
		o.increaseRemoteAvailable(1024);

		i.set(0);
		assertDoesNotThrow(() -> i.addAndGet(sq.take()));
		assertEquals(1024,i.get());

		// this will free up 1024 in the transit buffer
		// still not enough to liberate the write

		assertEquals(false,y.isDone());
		assertEquals(State.WAITING, t.getState());

		// send additional buffer increment of 1024
		// observe additional 1024 being sent
		// write is freed
		o.increaseRemoteAvailable(1024);

		i.set(0);
		assertDoesNotThrow(() -> i.addAndGet(sq.take()));
		assertEquals(1024,i.get());

		// this will free up 1024 in the transit buffer
		// liberating the write

		assertDoesNotThrow(() -> {
			assertEquals(1,y.get(3, TimeUnit.SECONDS));
		});
		
		// send buffer increment of 4096
		o.increaseRemoteAvailable(4096);
		// observe 4096 being sent
	
		// transitbuffer should be empty now
	}

}
