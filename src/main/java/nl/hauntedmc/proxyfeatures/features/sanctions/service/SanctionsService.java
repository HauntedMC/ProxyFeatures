package nl.hauntedmc.proxyfeatures.features.sanctions.service;

import nl.hauntedmc.proxyfeatures.features.sanctions.Sanctions;
import nl.hauntedmc.proxyfeatures.features.sanctions.entity.SanctionEntity;
import nl.hauntedmc.proxyfeatures.features.sanctions.entity.SanctionType;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SanctionsService {

    private final Sanctions feature;

    // in-memory cache for mutes: playerId -> active mute
    private final Map<Long, SanctionEntity> muteCache = new ConcurrentHashMap<>();

    public SanctionsService(Sanctions feature) { this.feature = feature; }

    // ===== Player lookups =====

    public Optional<PlayerEntity> getPlayerByUuid(String uuid) {
        return feature.getOrm().runInTransaction(
                s -> s.createQuery("FROM PlayerEntity WHERE uuid = :u", PlayerEntity.class)
                        .setParameter("u", uuid)
                        .uniqueResultOptional());
    }

    public Optional<PlayerEntity> getPlayerByName(String name) {
        return feature.getOrm().runInTransaction(
                s -> s.createQuery("FROM PlayerEntity WHERE username = :u", PlayerEntity.class)
                        .setParameter("u", name)
                        .uniqueResultOptional());
    }

    // ===== CREATE =====

    public SanctionEntity createBanForPlayer(PlayerEntity target, String reason,
                                             PlayerEntity actor, String actorName,
                                             Instant expiresAt) {
        return createActive(SanctionType.BAN, target, null, reason, actor, actorName, expiresAt);
    }

    public SanctionEntity createBanForIp(String ip, String reason,
                                         PlayerEntity actor, String actorName,
                                         Instant expiresAt) {
        return createActive(SanctionType.BAN_IP, null, ip, reason, actor, actorName, expiresAt);
    }

    public SanctionEntity createMute(PlayerEntity target, String reason,
                                     PlayerEntity actor, String actorName,
                                     Instant expiresAt) {
        SanctionEntity s = createActive(SanctionType.MUTE, target, null, reason, actor, actorName, expiresAt);
        muteCache.put(target.getId(), s);
        return s;
    }

    public void createWarn(PlayerEntity target, String reason,
                           PlayerEntity actor, String actorName) {
        createInstant(SanctionType.WARN, target, null, reason, actor, actorName);
    }

    public void createKick(PlayerEntity target, String reason,
                           PlayerEntity actor, String actorName) {
        createInstant(SanctionType.KICK, target, null, reason, actor, actorName);
    }

    private SanctionEntity createActive(SanctionType type, PlayerEntity target, String ip,
                                        String reason, PlayerEntity actor, String actorName,
                                        Instant expiresAt) {
        return feature.getOrm().runInTransaction(session -> {
            SanctionEntity s = new SanctionEntity();
            s.setType(type);
            s.setTargetPlayer(target);
            s.setTargetIp(ip);
            s.setReason(reason);
            s.setActorPlayer(actor);
            s.setCreatedAt(Instant.now());
            s.setExpiresAt(expiresAt); // null = permanent
            s.setActive(true);
            session.persist(s);
            return s;
        });
    }

    private void createInstant(SanctionType type, PlayerEntity target, String ip,
                               String reason, PlayerEntity actor, String actorName) {
        feature.getOrm().runInTransaction(session -> {
            SanctionEntity s = new SanctionEntity();
            s.setType(type);
            s.setTargetPlayer(target);
            s.setTargetIp(ip);
            s.setReason(reason);
            s.setActorPlayer(actor);
            s.setCreatedAt(Instant.now());
            s.setActive(false);
            s.setExpiresAt(null);
            session.persist(s);
            return null;
        });
    }

    // ===== QUERIES =====

    public Optional<SanctionEntity> findActiveBanByPlayer(PlayerEntity p) {
        return feature.getOrm().runInTransaction(session ->
                session.createQuery("FROM SanctionEntity WHERE active = true AND type = :t AND targetPlayer = :tp",
                                SanctionEntity.class)
                        .setParameter("t", SanctionType.BAN)
                        .setParameter("tp", p)
                        .uniqueResultOptional());
    }

    public Optional<SanctionEntity> findActiveBanByIp(String ip) {
        return feature.getOrm().runInTransaction(session ->
                session.createQuery("FROM SanctionEntity WHERE active = true AND type = :t AND targetIp = :ip",
                                SanctionEntity.class)
                        .setParameter("t", SanctionType.BAN_IP)
                        .setParameter("ip", ip)
                        .uniqueResultOptional());
    }

    public Optional<SanctionEntity> findActiveMuteByPlayer(PlayerEntity p) {
        return feature.getOrm().runInTransaction(session ->
                session.createQuery("FROM SanctionEntity WHERE active = true AND type = :t AND targetPlayer = :tp",
                                SanctionEntity.class)
                        .setParameter("t", SanctionType.MUTE)
                        .setParameter("tp", p)
                        .uniqueResultOptional());
    }

    // ===== EXPIRY =====

    public void sweepExpiries() {
        Instant now = Instant.now();
        List<SanctionEntity> toDeactivate = feature.getOrm().runInTransaction(session ->
                session.createQuery("FROM SanctionEntity WHERE active = true AND expiresAt IS NOT NULL AND expiresAt < :now",
                                SanctionEntity.class)
                        .setParameter("now", now)
                        .list());

        if (toDeactivate.isEmpty()) return;

        feature.getOrm().runInTransaction(session -> {
            for (SanctionEntity s : toDeactivate) {
                SanctionEntity managed = session.get(SanctionEntity.class, s.getId());
                managed.setActive(false);
            }
            return null;
        });

        toDeactivate.stream()
                .filter(s -> s.getType() == SanctionType.MUTE && s.getTargetPlayer() != null)
                .forEach(s -> muteCache.remove(s.getTargetPlayer().getId()));
    }

    // ===== MUTE CACHE =====

    public void loadActiveMuteIntoCache(Long playerId) {
        // find active mute by playerId
        feature.getOrm().runInTransaction(session -> {
            PlayerEntity p = session.get(PlayerEntity.class, playerId);
            if (p == null) return null;
            var opt = session.createQuery("FROM SanctionEntity WHERE active = true AND type = :t AND targetPlayer = :tp",
                            SanctionEntity.class)
                    .setParameter("t", SanctionType.MUTE)
                    .setParameter("tp", p)
                    .uniqueResultOptional();
            opt.ifPresentOrElse(s -> muteCache.put(playerId, s), () -> muteCache.remove(playerId));
            return null;
        });
    }

    public boolean isMuted(Long playerId) {
        SanctionEntity s = muteCache.get(playerId);
        if (s == null) return false;
        if (!s.isActive()) { muteCache.remove(playerId); return false; }
        if (s.isExpired(Instant.now())) {
            deactivateById(s.getId());
            muteCache.remove(playerId);
            return false;
        }
        return true;
    }

    public String remainingForMute(Long playerId) {
        SanctionEntity s = muteCache.get(playerId);
        if (s == null) return "0m";
        if (s.isPermanent()) return "permanent";
        long seconds = Math.max(0, s.getExpiresAt().getEpochSecond() - Instant.now().getEpochSecond());
        long days = seconds / 86400; seconds %= 86400;
        long hours = seconds / 3600; seconds %= 3600;
        long minutes = seconds / 60;
        return (days > 0 ? days + "d " : "") + (hours > 0 ? hours + "h " : "") + (minutes > 0 ? minutes + "m" : "0m");
    }

    private void deactivateById(Long id) {
        feature.getOrm().runInTransaction(session -> {
            SanctionEntity s = session.get(SanctionEntity.class, id);
            if (s != null && s.isActive()) s.setActive(false);
            return null;
        });
    }

    // ===== UTIL =====

    public Instant parseLengthToExpiry(String token) {
        if (token == null) return null;
        token = token.trim().toLowerCase(Locale.ROOT);
        if (token.equals("p")) return null; // permanent
        if (!token.endsWith("d")) throw new IllegalArgumentException("invalid");
        String num = token.substring(0, token.length() - 1);
        long days = Long.parseLong(num);
        return Instant.now().plus(Duration.ofDays(days));
    }


    public Map<String, String> placeholdersFor(SanctionEntity s) {
        Map<String, String> m = new HashMap<>();
        String dur = s.isPermanent() ? "permanent" : humanDuration(Instant.now(), s.getExpiresAt());

        // Resolve target label: prefer player username, else IP, else "-"
        String targetLabel = Optional.ofNullable(resolveUsername(s.getTargetPlayer()))
                .orElseGet(() -> s.getTargetIp() != null ? s.getTargetIp() : "-");

        // Resolve actor label: username if actor player known, else "CONSOLE"
        String actorLabel = Optional.ofNullable(resolveUsername(s.getActorPlayer()))
                .orElse("CONSOLE");

        m.put("reason", s.getReason());
        m.put("duration", dur);
        m.put("target", targetLabel);
        m.put("ip", s.getTargetIp() != null ? s.getTargetIp() : "-");
        m.put("actor", actorLabel);
        return m;
    }

    /** Safely resolve a username from a (possibly detached) PlayerEntity reference. */
    private String resolveUsername(PlayerEntity ref) {
        if (ref == null) return null;
        Long id = ref.getId();
        if (id == null) return null;
        return feature.getOrm().runInTransaction(session -> {
            PlayerEntity managed = session.get(PlayerEntity.class, id);
            return managed != null ? managed.getUsername() : null;
        });
    }


    public Map<String, String> mapOf(String k, String v) {
        Map<String, String> m = new HashMap<>(); m.put(k, v); return m;
    }

    public String humanDuration(Instant from, Instant to) {
        if (to == null) return "permanent";
        long seconds = Math.max(0, to.getEpochSecond() - from.getEpochSecond());
        long days = seconds / 86400; seconds %= 86400;
        long hours = seconds / 3600; seconds %= 3600;
        long minutes = seconds / 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m");
        String out = sb.toString().trim();
        return out.isEmpty() ? "0m" : out;
    }

    public void broadcastToStaff(String messageKey, Map<String, String> ph) {
        String notifyPerm = "proxyfeatures.feature.sanctions.notify";

        feature.getPlugin().getProxy().getAllPlayers().forEach(pl -> {
            if (pl.hasPermission(notifyPerm)) {
                pl.sendMessage(feature.getLocalizationHandler()
                        .getMessage(messageKey).withPlaceholders(ph).forAudience(pl).build());
            }
        });
        feature.getLogger().info(
                feature.getLocalizationHandler().getMessage(messageKey).withPlaceholders(ph).build());
    }

    // Deactivate all active BANs for a player (returns true if any changed)
    public boolean deactivateActiveBanForPlayer(PlayerEntity p) {
        return feature.getOrm().runInTransaction(session -> {
            var list = session.createQuery(
                            "FROM SanctionEntity WHERE active = true AND type = :t AND targetPlayer = :tp",
                            nl.hauntedmc.proxyfeatures.features.sanctions.entity.SanctionEntity.class)
                    .setParameter("t", nl.hauntedmc.proxyfeatures.features.sanctions.entity.SanctionType.BAN)
                    .setParameter("tp", p)
                    .list();
            if (list.isEmpty()) return false;
            for (var s : list) {
                var managed = session.get(nl.hauntedmc.proxyfeatures.features.sanctions.entity.SanctionEntity.class, s.getId());
                if (managed != null) managed.setActive(false);
            }
            return true;
        });
    }

    // Deactivate all active MUTE(s) for a player (returns true if any changed)
    public boolean deactivateActiveMuteForPlayer(PlayerEntity p) {
        boolean changed = feature.getOrm().runInTransaction(session -> {
            var list = session.createQuery(
                            "FROM SanctionEntity WHERE active = true AND type = :t AND targetPlayer = :tp",
                            nl.hauntedmc.proxyfeatures.features.sanctions.entity.SanctionEntity.class)
                    .setParameter("t", nl.hauntedmc.proxyfeatures.features.sanctions.entity.SanctionType.MUTE)
                    .setParameter("tp", p)
                    .list();
            if (list.isEmpty()) return false;
            for (var s : list) {
                var managed = session.get(nl.hauntedmc.proxyfeatures.features.sanctions.entity.SanctionEntity.class, s.getId());
                if (managed != null) managed.setActive(false);
            }
            return true;
        });
        if (changed && p.getId() != null) {
            muteCache.remove(p.getId());
        }
        return changed;
    }

    // Name suggestions for active sanctions (prefix match, case-insensitive)
    public java.util.List<String> suggestActiveTargetNames(
            nl.hauntedmc.proxyfeatures.features.sanctions.entity.SanctionType type,
            String startsWith,
            int limit
    ) {
        String prefix = (startsWith == null ? "" : startsWith).toLowerCase(java.util.Locale.ROOT) + "%";
        return feature.getOrm().runInTransaction(session ->
                session.createQuery(
                                "select s.targetPlayer.username " +
                                        "from SanctionEntity s " +
                                        "where s.active = true and s.type = :t and s.targetPlayer is not null " +
                                        "and lower(s.targetPlayer.username) like :p " +
                                        "order by s.targetPlayer.username asc",
                                String.class)
                        .setParameter("t", type)
                        .setParameter("p", prefix)
                        .setMaxResults(limit)
                        .list()
        );
    }

    // Deactivate all active BAN_IP entries for an IP (returns true if any changed)
    public boolean deactivateActiveBanByIp(String ip) {
        return feature.getOrm().runInTransaction(session -> {
            var list = session.createQuery(
                            "FROM SanctionEntity WHERE active = true AND type = :t AND targetIp = :ip",
                            nl.hauntedmc.proxyfeatures.features.sanctions.entity.SanctionEntity.class)
                    .setParameter("t", nl.hauntedmc.proxyfeatures.features.sanctions.entity.SanctionType.BAN_IP)
                    .setParameter("ip", ip)
                    .list();
            if (list.isEmpty()) return false;
            for (var s : list) {
                var managed = session.get(nl.hauntedmc.proxyfeatures.features.sanctions.entity.SanctionEntity.class, s.getId());
                if (managed != null) managed.setActive(false);
            }
            return true;
        });
    }

    // Suggestions: active banned IPs that start with a prefix (limited)
    public java.util.List<String> suggestActiveBannedIps(String startsWith, int limit) {
        String prefix = (startsWith == null ? "" : startsWith) + "%";
        return feature.getOrm().runInTransaction(session ->
                session.createQuery(
                                "select s.targetIp from SanctionEntity s " +
                                        "where s.active = true and s.type = :t and s.targetIp is not null " +
                                        "and s.targetIp like :p order by s.targetIp asc",
                                String.class)
                        .setParameter("t", nl.hauntedmc.proxyfeatures.features.sanctions.entity.SanctionType.BAN_IP)
                        .setParameter("p", prefix)
                        .setMaxResults(limit)
                        .list()
        );
    }

    /** List sanctions for a specific player, newest first. If activeOnly=true, returns only active ones. */
    public java.util.List<SanctionEntity> listSanctionsForPlayer(PlayerEntity p, boolean activeOnly) {
        return feature.getOrm().runInTransaction(session -> {
            var q = session.createQuery(
                    "FROM SanctionEntity WHERE targetPlayer = :p"
                            + (activeOnly ? " AND active = true" : "")
                            + " ORDER BY createdAt DESC",
                    SanctionEntity.class);
            q.setParameter("p", p);
            return q.list();
        });
    }

    /** Resolve a username for a (possibly detached) PlayerEntity safely. */
    public java.util.Optional<String> usernameOf(PlayerEntity ref) {
        if (ref == null || ref.getId() == null) return java.util.Optional.empty();
        return java.util.Optional.ofNullable(
                feature.getOrm().runInTransaction(session -> {
                    var managed = session.get(nl.hauntedmc.dataregistry.api.entities.PlayerEntity.class, ref.getId());
                    return managed != null ? managed.getUsername() : null;
                })
        );
    }

}
