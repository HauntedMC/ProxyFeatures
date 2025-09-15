package nl.hauntedmc.proxyfeatures.features.friends.entity;

import jakarta.persistence.*;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;

@Entity
@Table(name = "player_friends",
        uniqueConstraints = @UniqueConstraint(columnNames = {"player_id", "friend_id"}))
public class FriendRelationEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /* Who owns this row */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id",  nullable = false)
    private PlayerEntity player;

    /* Counter-party */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "friend_id",  nullable = false)
    private PlayerEntity friend;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private FriendStatus status;

    protected FriendRelationEntity() {}

    public FriendRelationEntity(PlayerEntity player,
                                PlayerEntity friend,
                                FriendStatus status) {
        this.player  = player;
        this.friend  = friend;
        this.status  = status;
    }

    public Long         getId()     { return id; }
    public PlayerEntity getPlayer() { return player; }
    public PlayerEntity getFriend() { return friend; }
    public FriendStatus getStatus() { return status; }

    public void setStatus(FriendStatus s) { this.status = s; }
}
