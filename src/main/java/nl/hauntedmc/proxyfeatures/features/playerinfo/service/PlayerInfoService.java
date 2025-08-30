package nl.hauntedmc.proxyfeatures.features.playerinfo.service;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerConnectionInfoEntity;
import nl.hauntedmc.proxyfeatures.features.playerinfo.PlayerInfo;
import nl.hauntedmc.proxyfeatures.features.sanctions.entity.SanctionEntity;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class PlayerInfoService {

    private final PlayerInfo feature;
    private final DateTimeFormatter formatter;

    public PlayerInfoService(PlayerInfo feature) {
        this.feature = feature;

        String tz = Objects.toString(feature.getConfigHandler().getSetting("timezone"));
        ZoneId zoneId;
        if (tz == null || tz.isBlank()) {
            zoneId = ZoneId.systemDefault();
        } else {
            ZoneId tmp;
            try { tmp = ZoneId.of(tz); } catch (Exception e) { tmp = ZoneId.systemDefault(); }
            zoneId = tmp;
        }

        String pattern = Objects.toString(feature.getConfigHandler().getSetting("datetimeFormat"));
        formatter = DateTimeFormatter.ofPattern(pattern).withZone(zoneId);
    }

    public record OnlineStatus(boolean online, String serverName) {}

    public Optional<PlayerEntity> findPlayerEntityByName(String name) {
        return feature.getOrmContext().runInTransaction(session ->
                session.createQuery("SELECT p FROM PlayerEntity p WHERE lower(p.username) = :name", PlayerEntity.class)
                        .setParameter("name", name.toLowerCase(Locale.ROOT))
                        .setMaxResults(1)
                        .uniqueResultOptional()
        );
    }

    public Optional<PlayerEntity> findPlayerEntityByUuid(String uuid) {
        return feature.getOrmContext().runInTransaction(session ->
                session.createQuery("SELECT p FROM PlayerEntity p WHERE p.uuid = :uuid", PlayerEntity.class)
                        .setParameter("uuid", uuid)
                        .setMaxResults(1)
                        .uniqueResultOptional()
        );
    }

    public Optional<PlayerConnectionInfoEntity> getConnectionInfo(PlayerEntity player) {
        return feature.getOrmContext().runInTransaction(session ->
                session.createQuery("SELECT c FROM PlayerConnectionInfoEntity c WHERE c.playerId = :pid", PlayerConnectionInfoEntity.class)
                        .setParameter("pid", player.getId())
                        .setMaxResults(1)
                        .uniqueResultOptional()
        );
    }

    public List<SanctionEntity> getActiveSanctions(PlayerEntity player) {
        Instant now = Instant.now();
        return feature.getOrmContext().runInTransaction(session ->
                session.createQuery(
                                "SELECT s FROM SanctionEntity s " +
                                        "WHERE s.targetPlayer = :player AND s.active = true AND (s.expiresAt IS NULL OR s.expiresAt > :now) " +
                                        "ORDER BY s.createdAt DESC", SanctionEntity.class)
                        .setParameter("player", player)
                        .setParameter("now", now)
                        .getResultList()
        );
    }

    public OnlineStatus getOnlineStatus(String nameOrUuid) {
        // Try by exact username
        Optional<Player> opt = feature.getPlugin().getProxy().getPlayer(nameOrUuid);
        if (opt.isEmpty()) {
            // Try search by UUID string
            try {
                UUID uuid = UUID.fromString(nameOrUuid);
                opt = feature.getPlugin().getProxy().getPlayer(uuid);
            } catch (Exception ignored) {}
        }
        if (opt.isEmpty()) return new OnlineStatus(false, null);

        Player p = opt.get();
        Optional<ServerConnection> current = p.getCurrentServer();
        String serverName = current.map(c -> c.getServerInfo().getName()).orElse("unknown");
        return new OnlineStatus(true, serverName);
    }

    public String fmt(Instant instant) {
        if (instant == null) return "—";
        return formatter.format(instant);
    }
}
