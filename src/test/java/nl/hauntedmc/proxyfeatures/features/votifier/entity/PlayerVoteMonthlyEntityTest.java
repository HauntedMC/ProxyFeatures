package nl.hauntedmc.proxyfeatures.features.votifier.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerVoteMonthlyEntityTest {

    @Test
    void defaultConstructorProvidesSafeIdFallbackValues() {
        PlayerVoteMonthlyEntity entity = new PlayerVoteMonthlyEntity();

        assertEquals(0L, entity.getPlayerId());
        assertEquals(0, entity.getMonthYearMonth());
    }

    @Test
    void keyedConstructorInitializesProcessingAndRankingState() {
        PlayerVoteMonthlyEntity entity = new PlayerVoteMonthlyEntity(42L, 202603);

        assertEquals(42L, entity.getPlayerId());
        assertEquals(202603, entity.getMonthYearMonth());
        assertEquals(0, entity.getFinalRank());
        assertFalse(entity.isMedalApplied());
        assertFalse(entity.isNotifySent());
        assertFalse(entity.isRewardGranted());
        assertFalse(entity.isProcessing());
        assertEquals(0L, entity.getProcessingAt());
    }

    @Test
    void mutableFlagsAndRankingFieldsCanBeUpdated() {
        PlayerVoteMonthlyEntity entity = new PlayerVoteMonthlyEntity(42L, 202603);

        entity.setFinalRank(2);
        entity.setMedalApplied(true);
        entity.setNotifySent(true);
        entity.setRewardGranted(true);
        entity.setProcessing(true);
        entity.setProcessingAt(12345L);

        assertEquals(42L, entity.getPlayerId());
        assertEquals(202603, entity.getMonthYearMonth());
        assertEquals(2, entity.getFinalRank());
        assertTrue(entity.isMedalApplied());
        assertTrue(entity.isNotifySent());
        assertTrue(entity.isRewardGranted());
        assertTrue(entity.isProcessing());
        assertEquals(12345L, entity.getProcessingAt());
    }

    @Test
    void keyEqualityAndHashCodeAreStable() {
        PlayerVoteMonthlyKey emptyKey = new PlayerVoteMonthlyKey();
        assertEquals(0L, emptyKey.getPlayerId());
        assertEquals(0, emptyKey.getMonthYearMonth());

        PlayerVoteMonthlyKey a = new PlayerVoteMonthlyKey(7L, 202601);
        PlayerVoteMonthlyKey b = new PlayerVoteMonthlyKey(7L, 202601);
        PlayerVoteMonthlyKey c = new PlayerVoteMonthlyKey(8L, 202601);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
        assertNotEquals(a, null);
        assertNotEquals(a, "x");
        assertEquals(7L, a.getPlayerId());
        assertEquals(202601, a.getMonthYearMonth());
    }
}
