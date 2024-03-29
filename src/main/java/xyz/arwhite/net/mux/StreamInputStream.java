package xyz.arwhite.net.mux;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import xyz.arwhite.net.mux.StreamController.TransmitData;

// TODO: how do we close this and terminate any threads awaiting data ?

public class StreamInputStream extends InputStream {

	static private final Logger logger = Logger.getLogger(StreamInputStream.class.getName());

	private enum BufferMode { READ, WRITE };
	private BufferMode mode = BufferMode.WRITE;
	private ByteBuffer transitBuffer;
	private AtomicInteger available = new AtomicInteger(0);
	private final ReentrantLock bufferLock = new ReentrantLock();
	private Condition dataAvailableToRead = bufferLock.newCondition();
	private volatile boolean closed = false;

	private final LinkedTransferQueue<Integer> freeNotificationQueue = new LinkedTransferQueue<>();

	public StreamInputStream(int capacity) {
		transitBuffer = ByteBuffer.allocate(capacity);
	}

	/**
	 * Writes the data from the remote peer into the buffer 
	 * 
	 * @param incoming
	 */
	public void writeFromPeer(TransmitData incoming) {
		logger.log(Level.FINE,"writeFromPeer");

		if ( closed )
			return;

		try {
			bufferLock.lock();

			if ( mode != BufferMode.WRITE ) {
				mode = BufferMode.WRITE;
				transitBuffer.compact();
			}	

			var incomingLength = incoming.size();

			incoming.buffer().read(transitBuffer.array(), transitBuffer.position(), incomingLength);
			transitBuffer.position(transitBuffer.position() + incomingLength);

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
		logger.log(Level.FINE,"read()");

		if ( closed )
			throw( new IOException("stream is closed") );

		int data = -1;

		try {
			bufferLock.lock();

			if ( closed )
				throw( new IOException("stream is closed") );

			while ( available.get() < 1 ) {
				dataAvailableToRead.await();

				if ( closed )
					throw( new IOException("stream is closed") );
			}

			if ( mode != BufferMode.READ ) {
				mode = BufferMode.READ;
				transitBuffer.flip();
			}	

			data = transitBuffer.get();

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
		logger.log(Level.FINE,"read(byte[] b, int off, int len)");

		if ( closed )
			throw( new IOException("stream is closed") );

		int bytesRead = len;

		try {
			bufferLock.lock();

			if ( closed )
				throw( new IOException("stream is closed") );

			while ( available.get() < 1 ) {
				dataAvailableToRead.await();

				if ( closed )
					throw( new IOException("stream is closed") );
			}

			if ( mode != BufferMode.READ ) {
				mode = BufferMode.READ;
				transitBuffer.flip();
			}	

			if ( bytesRead > transitBuffer.remaining() )
				bytesRead = transitBuffer.remaining();

			transitBuffer.get(b, off, bytesRead);

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

		try {
			bufferLock.lock();
			dataAvailableToRead.signalAll();
			
		} finally {
			bufferLock.unlock();
		}
	}

	@Override
	public boolean markSupported() {
		return false;
	}

	public LinkedTransferQueue<Integer> getFreeNotificationQueue() {
		return freeNotificationQueue;
	}


}
