package nl.hauntedmc.proxyfeatures.features.friends.entity;

/**
 * Lightweight player reference safe to cache & pass around (no JPA session needed).
 */
public record PlayerRef(Long id, String uuid, String username) {
}
