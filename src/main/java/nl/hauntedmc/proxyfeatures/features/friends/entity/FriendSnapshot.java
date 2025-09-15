package nl.hauntedmc.proxyfeatures.features.friends.entity;

/**
 * Immutable snapshot of a friend (safe to use outside a Hibernate session).
 */
public record FriendSnapshot(Long id, String uuid, String username) {
}
