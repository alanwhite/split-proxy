package xyz.arwhite.net.mux;

import io.helidon.nima.webserver.WebServer;
import io.helidon.nima.websocket.client.WsClient;
import io.helidon.nima.websocket.webserver.WsRouting;

public class WsMessageLink {

	// this is the only object that matters and is common
	// whether the link was launched in client or server mode
	private MessageLinkAdapter messageBroker;
		
	private WsMessageLink(Builder builder) {
		this.messageBroker = builder.messageBroker;
	}
	
	/**
	 * Stops the connected WebSocket
	 */
	public void stop() {
		messageBroker.stop();
	}
	
	public static class Builder {
		
		MessageLinkAdapter messageBroker;
		String endpoint;
		String host = "127.0.0.1";
		int port = 3258;
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
}
