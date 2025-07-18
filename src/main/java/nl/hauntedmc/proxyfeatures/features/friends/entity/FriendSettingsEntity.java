package nl.hauntedmc.proxyfeatures.features.friends.entity;

import jakarta.persistence.*;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;

@Entity
@Table(name = "player_friend_settings")
public class FriendSettingsEntity {

    @Id
    @Column(name = "player_id")
    private Long playerId;

    @OneToOne @MapsId
    @JoinColumn(name = "player_id")
    private PlayerEntity player;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    public FriendSettingsEntity() {}
    public FriendSettingsEntity(PlayerEntity player) {
        this.player   = player;
        this.playerId = player.getId();
    }

    public PlayerEntity getPlayer() { return player; }
    public boolean isEnabled()      { return enabled; }
    public void setEnabled(boolean e) { this.enabled = e; }
}
