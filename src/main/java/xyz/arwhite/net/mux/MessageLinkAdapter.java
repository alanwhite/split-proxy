package xyz.arwhite.net.mux;

import io.helidon.nima.websocket.WsListener;

/**
 * Aggregates interfaces needed to act as a message handler for WsMessageLink
 * @author Alan R. White
 *
 */
public abstract class MessageLinkAdapter implements WsListener, MessageBroker {

	public abstract void stop();
}
