package xyz.arwhite.net.mux;

/***
 * Connects to, or listens on a given URI
 * 
 * When connecting it establishes the WebSocket and sets up the MuxHandler
 * The caller is returned an instance of the MuxHandler
 * 
 * When listening it accepts connections and sets up the MuxHandler on the connection
 * A set of queues is needed for each connection.
 * 
 * MuxHandler is given a pointer to the launching MuxEngine.
 * The MuxEngine contains the queues for the messaging.
 * 
 * @author Alan R. White
 *
 */
public class WebSocketMux {

}
