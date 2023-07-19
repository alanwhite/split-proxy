package xyz.arwhite.net.mux;

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
	private boolean isServer = false;
	private String host;
	private int localPort;
	private String endpoint;

	private WsMessageLink(Builder builder) {
		logger.entering(this.getClass().getName(), "Constructor");

		this.messageBroker = builder.messageBroker;
		this.server = builder.server;
		this.isServer = this.server != null;
		this.localPort = builder.port;
		this.host = builder.host;

		logger.fine("WebSocket established: server="+this.server);
		
		logger.info("Websocket" + (isServer ? "s being served on " : " connected to ")
				+ this.host + ":" + localPort + "/" + endpoint);

		logger.exiting(this.getClass().getName(), "Constructor");
	}

	/**
	 * Stops the connected WebSocket
	 */
	public void stop() {
		logger.entering(this.getClass().getName(), "stop");

		messageBroker.stop();

		if ( isServer )
			server.stop();
		
		logger.exiting(this.getClass().getName(), "stop");
	}

	public static class Builder {

		MessageLinkAdapter messageBroker;
		String endpoint;
		String host = "127.0.0.1";
		int port = -1;
		WebServer server = null;
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
			logger.entering(this.getClass().getName(), "connect");

			client = WsClient.builder().build();
			client.connect("http://"+host+":"+port+"/"+endpoint, messageBroker);

			var wsml = new WsMessageLink(this);
			
			logger.exiting(this.getClass().getName(), "connect", wsml);
			return wsml;
		}

		/**
		 * Returns a WsMessageLink that is listening for incoming connections
		 * @return
		 */
		public WsMessageLink listen() {
			logger.entering(this.getClass().getName(), "listen");

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

			this.port = server.port();

			var wsml = new WsMessageLink(this);
			
			logger.exiting(this.getClass().getName(), "listen", wsml);
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
