package nl.hauntedmc.proxyfeatures.features.friends.entity;

/** Immutable snapshot of a friend (safe to use outside a Hibernate session). */
public final class FriendSnapshot {
    private final Long id;
    private final String uuid;
    private final String username;

    public FriendSnapshot(Long id, String uuid, String username) {
        this.id = id;
        this.uuid = uuid;
        this.username = username;
    }

    public Long getId() { return id; }
    public String getUuid() { return uuid; }
    public String getUsername() { return username; }
}
