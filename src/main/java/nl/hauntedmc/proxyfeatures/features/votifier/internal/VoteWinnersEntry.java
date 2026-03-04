package nl.hauntedmc.proxyfeatures.features.votifier.internal;

public record VoteWinnersEntry(
        long playerId,
        String username,
        int firstPlaces,
        int secondPlaces,
        int thirdPlaces
) {
}