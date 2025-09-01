package nl.hauntedmc.proxyfeatures.features.votifier.model;

public record Vote(
        String serviceName,
        String username,
        String address,
        long   timestamp
) {}
