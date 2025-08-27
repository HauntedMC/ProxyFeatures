package nl.hauntedmc.proxyfeatures.features.sanctions.entity;

import jakarta.persistence.*;
import java.time.Instant;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;

@Entity
@Table(name = "player_sanctions", indexes = {
        @Index(name = "idx_active_type", columnList = "active,type"),
        @Index(name = "idx_target_player", columnList = "target_player_id"),
        @Index(name = "idx_target_ip", columnList = "target_ip"),
        @Index(name = "idx_active_expires", columnList = "active,expires_at")
})
public class SanctionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** For optimistic locking so concurrent changes don't clobber each other. */
    @Version
    private Long version;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    private SanctionType type;

    // Target: either a player OR an IP (BAN_IP can have null player)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_player_id")
    private PlayerEntity targetPlayer;

    @Column(name = "target_ip", length = 64)
    private String targetIp;   // for IP bans

    @Column(name = "reason", nullable = false, length = 512)
    private String reason;

    // Actor (nullable for CONSOLE)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_player_id")
    private PlayerEntity actorPlayer;

    @Column(name = "actor_name", length = 64)
    private String actorName; // Fallback display name for console or when actor player is null

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt; // null == permanent

    @Column(name = "active", nullable = false)
    private boolean active;

    public Long getId() { return id; }
    public Long getVersion() { return version; }

    public SanctionType getType() { return type; }
    public void setType(SanctionType type) { this.type = type; }

    public PlayerEntity getTargetPlayer() { return targetPlayer; }
    public void setTargetPlayer(PlayerEntity targetPlayer) { this.targetPlayer = targetPlayer; }

    public String getTargetIp() { return targetIp; }
    public void setTargetIp(String targetIp) { this.targetIp = targetIp; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public PlayerEntity getActorPlayer() { return actorPlayer; }
    public void setActorPlayer(PlayerEntity actorPlayer) { this.actorPlayer = actorPlayer; }

    public String getActorName() { return actorName; }
    public void setActorName(String actorName) { this.actorName = actorName; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public boolean isPermanent() { return expiresAt == null; }
    public boolean isExpired(Instant now) {
        return !isPermanent() && expiresAt.isBefore(now);
    }
}
