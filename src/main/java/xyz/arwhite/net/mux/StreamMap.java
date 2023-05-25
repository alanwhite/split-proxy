package xyz.arwhite.net.mux;

import java.io.IOException;
import java.util.BitSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import javax.naming.LimitExceededException;

@SuppressWarnings("serial")
public class StreamMap extends ConcurrentHashMap<Integer, Stream> {

	/**
	 * Maximum number of concurrent streams
	 */
	private static final int MAX_STREAMS = 100;

	/**
	 * Tracks which entries in the map of sockets are used/free
	 */
	private final BitSet slots = new BitSet(128); 

	/**
	 * Ensure atomicity when accessing bitset tracking allocation of map entries
	 */
	private final ReentrantLock slotLock = new ReentrantLock();

	/*
	 * Maybe need to make the allocation and insertion an operation, as is freeing
	 * We shall see.
	 */
	
	/**
	 * Find an unused entry in the map
	 * @return
	 * @throws IOException
	 */
	public int allocNewStreamId() throws IOException {
		int localStreamId = -1;

		slotLock.lock();
		try {
			localStreamId = slots.nextClearBit(0);
			if ( localStreamId == -1 || localStreamId >= MAX_STREAMS ) 
				throw (new IOException(new LimitExceededException("stream id limit reached")));

			slots.set(localStreamId);
		} finally { // as soon as poss.
			slotLock.unlock();
		}
		
		return localStreamId;
		
	}
	
	public void freeStreamId(int streamId) throws IOException {

		if ( streamId >= MAX_STREAMS ) 
			throw (new IOException(new IllegalArgumentException("stream id exceeds limit")));
		
		slotLock.lock();
		try {
			if ( slots.get(streamId) ) 
				slots.clear(streamId);
			else
				throw (new IOException(new IllegalArgumentException("stream id not in use")));

		} finally { // as soon as poss.
			slotLock.unlock();
		}
	}
}
