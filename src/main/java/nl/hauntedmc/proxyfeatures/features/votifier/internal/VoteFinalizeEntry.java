package nl.hauntedmc.proxyfeatures.features.votifier.internal;

public record VoteFinalizeEntry(
        long playerId,
        int monthVotes,
        long lastVoteAt
) {
}