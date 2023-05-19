package xyz.arwhite.net.mux;

import io.helidon.common.buffers.BufferData;

/***
 * Defines an entry in the priority queue. 
 * 
 * The user defines the priority of a stream, and any message with a higher priority will be pulled from the queue first.
 * Priority 0 is the highest priority and should not be used by user streams, 1 is the next highest, 255 is the lowest.
 * If multiple messages arrive with the same priority, they are pulled in FIFO order within that priority.
 * If multiple messages arrive with the same priority, within the same timestamp, they are sequenced and pulled in FIFO order.
 * The timestamp granularity is determined by the platform, it may be so granular that sequencing is never used.
 * 
 * @author Alan R. White
 *
 */
public record PriorityQueueEntry(byte priority, long timestamp, int sequence, BufferData message) 
	implements Comparable<PriorityQueueEntry> {

	public int compareTo(PriorityQueueEntry o) {
		// lowest priority
		if ( o.priority < this.priority )
			return +1;

		if ( o.priority > this.priority )
			return -1;

		// priority must be equal - check earliest timestamp
		if ( o.timestamp < this.timestamp )
			return +1;

		if ( o.timestamp > this.timestamp )
			return -1;

		// priority and timestamp equal - order within timestamp 
		if ( o.sequence < this.sequence )
			return +1;

		if ( o.sequence > this.sequence )
			return -1;

		// should never get here
		return 0;
	}
}
