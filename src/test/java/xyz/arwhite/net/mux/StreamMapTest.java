package xyz.arwhite.net.mux;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.Exception;
import javax.naming.LimitExceededException;

class StreamMapTest {

	private StreamMap streams;
	
	@BeforeEach
	void setUp() throws Exception {
		streams = new StreamMap();
	}

	@AfterEach
	void tearDown() throws Exception {
		streams = null;
	}

	@Test
	@DisplayName("Insert into empty StreamMap")
	void insertIntoEmptyStreamMap() {
		
		assertDoesNotThrow(() -> streams.allocNewStreamId());
		
	}
	
	@Test
	@DisplayName("Insert into full StreamMap")
	void insertIntoFullStreamMap() {
		
		// limit is hardcoded as 100 in StreamMap
		for ( int i=0; i < 100; i++ ) {
			assertDoesNotThrow(() -> streams.allocNewStreamId());
		}
		
		assertThrows(LimitExceededException.class, () -> streams.allocNewStreamId());
		
	}
	
	@Test
	@DisplayName("Remove allocated Id from StreamMap")
	void removeValidIdFromStreamMap() {

		int id = assertDoesNotThrow(() -> streams.allocNewStreamId());
		assertDoesNotThrow(() -> streams.freeStreamId(id));
		
	}

	@Test
	@DisplayName("Remove unallocated Id from StreamMap")
	void removeUnallocatedIdFromStreamMap() {

		assertThrows(IllegalArgumentException.class,() -> streams.freeStreamId(3));
		
	}
	
	@Test
	@DisplayName("Remove invalid Id from StreamMap")
	void removeInvalidIdFromStreamMap() {

		assertThrows(IllegalArgumentException.class,() -> streams.freeStreamId(258));
		
	}
}
