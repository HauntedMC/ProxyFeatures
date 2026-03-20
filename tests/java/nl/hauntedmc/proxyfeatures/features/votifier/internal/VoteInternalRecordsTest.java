package nl.hauntedmc.proxyfeatures.features.votifier.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VoteInternalRecordsTest {

    @Test
    void voteInternalRecordsExposeExpectedComponents() {
        VoteFinalizeEntry finalizeEntry = new VoteFinalizeEntry(1L, 5, 11L);
        assertEquals(1L, finalizeEntry.playerId());
        assertEquals(5, finalizeEntry.monthVotes());
        assertEquals(11L, finalizeEntry.lastVoteAt());

        VoteLeaderboardEntry leaderboard = new VoteLeaderboardEntry(2L, "Remy", 9, 100L);
        assertEquals(2L, leaderboard.playerId());
        assertEquals("Remy", leaderboard.username());
        assertEquals(9, leaderboard.monthVotes());
        assertEquals(100L, leaderboard.totalVotes());

        VoteMonthNotification notification = new VoteMonthNotification(3L, "Nina", 202603, 1, 22, true, true);
        assertEquals(3L, notification.playerId());
        assertEquals("Nina", notification.username());
        assertEquals(202603, notification.monthYearMonth());
        assertEquals(1, notification.rank());
        assertEquals(22, notification.monthVotes());
        assertTrue(notification.needsNotify());
        assertTrue(notification.needsReward());

        VotePlayerStatsView stats = new VotePlayerStatsView(4L, "Alex", 10, 25, 200L, 3, 7);
        assertEquals(4L, stats.playerId());
        assertEquals("Alex", stats.username());
        assertEquals(10, stats.monthVotes());
        assertEquals(25, stats.highestMonthVotes());
        assertEquals(200L, stats.totalVotes());
        assertEquals(3, stats.voteStreak());
        assertEquals(7, stats.bestVoteStreak());

        VoteReminderState reminder = new VoteReminderState(5L, "Luca", false, 999L);
        assertEquals(5L, reminder.playerId());
        assertEquals("Luca", reminder.username());
        assertFalse(reminder.remindEnabled());
        assertEquals(999L, reminder.lastVoteAt());

        VoteWinnersEntry winners = new VoteWinnersEntry(6L, "Mira", 1, 2, 3);
        assertEquals(6L, winners.playerId());
        assertEquals("Mira", winners.username());
        assertEquals(1, winners.firstPlaces());
        assertEquals(2, winners.secondPlaces());
        assertEquals(3, winners.thirdPlaces());
    }

    @Test
    void remindModeEnumsAndRolloverResultAreReachable() {
        assertEquals(VoteStatsService.RemindMode.TOGGLE, VoteStatsService.RemindMode.valueOf("TOGGLE"));
        assertEquals(VotifierService.RemindMode.ON, VotifierService.RemindMode.valueOf("ON"));

        VoteStatsService.RolloverResult result = new VoteStatsService.RolloverResult(202602, 202603, 3, "dump.txt");
        assertEquals(202602, result.finalizedMonthYearMonth());
        assertEquals(202603, result.currentMonthYearMonth());
        assertEquals(3, result.medalsApplied());
        assertEquals("dump.txt", result.dumpFile());
    }
}
