package xyz.arwhite.net.mux;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StreamServerTest {

	@BeforeAll
	static void setUpBeforeClass() throws Exception {
	}

	@AfterAll
	static void tearDownAfterClass() throws Exception {
	}

	@BeforeEach
	void setUp() throws Exception {
	}

	@AfterEach
	void tearDown() throws Exception {
	}

	@Test
	@DisplayName("Registering StreamServer on busy port")
	void registeringOnUsedPort() {

		assertThrows(IllegalArgumentException.class, 
				() -> new StreamServer(new StreamController() {
					
					@Override
					public boolean registerStreamServer(int port, StreamServer server) {
						return false;
					}

				}, 258));

	}

	@Test
	@DisplayName("Deregistering StreamServer on free port")
	void deregisteringFreePort() {

		assertDoesNotThrow( 
				() -> { 
					var ss = new StreamServer(new StreamController() {

						@Override
						public boolean deregisterStreamServer(int port) {
							return true;
						}

						@Override
						public boolean registerStreamServer(int port, StreamServer server) {
							return true;
						}

					}, 258);

					ss.close();
				}

				);

	}

	@Test
	@DisplayName("Stream passed through to Accept")
	void streamPassedToAccept() {

		assertDoesNotThrow( 
				() -> { 
					var sc = new StreamController() {

						@Override
						public boolean deregisterStreamServer(int port) {
							return true;
						}

						@Override
						public boolean registerStreamServer(int port, StreamServer server) {
							return true;
						}

					}; 
					
					var ss = new StreamServer(sc, 258);

					// set up listening for a new Stream
					var t = Thread.ofVirtual().start(new Runnable() {

						@Override
						public void run() {
							assertDoesNotThrow(() -> ss.accept());
						}

					});

					// pop a Stream in
					ss.connectStream(new Stream(sc, 0, 0, 0));

					// wait for thread to terminate
					assertTrue(t.join(Duration.ofSeconds(1)));
				}

				);
	}
}
