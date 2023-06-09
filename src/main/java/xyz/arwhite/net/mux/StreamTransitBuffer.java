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

	private enum BufferMode { NONE, READ, WRITE };
	private BufferMode mode = BufferMode.NONE;
	private ByteBuffer buffer;
	private AtomicInteger available = new AtomicInteger(0);
	private final ReentrantLock bufferLock = new ReentrantLock();
	private Condition dataAvailableToRead = bufferLock.newCondition();
	
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
		
		// gets the lock on the byte buffer
		// checks if it's in write mode, if not switch mode, compact it
		// copy the data from incoming into byte buffer
		
		// increment the available count
		
		// make the data available condition true

		// release lock
	}
	
	/**
	 * Reads a single byte from the buffer, blocking if empty
	 * @return value of byte of data read or integer -1 if closed
	 */
	@Override
	public int read() throws IOException {
		
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
		
		// gets the lock on the byte buffer
		// check if available > 0 else await condition that there's data available
		// checks if it's in read mode, if not switch, flip it
		// copy the first byte out of the byte buffer
		// decrement available count

		// release lock
		// return byte
		
		return data;
	}

	@Override
	public int read(byte[] b) throws IOException {
		return read(b, 0, b.length);
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		
		int bytesRead = len;
		
		try {
			bufferLock.lock();
			
			while ( available.get() < 1 )
				dataAvailableToRead.await();
			
			if ( mode != BufferMode.READ ) {
				mode = BufferMode.READ;
				buffer.flip();
			}	
		
			if ( bytesRead < buffer.remaining() )
				bytesRead = buffer.remaining();
			
			buffer.get(b, off, bytesRead);
			
			available.addAndGet(-bytesRead);
			
			// do something that informs the remote peer that there's bytesRead free in the buffer
			freeNotificationQueue.add(Integer.valueOf(bytesRead));
			
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			bufferLock.unlock();
		}
		
		return bytesRead;
	}

	@Override
	public int available() throws IOException {
		return available.get();
	}

	@Override
	public void close() throws IOException {
		// TODO Auto-generated method stub
		super.close();
	}

	@Override
	public boolean markSupported() {
		return false;
	}

	public LinkedTransferQueue<Integer> getFreeNotificationQueue() {
		return freeNotificationQueue;
	}
	
	// why not using pipedinput and output streams?
	// we need to know how much is left in a buffer, and importantly
	// when data is read out of the buffer in order to control flow
	// all the way back to the remote peer. If the pipeline hangs
	// then that's ok, as it's not blocking the whole websocket
	// problem becomes when the remote peer keeps sending data when it
	// has a block ... the peerIncoming buffer will fill, and
	// data will be dropped by the mux handler as it has nowhere to 
	// store it. Maybe the available counts are useful, can these
	// tell us when data has been read off, so we can send a buff ctl
	// message back to the remote peer and have a nominal buff size.
	
	// we wouldn't have control of the receive buffer size on the 
	// stream to be able to manage flow control to say whoah to the peer
	
	// when the peer thread writes in, there should always be room
	// in the buffer if flow control is working. We should check there is
	// room .. if not it's an exception and defect somewhere.
	
	
}
