package xyz.arwhite.net.mux;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;

import javax.naming.LimitExceededException;
import java.net.SocketTimeoutException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
				// send a connect confirm so it times out
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
	void testDirectConnect() {
		
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
				// send a connect confirm so it times out
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
		assertDoesNotThrow(() -> stream.connect(sa));	
		
	}

}
