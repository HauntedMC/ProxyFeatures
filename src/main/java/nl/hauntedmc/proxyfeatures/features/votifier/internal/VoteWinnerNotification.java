package nl.hauntedmc.proxyfeatures.features.votifier.internal;

public record VoteWinnerNotification(
        long playerId,
        String username,
        int winnerMonthYearMonth,
        int winnerRank,
        int monthVotes,
        boolean needsCongrats,
        boolean needsReward
) {
}