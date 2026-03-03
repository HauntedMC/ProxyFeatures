package nl.hauntedmc.proxyfeatures.features.votifier.internal;

public record VotePlayerStatsView(
            long playerId,
            String username,
            int monthVotes,
            int highestMonthVotes,
            long totalVotes,
            int voteStreak,
            int bestVoteStreak
    ) {
    }