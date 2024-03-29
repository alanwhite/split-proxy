package xyz.arwhite.net.mux;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

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

	static private final Logger logger = Logger.getLogger(StreamOutputStream.class.getName());

	private enum BufferMode { READ, WRITE };
	private BufferMode mode = BufferMode.WRITE;
	private final ReentrantLock bufferLock = new ReentrantLock();

	private ByteBuffer transitBuffer;
	private AtomicInteger transitAvailableToWrite;
	private Condition spaceAvailableToWrite = bufferLock.newCondition();
	private AtomicInteger transitAvailableToRead;
	private Condition dataAvailableToRead = bufferLock.newCondition();
	private Thread sendFromTransitThread;

	private boolean closed = false;
	private AtomicInteger remoteFreeCapacity = new AtomicInteger(4096);
	private Condition remoteBufferHasFreeCapacity = bufferLock.newCondition();

	private Stream stream;


	public StreamOutputStream(int capacity, Stream stream) {
		logger.entering(this.getClass().getName(), "Constructor", 
				new Object[] { Integer.valueOf(capacity), stream });

		transitBuffer = ByteBuffer.allocate(capacity);
		transitAvailableToWrite = new AtomicInteger(capacity);
		transitAvailableToRead = new AtomicInteger(0);

		this.stream = stream;

		// start the virtual thread that waits for data to be in the transit buffer
		this.sendFromTransitThread = Thread.ofVirtual().start(() -> {
			try {
				sendFromTransit();
			} catch (IOException e) {
				e.printStackTrace();
			}
		});

		logger.exiting(this.getClass().getName(), "Constructor");
	}

	/**
	 * Used by Stream to inform us peer has more buffer capacity
	 * @param size
	 */
	public void increaseRemoteAvailable(int size) {
		logger.entering(this.getClass().getName(), "increaseRemoteAvailable", size);

		remoteFreeCapacity.addAndGet(size);

		try {
			bufferLock.lock();
			remoteBufferHasFreeCapacity.signalAll();
		} finally {
			bufferLock.unlock();
		}

		logger.exiting(this.getClass().getName(), "increaseRemoteAvailable");
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

	private void sendFromTransit() throws IOException {
		logger.entering(this.getClass().getName(), "sendFromTransit");

		try {
			while( true ) {
				try {
					bufferLock.lock();

					// only bother waking up if we can send anything
					while ( remoteFreeCapacity.get() < 1 )
						remoteBufferHasFreeCapacity.await();

					// then wait for something to send
					while ( transitAvailableToRead.get() < 1 )
						dataAvailableToRead.await();

					if ( mode != BufferMode.READ ) {
						mode = BufferMode.READ;
						transitBuffer.flip();
					}	

					// logTransitProps("Before Offloading");

					// limit sending to whatever the remote end can take
					int bytesRead = remoteFreeCapacity.get();

					// then reduce if it's less than the data in the transit buffer
					// an optimization might be to loop here to drain as there's
					// capacity at the remote end to take it ...
					if ( bytesRead > transitBuffer.remaining() )
						bytesRead = transitBuffer.remaining();

					stream.sendData(transitBuffer, bytesRead);

					remoteFreeCapacity.addAndGet(-bytesRead);
					transitAvailableToRead.addAndGet(-bytesRead);

					transitAvailableToWrite.addAndGet(bytesRead);
					spaceAvailableToWrite.signalAll();

				} finally {
					bufferLock.unlock();
				}
			}

		} catch (InterruptedException e) {
			// should be because we are tidily closing down
			logger.finest("exiting sendFromTransit due to interrupt");
		}

		logger.exiting(this.getClass().getName(), "sendFromTransit");

	}

	//	private void logTransitProps(String who) {
	//		System.out.println(who);
	//		System.out.println("Position "+transitBuffer.position());
	//		System.out.println("Limit "+transitBuffer.limit());
	//		System.out.println("");
	//		
	//	}

	@Override
	public void write(int b) throws IOException {
		logger.entering(this.getClass().getName(), "write", b);

		byte[] ba = { (byte) b };
		this.write(ba, 0, 1);
		
//		if ( closed ) 
//			throw( new IOException("stream is closed") );
//
//		try {
//			bufferLock.lock();
//
//			if ( closed ) 
//				throw( new IOException("stream is closed") );
//
//			while ( transitAvailableToWrite.get() < 1 ) {
//				spaceAvailableToWrite.await();
//
//				if ( closed ) 
//					throw( new IOException("stream is closed") );
//			}
//			
//			if ( mode != BufferMode.WRITE ) {
//				mode = BufferMode.WRITE;
//				transitBuffer.compact();
//			}	
//
//			// logTransitProps("Writing Transit Buffer");
//
//			transitBuffer.put((byte) b);
//
//			transitAvailableToWrite.addAndGet(-1);
//			transitAvailableToRead.addAndGet(1);
//			dataAvailableToRead.signalAll();
//
//		} catch (InterruptedException e) {
//			throw( new IOException(e) );
//
//		} finally {
//			// logTransitProps("After writing Transit Buffer");
//			bufferLock.unlock();
//		}

		logger.exiting(this.getClass().getName(), "write");
	}

	@Override
	public void write(byte[] b) throws IOException {
		logger.entering(this.getClass().getName(), "write", b);

		if ( b == null )
			throw( new NullPointerException("buffer may not be null") );

		write(b, 0, b.length);

		logger.exiting(this.getClass().getName(), "write");
	}

	@Override
	/**
	 * Copy data into transit buffer and signal data available to send
	 */
	public void write(byte[] b, int off, int len) throws IOException {
		logger.entering(this.getClass().getName(), "write",
				new Object[] { b, Integer.valueOf(off), Integer.valueOf(len) });

		if ( b == null )
			throw( new NullPointerException("buffer may not be null") );

		if ( closed ) 
			throw( new IOException("stream is closed") );


		int bytesTransferred = 0;

		// we may not be able to copy all in at once
		while ( bytesTransferred < len )
		{
			try {
				bufferLock.lock();

				if ( closed ) 
					throw( new IOException("stream is closed") );

				while ( transitAvailableToWrite.get() < 1 ) {
					spaceAvailableToWrite.await();

					if ( closed ) 
						throw( new IOException("stream is closed") );
				}

				if ( mode != BufferMode.WRITE ) {
					mode = BufferMode.WRITE;
					transitBuffer.compact();
				}	

				int bytesToTransfer = len - bytesTransferred;

				// limit what we send to space available
				if ( len > transitAvailableToWrite.get() ) 
					bytesToTransfer = transitAvailableToWrite.get();

				transitBuffer.put(b, off + bytesTransferred, bytesToTransfer);
				bytesTransferred += bytesToTransfer;

				transitAvailableToWrite.addAndGet(-bytesToTransfer);
				transitAvailableToRead.addAndGet(bytesToTransfer);
				dataAvailableToRead.signalAll();

			} catch (InterruptedException e) {
				throw( new IOException(e) );

			} finally {
				bufferLock.unlock();
			}
		}

		logger.exiting(this.getClass().getName(), "write");
	}

	@Override
	public void close() throws IOException {
		logger.entering(this.getClass().getName(), "close");
		closed = true;

		sendFromTransitThread.interrupt();

		try {
			bufferLock.lock();
			spaceAvailableToWrite.signalAll();
			dataAvailableToRead.signalAll();
		} finally {
			bufferLock.unlock();
		}

		logger.exiting(this.getClass().getName(), "close");
	}

	@Override
	public void flush() throws IOException {
		logger.entering(this.getClass().getName(), "flush");

		// TODO: wait until all transit buffer contents emptied
		
		logger.exiting(this.getClass().getName(), "flush");
	}

}
