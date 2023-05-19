package xyz.arwhite.net.mux;


import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.Arrays;
import java.util.ArrayList;

class PriorityQueueEntryTest {

	@Test
	@DisplayName("Test Priority Order")
	void testPriorityOrder() {

		var v1 = new PriorityQueueEntry((byte) 1,1l,0,null);
		var v2 = new PriorityQueueEntry((byte) 2,1l,0,null);
		var v3 = new PriorityQueueEntry((byte) 3,1l,0,null);
		
		var pbq = new PriorityBlockingQueue<PriorityQueueEntry>();
		pbq.addAll(Arrays.asList(v3,v1,v2));
		ArrayList<PriorityQueueEntry> pbqList = new ArrayList<PriorityQueueEntry>();
		pbq.drainTo(pbqList);
		
		assertThat(pbqList).containsExactly(v1, v2, v3);
	}

	@Test
	@DisplayName("Test Timestamp Order")
	void testTimestampOrder() {

		var v1 = new PriorityQueueEntry((byte) 1,1l,0,null);
		var v2 = new PriorityQueueEntry((byte) 1,2l,0,null);
		var v3 = new PriorityQueueEntry((byte) 1,3l,0,null);
		
		var pbq = new PriorityBlockingQueue<PriorityQueueEntry>();
		pbq.addAll(Arrays.asList(v3,v1,v2));
		ArrayList<PriorityQueueEntry> pbqList = new ArrayList<PriorityQueueEntry>();
		pbq.drainTo(pbqList);
		
		assertThat(pbqList).containsExactly(v1, v2, v3);
	}
	
	@Test
	@DisplayName("Test Sequence Order")
	void testSequenceOrder() {

		var v1 = new PriorityQueueEntry((byte) 1,1l,1,null);
		var v2 = new PriorityQueueEntry((byte) 1,1l,2,null);
		var v3 = new PriorityQueueEntry((byte) 1,1l,3,null);
		
		var pbq = new PriorityBlockingQueue<PriorityQueueEntry>();
		pbq.addAll(Arrays.asList(v3,v1,v2));
		ArrayList<PriorityQueueEntry> pbqList = new ArrayList<PriorityQueueEntry>();
		pbq.drainTo(pbqList);
		
		assertThat(pbqList).containsExactly(v1, v2, v3);
	}
}
