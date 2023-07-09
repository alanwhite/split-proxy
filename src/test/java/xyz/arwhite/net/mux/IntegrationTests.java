package xyz.arwhite.net.mux;

import java.util.logging.*;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;

import org.junit.jupiter.api.Test;

class IntegrationTests {

	@Test
	void defaultPattern() throws IOException, InterruptedException {
		testPattern(ServerSocketFactory.getDefault(), SocketFactory.getDefault(),1);
	}

	@Test
	void testMux() throws IOException, InterruptedException {
		
		var logger = Logger.getLogger("xyz.arwhite.net");
		var c = new ConsoleHandler();
		logger.addHandler(c);
		c.setLevel(Level.FINEST);
		logger.setLevel(Level.FINER);
		
		var serverHandler = new WsPriorityMessageHandler();
		var wsServerLink = new WsMessageLink.Builder()
				.withMessageBroker(serverHandler)
				.withEndpoint("127.0.0.1")
				.listen();
		
		var linkServerMux = new StreamController.Builder()
				.withMessageLink(wsServerLink).build();
		
		var muxServerSocketFactory = new MuxServerSocketFactory.Builder()
				.withMux(linkServerMux)
				.build();
		
		var clientHandler = new WsPriorityMessageHandler();
		var wsClientLink = new WsMessageLink.Builder()
				.withMessageBroker(clientHandler)
				.withEndpoint("127.0.0.1")
				.withPort(wsServerLink.getLocalPort())
				.connect();
		
		var linkClientMux = new StreamController.Builder()
				.withMessageLink(wsClientLink)
				.build();
		
		var muxSocketFactory = new MuxSocketFactory.Builder()
				.withMux(linkClientMux)
				.build();
		
//		var logM = LogManager.getLogManager();
//		logM.getLoggerNames().asIterator().forEachRemaining(s -> System.out.println(s));


		
		
		testPattern(muxServerSocketFactory,muxSocketFactory,1);
	}

	private void testPattern(ServerSocketFactory serverFactory, SocketFactory clientFactory, int iterations) 
			throws IOException, InterruptedException {

		// Server thread(s)
		final ServerSocket server = serverFactory.createServerSocket(0, iterations > 50 ? iterations : 0);
		final var port = server.getLocalPort();

		Thread.ofVirtual().start(() -> {
			try {
				while(true) {
					var sock = server.accept();
					log("Server Accepted Connection");
					Thread.ofVirtual().start(() -> {
						try {
							log("Server Reading Hello");
							assertEquals(1, sock.getInputStream().read());
							log("Server Writing Response");
							sock.getOutputStream().write(2);
							log("Server Closing Connection");
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

		for (int i=0;i<iterations; i++) 
			tasks.add(() -> {
				long start = System.nanoTime();
				var client = clientFactory.createSocket("127.0.0.1", port);
				log("Client Writing Hello");
				client.getOutputStream().write(1);
				log("Client Reading Response");
				assertEquals(2, client.getInputStream().read());
				log("Client Done");
				// client.close();
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
		System.out.println(Thread.currentThread().threadId()+": "+msg);
	}

}
