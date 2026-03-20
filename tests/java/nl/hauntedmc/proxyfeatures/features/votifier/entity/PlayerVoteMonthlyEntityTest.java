package nl.hauntedmc.proxyfeatures.features.votifier.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerVoteMonthlyEntityTest {

    @Test
    void defaultConstructorProvidesSafeFallbackValues() {
        PlayerVoteMonthlyEntity entity = new PlayerVoteMonthlyEntity();

        assertEquals(0L, entity.getPlayerId());
        assertEquals(0, entity.getMonthYearMonth());
    }

    @Test
    void keyedConstructorInitializesStateAndSupportsMutation() {
        PlayerVoteMonthlyEntity entity = new PlayerVoteMonthlyEntity(42L, 202603);

        assertEquals(42L, entity.getPlayerId());
        assertEquals(202603, entity.getMonthYearMonth());
        assertEquals(0, entity.getMonthVotes());
        assertEquals(0, entity.getFinalRank());
        assertFalse(entity.isMedalApplied());
        assertFalse(entity.isNotifySent());
        assertFalse(entity.isRewardGranted());
        assertFalse(entity.isProcessing());
        assertEquals(0L, entity.getProcessingAt());

        entity.setMonthVotes(33);
        entity.setFinalRank(2);
        entity.setMedalApplied(true);
        entity.setNotifySent(true);
        entity.setRewardGranted(true);
        entity.setProcessing(true);
        entity.setProcessingAt(12345L);

        assertNotNull(entity.getId());
        assertNull(entity.getPlayer());
        assertEquals(33, entity.getMonthVotes());
        assertEquals(2, entity.getFinalRank());
        assertTrue(entity.isMedalApplied());
        assertTrue(entity.isNotifySent());
        assertTrue(entity.isRewardGranted());
        assertTrue(entity.isProcessing());
        assertEquals(12345L, entity.getProcessingAt());
    }

    @Test
    void keyEqualityHashCodeAndRolloverStateAccessorsWork() {
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

        VotifierRolloverStateEntity rolloverDefault = new VotifierRolloverStateEntity();
        assertNull(rolloverDefault.getId());
        assertEquals(0, rolloverDefault.getLastResetYearMonth());

        VotifierRolloverStateEntity rollover = new VotifierRolloverStateEntity(1, 202602);
        assertEquals(1, rollover.getId());
        assertEquals(202602, rollover.getLastResetYearMonth());
        rollover.setLastResetYearMonth(202603);
        assertEquals(202603, rollover.getLastResetYearMonth());
    }
}
