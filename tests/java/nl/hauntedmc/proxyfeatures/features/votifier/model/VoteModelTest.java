package nl.hauntedmc.proxyfeatures.features.votifier.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VoteModelTest {

    @Test
    void voteRecordStoresPayload() {
        Vote vote = new Vote("service", "player", "1.2.3.4", 123456L);
        Vote same = new Vote("service", "player", "1.2.3.4", 123456L);
        Vote diff = new Vote("other", "player", "1.2.3.4", 1L);

        assertEquals("service", vote.serviceName());
        assertEquals("player", vote.username());
        assertEquals("1.2.3.4", vote.address());
        assertEquals(123456L, vote.timestamp());
        assertEquals(vote, same);
        assertEquals(vote.hashCode(), same.hashCode());
        assertNotEquals(vote, diff);
    }
}
