package xyz.arwhite.net.mux;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.common.buffers.BufferData;

public class StreamTransitBuffer extends InputStream {

	private enum BufferMode { READ, WRITE };
	private BufferMode mode = BufferMode.WRITE;
	private ByteBuffer buffer;
	private AtomicInteger available = new AtomicInteger(0);
	private final ReentrantLock bufferLock = new ReentrantLock();
	private Condition dataAvailableToRead = bufferLock.newCondition();
	private boolean closed = false;
	
	private final LinkedTransferQueue<Integer> freeNotificationQueue = new LinkedTransferQueue<>();
	
	public StreamTransitBuffer(int capacity) {
		buffer = ByteBuffer.allocate(capacity);
	}
	
	/**
	 * Writes the data from the remote peer into the buffer 
	 * 
	 * @param incoming
	 */
	public void writeFromPeer(BufferData incoming) {
		
		if ( closed )
			return;
		
		try {
			bufferLock.lock();
			
			if ( mode != BufferMode.WRITE ) {
				mode = BufferMode.WRITE;
				buffer.compact();
			}	

			var incomingLength = incoming.available();

			incoming.read(buffer.array(), buffer.position(), incomingLength);
			buffer.position(buffer.position() + incomingLength);

			available.addAndGet(incomingLength);
			dataAvailableToRead.signalAll();
			
		} finally {
			bufferLock.unlock();
		}

	}
	
	/**
	 * Reads a single byte from the buffer, blocking if empty
	 * @return value of byte of data read or integer -1 if closed
	 */
	@Override
	public int read() throws IOException {
		
		if ( closed )
			throw( new IOException("stream is closed") );
		
		int data = -1;
		
		try {
			bufferLock.lock();
			
			while ( available.get() < 1 )
				dataAvailableToRead.await();
			
			if ( mode != BufferMode.READ ) {
				mode = BufferMode.READ;
				buffer.flip();
			}	
			
			data = buffer.get();
			
			available.decrementAndGet();
			
			freeNotificationQueue.add(Integer.valueOf(1));
			
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			bufferLock.unlock();
		}

		return data;
	}

	@Override
	public int read(byte[] b) throws IOException {
		return read(b, 0, b.length);
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		
		if ( closed )
			throw( new IOException("stream is closed") );
		
		int bytesRead = len;
		
		try {
			bufferLock.lock();
			
			while ( available.get() < 1 )
				dataAvailableToRead.await();
			
			if ( mode != BufferMode.READ ) {
				mode = BufferMode.READ;
				buffer.flip();
			}	
		
			if ( bytesRead > buffer.remaining() )
				bytesRead = buffer.remaining();

			buffer.get(b, off, bytesRead);
			
			available.addAndGet(-bytesRead);
			
			// do something that informs the remote peer that there's an additional bytesRead free in the buffer
			freeNotificationQueue.add(Integer.valueOf(bytesRead));
			
		} catch (InterruptedException e) {
			throw( new IOException(e) );
			
		} finally {
			bufferLock.unlock();
		}
		
		return bytesRead;
	}

	@Override
	public int available() throws IOException {
		
		if ( closed )
			throw( new IOException("stream is closed") );
		
		return available.get();
	}

	@Override
	public void close() throws IOException {
		closed = true;
	}

	@Override
	public boolean markSupported() {
		return false;
	}

	public LinkedTransferQueue<Integer> getFreeNotificationQueue() {
		return freeNotificationQueue;
	}
	
}
