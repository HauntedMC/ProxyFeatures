package nl.hauntedmc.proxyfeatures.features.votifier.internal;

public record VoteLeaderboardEntry(
        long playerId,
        String username,
        int monthVotes,
        long totalVotes
) {
}