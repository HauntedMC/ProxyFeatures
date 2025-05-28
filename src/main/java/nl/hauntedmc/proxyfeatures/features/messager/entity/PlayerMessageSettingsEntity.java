package nl.hauntedmc.proxyfeatures.features.messager.entity;

import jakarta.persistence.*;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "player_message_settings")
public class PlayerMessageSettingsEntity {

    @Id
    @Column(name = "player_id")
    private Long playerId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "player_id", nullable = false)
    private PlayerEntity player;

    @Column(name = "msg_toggle", nullable = false)
    private boolean msgToggle = true;

    @Column(name = "msg_spy", nullable = false)
    private boolean msgSpy = false;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "player_message_blocks",
            joinColumns = @JoinColumn(name = "player_id"),
            inverseJoinColumns = @JoinColumn(name = "blocked_player_id")
    )
    private Set<PlayerEntity> blockedPlayers = new HashSet<>();

    public PlayerMessageSettingsEntity() {}

    public PlayerMessageSettingsEntity(PlayerEntity player) {
        this.player = player;
        this.playerId = player.getId();
    }

    public boolean isMsgToggle() { return msgToggle; }
    public void setMsgToggle(boolean msgToggle) { this.msgToggle = msgToggle; }

    public boolean isMsgSpy() { return msgSpy; }
    public void setMsgSpy(boolean msgSpy) { this.msgSpy = msgSpy; }

    public Set<PlayerEntity> getBlockedPlayers() { return blockedPlayers; }
    public void block(PlayerEntity target)   { blockedPlayers.add(target); }
    public void unblock(PlayerEntity target) { blockedPlayers.remove(target); }
    public boolean isBlocking(PlayerEntity t){ return blockedPlayers.contains(t); }
}
