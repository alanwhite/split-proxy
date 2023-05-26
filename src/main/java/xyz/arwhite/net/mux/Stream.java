package xyz.arwhite.net.mux;

// analagous to a Socket in the sockets world

// can't be a record, we need to update the remoteId when received

// probs have a queue in here for recvd IO, check for CC close confirms etc

public class Stream {

	private int localId;
	private int remoteId;
	
	/**
	 * The relative priority on the WebSocket of messages for
	 * this Stream.
	 */
	private int priority;
	
	public Stream(int localId, int remoteId, int priority) {
		this.setLocalId(localId);
		this.setRemoteId(remoteId);
		this.setPriority(priority);
	}

	
	
	public int getLocalId() {
		return localId;
	}

	public void setLocalId(int localId) {
		this.localId = localId;
	}

	public int getRemoteId() {
		return remoteId;
	}

	public void setRemoteId(int remoteId) {
		this.remoteId = remoteId;
	}

	public int getPriority() {
		return priority;
	}

	public void setPriority(int priority) {
		this.priority = priority;
	}
	
	
}
