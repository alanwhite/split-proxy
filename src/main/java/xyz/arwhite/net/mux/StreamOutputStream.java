package xyz.arwhite.net.mux;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/*
 * Here the consumer writes to a mux'd Stream.
 * 
 * The writes can block if there's no space in the buffer used to send
 * data to the remote peer.
 * 
 * This must respect the flow control semantics, i.e. 
 * - start with the default available remote receive buffer size (4096 for each Stream)
 * - every time we send data we decrement the available remote space
 * - every time we receive a buffer increment we increase the size
 * 
 */

public class StreamOutputStream extends OutputStream {


	
	private enum BufferMode { READ, WRITE };
	private BufferMode mode = BufferMode.WRITE;
	private final ReentrantLock bufferLock = new ReentrantLock();

	private ByteBuffer transitBuffer;
	private AtomicInteger transitAvailable;
	private Condition dataAvailableToWrite = bufferLock.newCondition();
	
	private boolean closed = false;
	private AtomicInteger remoteFreeCapacity = new AtomicInteger(4096);
	private Condition remoteBufferHasFreeCapacity = bufferLock.newCondition();
	
	private StreamController streamController;
	private int remoteStreamId;
	
	public StreamOutputStream(int capacity, StreamController streamController, int remoteStreamId) {
		transitBuffer = ByteBuffer.allocate(capacity);
		transitAvailable = new AtomicInteger(capacity);
		this.streamController = streamController;
		this.remoteStreamId = remoteStreamId;
	}
	
	public void increaseRemoteAvailable(int size) {
		remoteFreeCapacity.addAndGet(size);
	}
	
	private void reduceRemoteAvailable(int size) {
		remoteFreeCapacity.addAndGet(-size);
	}
	
	private void decrementRemoteAvailable() {
		remoteFreeCapacity.decrementAndGet();
	}
	
	/*
	 * Need a method that reads data from the transit buffer
	 * and sends to the remote peer. It can only do this if
	 * the remote has buffer capacity, and there's data to be
	 * read from the transit buffer.
	 * 
	 * Order of conditions is important. Once there's capacity
	 * at the remote, only then is it worth seeing if anything
	 * to read.
	 * 
	 * When we read data out of the transit buffer to send we
	 * must decrement our tracking of the remote buffer size.
	 * 
	 * Once we've read data out we must also signal the condition
	 * that there's space in the local transit buffer.
	 */
	
	 /*
	  * Overall we need to think about this, do we inject some
	  * mechanism to allow this class to send to the websocket
	  * or introduce another intermediary? Probably best it go
	  * direct, so the info it needs is the remoteId and the 
	  * StreamController. We shall get these in the constructor.
	  */
	
	/*
	 * So we have the data to send in the transit buffer
	 * we need a BufferData formatted in which to send it
	 * format is usual header plus as much of the contents
	 * of the transmit buffer as flow control permits.
	 * 
	 * we should copy required amount into the BufferData 
	 * from the transmit buffer
	 * 
	 * // TODO: on the inputstream side we need to skip the header
	 * 
	 */
	
	@Override
	/**
	 * Copy data into transit buffer and signal data available to send
	 */
	public void write(int b) throws IOException {
				
		if ( closed ) 
			throw( new IOException("stream is closed") );
		
		try {
			bufferLock.lock();
			
			while ( remoteFreeCapacity.get() < 1 )
				dataAvailableToWrite.await();
			
			if ( mode != BufferMode.WRITE ) {
				mode = BufferMode.WRITE;
				transitBuffer.compact();
			}	
			
			// data = transitBuffer.get();
			
			decrementRemoteAvailable();
			
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			bufferLock.unlock();
		}


	}

	@Override
	public void write(byte[] b) throws IOException {
		
		if ( b == null )
			throw( new NullPointerException("buffer may not be null") );
		
		write(b, 0, b.length);
	}

	@Override
	/**
	 * Copy data into transit buffer and signal data available to send
	 */
	public void write(byte[] b, int off, int len) throws IOException {
			
		if ( b == null )
			throw( new NullPointerException("buffer may not be null") );
		
		if ( closed ) 
			throw( new IOException("stream is closed") );
		
		int bytesTransferred = 0;
		
		while(bytesTransferred > len) {
			
			var bytesToWrite = len;
			
		//	if ( bytesToWrite > remoteFreeCapacity)
			
			reduceRemoteAvailable(bytesToWrite); // or whatever subset we send
		}
	}

	@Override
	public void close() throws IOException {
		closed = true;
	}

	@Override
	public void flush() throws IOException {
		// wait until all transit buffer contents emptied
	}

}
