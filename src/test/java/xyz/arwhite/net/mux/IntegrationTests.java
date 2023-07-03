package xyz.arwhite.net.mux;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;

import org.junit.jupiter.api.Test;

class IntegrationTests {

	@Test
	void defaultPattern() throws IOException, InterruptedException {
		testPattern(ServerSocketFactory.getDefault(), SocketFactory.getDefault());
	}

	@Test
	void testMux() throws IOException, InterruptedException {
		
		var wsServerLink = new WsMessageLink.Builder()
				.withEndpoint("127.0.0.1")
				.withPort(3258)
				.listen();
		
		var linkServerMux = new StreamController.Builder()
				.withMessageLink(wsServerLink).build();
		
		var muxServerSocketFactory = new MuxServerSocketFactory.Builder()
				.withMux(linkServerMux)
				.build();
		
		var wsClientLink = new WsMessageLink.Builder()
				.withEndpoint("127.0.0.1")
				.withPort(3258)
				.connect();
		
		var linkClientMux = new StreamController.Builder()
				.withMessageLink(wsClientLink)
				.build();
		
		testPattern(muxServerSocketFactory,SocketFactory.getDefault());
	}

	private void testPattern(ServerSocketFactory serverFactory, SocketFactory clientFactory) throws IOException, InterruptedException {

		// Server thread(s)
		final ServerSocket server = serverFactory.createServerSocket(0, 100);
		final var port = server.getLocalPort();

		Thread.ofVirtual().start(() -> {
			try {
				while(true) {
					var sock = server.accept();
					log("Server Accepted Connection");
					Thread.ofVirtual().start(() -> {
						try {
							log("Server Handling Accepted Connection");
							assertEquals(1, sock.getInputStream().read());
							log("Server Writing Response");
							sock.getOutputStream().write(2);
							sock.close();
						} catch (IOException e) {
							e.printStackTrace();
						}
					});
				}
			} catch (IOException e) {
				if ( !e.getMessage().equalsIgnoreCase("socket closed") )
					e.printStackTrace();
			}
		});

		// Client threads
		List<Callable<Long>> tasks = new ArrayList<>(2);
		var exec = Executors.newVirtualThreadPerTaskExecutor();

		// final SocketFactory clientFactory = SocketFactory.getDefault();

		for (int i=0;i<100; i++) 
			tasks.add(() -> {
				long start = System.nanoTime();
				var client = clientFactory.createSocket("127.0.0.1", port);
				log("Client Writing Hello");
				client.getOutputStream().write(1);
				assertEquals(2, client.getInputStream().read());
				log("Client Done");
				return System.nanoTime() - start;
			});

		log("Launching Clients");
		assertDoesNotThrow(() -> {
			var timings = exec.invokeAll(tasks);

			List<Long> timingList = timings.stream().mapToLong(t -> {
				try {
					return t.get();
				} catch (InterruptedException | ExecutionException e) {
					e.printStackTrace();
					return 0;
				}
			}).boxed().collect(Collectors.toList());

			var slowest = timingList.stream().mapToLong(v -> v).max();
			var fastest = timingList.stream().mapToLong(v -> v).min();
			var average = timingList.stream().mapToLong(v -> v).average();

			System.out.println("average = "+average.getAsDouble()+
					", fastest = "+fastest.getAsLong()+", slowest "+slowest.getAsLong());

		});

		// tidy up - should cause exception in accept and thread exits
		log("Closing Server");
		// Thread.sleep(Duration.ofSeconds(1));
		server.close();

		log("Exit");
	}

	private void log(String msg) {
		// System.out.println(Thread.currentThread().threadId()+": "+msg);
	}

}
