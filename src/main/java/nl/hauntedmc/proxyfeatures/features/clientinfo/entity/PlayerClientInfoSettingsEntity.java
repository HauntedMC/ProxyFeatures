package nl.hauntedmc.proxyfeatures.features.clientinfo.entity;

import jakarta.persistence.*;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;

@Entity
@Table(name = "player_clientinfo_settings")
public class PlayerClientInfoSettingsEntity {

    @Id
    @Column(name = "player_id")
    private Long playerId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "player_id", nullable = false)
    private PlayerEntity player;

    @Column(name = "notify_enabled", nullable = false)
    private boolean notifyEnabled = true;

    public PlayerClientInfoSettingsEntity() {
    }

    public PlayerClientInfoSettingsEntity(PlayerEntity player) {
        this.player = player;
        this.playerId = player.getId();
    }

    public boolean isNotifyEnabled() {
        return notifyEnabled;
    }

    public void setNotifyEnabled(boolean notifyEnabled) {
        this.notifyEnabled = notifyEnabled;
    }
}
