package xyz.arwhite.net.mux;

import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.websocket.client.WsClient;
import io.helidon.nima.websocket.webserver.WsRouting;

public class WsMessageLink {

	static private final Logger logger = Logger.getLogger(WsMessageLink.class.getName());
	
	// this is the only object that matters and is common
	// whether the link was launched in client or server mode
	private MessageLinkAdapter messageBroker;
	private WebServer server;
	private boolean isServer;
	private int localPort;

	private WsMessageLink(Builder builder) {
		logger.log(Level.FINE,"WsMessageLink");
		
		this.messageBroker = builder.messageBroker;
		this.server = builder.server;
		this.isServer = server != null;
		if ( isServer ) 
			this.localPort = server.port();
	}

	/**
	 * Stops the connected WebSocket
	 */
	public void stop() {
		logger.log(Level.FINE,"stop");
				
		messageBroker.stop();

		if ( isServer )
			server.stop();
	}

	public static class Builder {

		MessageLinkAdapter messageBroker;
		String endpoint;
		String host = "127.0.0.1";
		int port = -1;
		WebServer server;
		WsClient client;

		public Builder withMessageBroker(MessageLinkAdapter messageBroker) {
			this.messageBroker = messageBroker;
			return this;
		}

		public Builder withEndpoint(String endpoint) {
			this.endpoint = endpoint;
			return this;
		}

		public Builder withHost(String host) {
			this.host = host;
			return this;
		}

		public Builder withPort(int port) {
			this.port = port;
			return this;
		}

		/**
		 * Returns a WsMessageLink that initiated and connected to a remote peer
		 * @return
		 */
		public WsMessageLink connect() {
			logger.log(Level.FINE,"connect");
			
			client = WsClient.builder().build();
			client.connect("http://"+host+":"+port+"/"+endpoint, messageBroker);

			var wsml = new WsMessageLink(this);
			return wsml;
		}

		/**
		 * Returns a WsMessageLink that is listening for incoming connections
		 * @return
		 */
		public WsMessageLink listen() {
			logger.log(Level.FINE,"listen");
			
			if ( port == -1 )
				server = WebServer.builder()
				.host(this.host)
				.addRouting(WsRouting.builder().endpoint(endpoint, messageBroker))
				.start();
			else
				server = WebServer.builder()
				.host(this.host)
				.port(port)
				.addRouting(WsRouting.builder().endpoint(endpoint, messageBroker))
				.start();

			var wsml = new WsMessageLink(this);
			return wsml;
		}
	}

	public MessageLinkAdapter getMessageBroker() {
		return messageBroker;
	}

	public int getLocalPort() {
		return localPort;
	}
}
