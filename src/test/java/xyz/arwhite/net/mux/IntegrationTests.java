package xyz.arwhite.net.mux;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.Executors;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.Callable;

import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;

import org.junit.jupiter.api.Test;

class IntegrationTests {

	@Test
	void defaultPattern() throws IOException {
		testPattern(ServerSocketFactory.getDefault(), SocketFactory.getDefault());
	}
	
	private void testPattern(ServerSocketFactory serverFactory, SocketFactory clientFactory) throws IOException {
		
		// Server thread(s)
		final ServerSocket server = serverFactory.createServerSocket(0);
		final var port = server.getLocalPort();

		Thread.ofVirtual().start(() -> {
			try {
				while(true) {
					var sock = server.accept();
					log("Server Accepted Connection");
					Thread.ofVirtual().start(() -> {
						try {
							log("Server Handling Accepted Connection");
							assertEquals("Hello World", new String(sock.getInputStream().readAllBytes()));
							log("Server Writing Response");
							sock.getOutputStream().write("Goodbye World".getBytes());
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
		List<Callable<Object>> tasks = new ArrayList<>(2);
		var exec = Executors.newVirtualThreadPerTaskExecutor();
		
		// final SocketFactory clientFactory = SocketFactory.getDefault();
				
		for (int i=1;i<20; i++) 
			tasks.add(() -> { 
				var client = clientFactory.createSocket("127.0.0.1", port);
				log("Client Writing Hello");
				client.getOutputStream().write("Hello World".getBytes());
				client.getOutputStream().close();
				assertEquals("Goodbye World", new String(client.getInputStream().readAllBytes()));
				log("Client Done");
				return null;
			});
		
		log("Launching Clients");
		assertDoesNotThrow(() -> exec.invokeAll(tasks));
		
		// tidy up - should cause exception in accept and thread exits
		log("Closing Server");
		server.close();
		
		log("Exit");
	}
	
	private void log(String msg) {
		// System.out.println(Thread.currentThread().threadId()+": "+msg);
	}

}
