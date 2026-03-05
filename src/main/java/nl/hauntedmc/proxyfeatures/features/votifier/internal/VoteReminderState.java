package nl.hauntedmc.proxyfeatures.features.votifier.internal;

public record VoteReminderState(
        long playerId,
        String username,
        boolean remindEnabled,
        long lastVoteAt
) {
}