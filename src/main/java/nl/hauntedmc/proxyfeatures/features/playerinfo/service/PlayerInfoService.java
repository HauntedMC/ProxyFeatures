package nl.hauntedmc.proxyfeatures.features.playerinfo.service;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import nl.hauntedmc.dataregistry.api.entities.PlayerConnectionInfoEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
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

        String tz = feature.getConfigHandler().get("timezone", String.class, "");
        ZoneId zoneId;
        if (tz == null || tz.isBlank()) {
            zoneId = ZoneId.systemDefault();
        } else {
            ZoneId tmp;
            try {
                tmp = ZoneId.of(tz);
            } catch (Exception e) {
                tmp = ZoneId.systemDefault();
            }
            zoneId = tmp;
        }

        String pattern = feature.getConfigHandler().get("datetimeFormat", String.class, "dd-MM-yyyy HH:mm:ss");
        if (pattern == null || pattern.isBlank()) {
            pattern = "dd-MM-yyyy HH:mm:ss";
        }

        DateTimeFormatter fmt;
        try {
            fmt = DateTimeFormatter.ofPattern(pattern).withZone(zoneId);
        } catch (IllegalArgumentException ex) {
            feature.getLogger().warn("[PlayerInfo] Invalid datetimeFormat '" + pattern + "', falling back to default.");
            fmt = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss").withZone(zoneId);
        }
        formatter = fmt;
    }

    public record OnlineStatus(boolean online, String serverName) {
    }

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
                session.createQuery(
                                "SELECT c FROM PlayerConnectionInfoEntity c WHERE c.playerId = :pid",
                                PlayerConnectionInfoEntity.class)
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
                                        "WHERE s.targetPlayer = :player AND s.active = true " +
                                        "AND (s.expiresAt IS NULL OR s.expiresAt > :now) " +
                                        "ORDER BY s.createdAt DESC",
                                SanctionEntity.class)
                        .setParameter("player", player)
                        .setParameter("now", now)
                        .getResultList()
        );
    }

    public OnlineStatus getOnlineStatus(String nameOrUuid) {
        // Try by exact username
        Optional<Player> opt = ProxyFeatures.getProxyInstance().getPlayer(nameOrUuid);
        if (opt.isEmpty()) {
            // Try search by UUID string
            try {
                UUID uuid = UUID.fromString(nameOrUuid);
                opt = ProxyFeatures.getProxyInstance().getPlayer(uuid);
            } catch (IllegalArgumentException ignored) {
            }
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

    /**
     * Find other usernames whose last known IP equals the provided IP.
     * Returns A→Z usernames; excludes the player with excludePlayerId.
     */
    public List<String> findUsernamesByLastIp(String ip, Long excludePlayerId) {
        if (ip == null || ip.isBlank()) return List.of();
        return feature.getOrmContext().runInTransaction(session ->
                session.createQuery(
                                "SELECT c.player.username FROM PlayerConnectionInfoEntity c " +
                                        "WHERE c.ipAddress = :ip AND c.player.id <> :ex " +
                                        "ORDER BY c.player.username ASC",
                                String.class)
                        .setParameter("ip", ip)
                        .setParameter("ex", excludePlayerId)
                        .getResultList()
        );
    }
}
