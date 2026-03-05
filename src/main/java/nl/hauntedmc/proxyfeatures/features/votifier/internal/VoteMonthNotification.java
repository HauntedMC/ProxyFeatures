package nl.hauntedmc.proxyfeatures.features.votifier.internal;

public record VoteMonthNotification(
        long playerId,
        String username,
        int monthYearMonth,
        int rank,
        int monthVotes,
        boolean needsNotify,
        boolean needsReward
) {
}