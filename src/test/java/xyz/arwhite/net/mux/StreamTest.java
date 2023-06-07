package xyz.arwhite.net.mux;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.SocketTimeoutException;
import java.io.IOException;

import javax.naming.LimitExceededException;

import org.junit.jupiter.api.Test;

import io.helidon.common.buffers.BufferData;

class StreamTest {

	@Test
	void testDirectConnectTimeout() {
		
		/*
		 * There are multiple ways to create and connect a stream.
		 * This tests:
		 * 
		 * new Stream()
		 * stream.connect(StreamSocketAddress, Timeout)
		 * 
		 * where StreamSocketAddress specifies the tunnel instance to use
		 */
		
		// need a mock stream controller
		
		var sc = new StreamController() {

			@Override
			public boolean send(BufferData buffer) {
				// expecting a connect request
				// do not return a connect confirm so it times out
				return true;
			}

			@Override
			protected int registerStream(Stream stream) throws LimitExceededException, IllegalArgumentException {
				return 17;
			}

			@Override
			protected boolean deregisterStream(int stream) {
				return true;
			}

		}; 
		
		var sa = new StreamSocketAddress(sc, 258);
		
		Stream stream = new Stream();
		assertThrows(SocketTimeoutException.class, () -> stream.connect(sa, 2000));	
		
	}
	
	@Test
	void testDirectConnectBeforeTimeout() {
		
		/*
		 * There are multiple ways to create and connect a stream.
		 * This tests:
		 * 
		 * new Stream()
		 * stream.connect(StreamSocketAddress, Timeout);
		 * 
		 * where StreamSocketAddress specifies the tunnel instance to use
		 */
		
		// need a mock stream controller
		
		var sc = new StreamController() {

			Stream victim;
			
			@Override
			public boolean send(BufferData buffer) {
				// expecting a connect request
				// send a connect confirm
				victim.getPeerIncoming().offer(
						StreamBuffers.createConnectConfirm(258, 27, 17)
						);
				return true;
			}

			@Override
			protected int registerStream(Stream stream) throws LimitExceededException, IllegalArgumentException {
				this.victim = stream;
				return 17;
			}

			@Override
			protected boolean deregisterStream(int stream) {
				return true;
			}

		}; 
		
		var sa = new StreamSocketAddress(sc, 258);
		
		Stream stream = new Stream();
		assertDoesNotThrow(() -> stream.connect(sa, 2000));	
		
	}
	
	@Test
	void testHappyPath() {
		
		/*
		 * There are multiple ways to create and connect a stream.
		 * This tests:
		 * 
		 * new Stream()
		 * stream.connect(StreamSocketAddress, Timeout);
		 * 
		 * where StreamSocketAddress specifies the tunnel instance to use
		 */
		
		// need a mock stream controller
		
		var sc = new StreamController() {

			Stream victim;
			
			@Override
			public boolean send(BufferData buffer) {
				switch( StreamBuffers.getBufferType(buffer) )
				{
				case StreamBuffers.CONNECT_REQUEST -> {
					victim.getPeerIncoming().offer(
							StreamBuffers.createConnectConfirm(258, 27, 17)
							);
				}
				case StreamBuffers.DISCONNECT_REQUEST -> {
					victim.getPeerIncoming().offer(
							StreamBuffers.createDisconnectConfirm(258, 17)
							);
				}
				
				}

				return true;
			}

			@Override
			protected int registerStream(Stream stream) throws LimitExceededException, IllegalArgumentException {
				this.victim = stream;
				return 17;
			}

			@Override
			protected boolean deregisterStream(int stream) {
				return true;
			}

		}; 
		
		var sa = new StreamSocketAddress(sc, 258);
		
		Stream stream = new Stream();
		
		// set default timeout for all operations to 3 seconds
		stream.setStreamTimeout(2 * 1000);
		
		assertDoesNotThrow(() -> stream.connect(sa));	
		assertDoesNotThrow(() -> stream.close());
		assertEquals(true, stream.isClosed());
		
		
	}
	
	@Test
	void testCloseTimeout() {
		
		// need a mock stream controller
		
		var sc = new StreamController() {

			Stream victim;
			
			@Override
			public boolean send(BufferData buffer) {
				switch( StreamBuffers.getBufferType(buffer) )
				{
				case StreamBuffers.CONNECT_REQUEST -> {
					victim.getPeerIncoming().offer(
							StreamBuffers.createConnectConfirm(258, 27, 17)
							);
				}
				case StreamBuffers.DISCONNECT_REQUEST -> {
					// do nothing so times out
				}
				
				}

				return true;
			}

			@Override
			protected int registerStream(Stream stream) throws LimitExceededException, IllegalArgumentException {
				this.victim = stream;
				return 17;
			}

			@Override
			protected boolean deregisterStream(int stream) {
				return true;
			}

		}; 
		
		var sa = new StreamSocketAddress(sc, 258);
		
		Stream stream = new Stream();
		
		// set default timeout for all operations to 3 seconds
		stream.setStreamTimeout(2 * 1000);
		
		assertDoesNotThrow(() -> stream.connect(sa));	
		assertThrows(SocketTimeoutException.class, () -> stream.close());
		assertEquals(true, stream.isClosed());
		
		
	}

	@Test
	void testCloseSequenceError() {
		
		// need a mock stream controller
		
		var sc = new StreamController() {

			Stream victim;
			
			@Override
			public boolean send(BufferData buffer) {
				switch( StreamBuffers.getBufferType(buffer) )
				{
				case StreamBuffers.CONNECT_REQUEST -> {
					victim.getPeerIncoming().offer(
							StreamBuffers.createConnectConfirm(258, 27, 17)
							);
				}
				case StreamBuffers.DISCONNECT_REQUEST -> {
					// send wrong thing
					victim.getPeerIncoming().offer(
							StreamBuffers.createConnectConfirm(258, 27, 17)
							);
				}
				
				}

				return true;
			}

			@Override
			protected int registerStream(Stream stream) throws LimitExceededException, IllegalArgumentException {
				this.victim = stream;
				return 17;
			}

			@Override
			protected boolean deregisterStream(int stream) {
				return true;
			}

		}; 
		
		var sa = new StreamSocketAddress(sc, 258);
		
		Stream stream = new Stream();
		
		// set default timeout for all operations to 3 seconds
		stream.setStreamTimeout(2 * 1000);
		
		assertDoesNotThrow(() -> stream.connect(sa));	
		assertThrows(IOException.class, () -> stream.close());
		assertEquals(true, stream.isClosed());
		
		
	}
}
