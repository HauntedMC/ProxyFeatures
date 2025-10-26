package nl.hauntedmc.proxyfeatures.features.sanctions.service;

import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.proxyfeatures.api.util.text.placeholder.MessagePlaceholders;
import nl.hauntedmc.proxyfeatures.features.sanctions.Sanctions;
import nl.hauntedmc.proxyfeatures.features.sanctions.entity.SanctionEntity;
import nl.hauntedmc.proxyfeatures.features.sanctions.entity.SanctionType;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SanctionsService {

    private static final int REASON_MAX = 512;
    private static final Pattern DURATION_TOKEN = Pattern.compile("(\\d+)(y|mo|w|d|h|m|s)", Pattern.CASE_INSENSITIVE);

    private final Sanctions feature;

    // in-memory cache for mutes: playerId -> active mute
    private final Map<Long, SanctionEntity> muteCache = new ConcurrentHashMap<>();

    public SanctionsService(Sanctions feature) {
        this.feature = feature;
    }

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
        if (target != null) muteCache.put(target.getId(), s);
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
        String sanitized = sanitizeReason(reason);
        Instant now = Instant.now();
        return feature.getOrm().runInTransaction(session -> {
            // Deactivate any pre-existing active sanctions of same type & target/IP in the same transaction
            if (target != null) {
                session.createMutationQuery(
                                "UPDATE SanctionEntity s " +
                                        "SET s.active = false " +
                                        "WHERE s.active = true AND s.type = :t AND s.targetPlayer = :tp")
                        .setParameter("t", type)
                        .setParameter("tp", target)
                        .executeUpdate();
            } else if (ip != null) {
                session.createMutationQuery(
                                "UPDATE SanctionEntity s " +
                                        "SET s.active = false " +
                                        "WHERE s.active = true AND s.type = :t AND s.targetIp = :ip")
                        .setParameter("t", type)
                        .setParameter("ip", ip)
                        .executeUpdate();
            }

            SanctionEntity s = new SanctionEntity();
            s.setType(type);
            s.setTargetPlayer(target);
            s.setTargetIp(ip);
            s.setReason(sanitized);
            s.setActorPlayer(actor);
            s.setActorName(actorName);
            s.setCreatedAt(now);
            s.setExpiresAt(expiresAt); // null = permanent
            s.setActive(true);
            session.persist(s);
            return s;
        });
    }

    private void createInstant(SanctionType type, PlayerEntity target, String ip,
                               String reason, PlayerEntity actor, String actorName) {
        String sanitized = sanitizeReason(reason);
        Instant now = Instant.now();
        feature.getOrm().runInTransaction(session -> {
            SanctionEntity s = new SanctionEntity();
            s.setType(type);
            s.setTargetPlayer(target);
            s.setTargetIp(ip);
            s.setReason(sanitized);
            s.setActorPlayer(actor);
            s.setActorName(actorName);
            s.setCreatedAt(now);
            s.setActive(false);
            s.setExpiresAt(null);
            session.persist(s);
            return null;
        });
    }

    // ===== QUERIES (with expiry awareness) =====

    public Optional<SanctionEntity> findActiveBanByPlayer(PlayerEntity p) {
        return findActive(typeSanctionQuery("targetPlayer = :tp", "tp", p, SanctionType.BAN));
    }

    public Optional<SanctionEntity> findActiveBanByIp(String ip) {
        return findActive(typeSanctionQuery("targetIp = :ip", "ip", ip, SanctionType.BAN_IP));
    }

    public Optional<SanctionEntity> findActiveMuteByPlayer(PlayerEntity p) {
        return findActive(typeSanctionQuery("targetPlayer = :tp", "tp", p, SanctionType.MUTE));
    }

    private Optional<SanctionEntity> findActive(QueryBuilder builder) {
        return feature.getOrm().runInTransaction(session -> {
            SanctionEntity s = builder.apply(session)
                    .uniqueResultOptional()
                    .orElse(null);
            if (s == null) return Optional.empty();
            if (s.isExpired(Instant.now())) {
                // auto-deactivate on read for correctness under race with sweeper
                SanctionEntity managed = session.get(SanctionEntity.class, s.getId());
                if (managed != null && managed.isActive()) managed.setActive(false);
                if (managed != null && managed.getType() == SanctionType.MUTE && managed.getTargetPlayer() != null) {
                    muteCache.remove(managed.getTargetPlayer().getId());
                }
                return Optional.empty();
            }
            return Optional.of(s);
        });
    }

    private interface QueryBuilder {
        org.hibernate.query.Query<SanctionEntity> apply(org.hibernate.Session session);
    }

    private QueryBuilder typeSanctionQuery(String where, String paramName, Object paramValue, SanctionType type) {
        return session -> session.createQuery(
                        "FROM SanctionEntity WHERE active = true AND type = :t AND " + where,
                        SanctionEntity.class)
                .setParameter("t", type)
                .setParameter(paramName, paramValue);
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
                if (managed != null && managed.isActive()) managed.setActive(false);
            }
            return null;
        });

        toDeactivate.stream()
                .filter(s -> s.getType() == SanctionType.MUTE && s.getTargetPlayer() != null)
                .forEach(s -> muteCache.remove(s.getTargetPlayer().getId()));
    }

    public void deactivateExpiredSanction(Long id) {
        if (id == null) return;
        feature.getOrm().runInTransaction(session -> {
            SanctionEntity s = session.get(SanctionEntity.class, id);
            if (s != null && s.isActive() && s.isExpired(Instant.now())) {
                s.setActive(false);
                if (s.getType() == SanctionType.MUTE && s.getTargetPlayer() != null) {
                    muteCache.remove(s.getTargetPlayer().getId());
                }
            }
            return null;
        });
    }

    // ===== MUTE CACHE =====

    public void loadActiveMuteIntoCache(Long playerId) {
        feature.getOrm().runInTransaction(session -> {
            PlayerEntity p = session.get(PlayerEntity.class, playerId);
            if (p == null) return null;
            var opt = session.createQuery("FROM SanctionEntity WHERE active = true AND type = :t AND targetPlayer = :tp",
                            SanctionEntity.class)
                    .setParameter("t", SanctionType.MUTE)
                    .setParameter("tp", p)
                    .uniqueResultOptional();
            opt.ifPresentOrElse(s -> {
                if (!s.isExpired(Instant.now())) {
                    muteCache.put(playerId, s);
                }
            }, () -> muteCache.remove(playerId));
            return null;
        });
    }

    public boolean isMuted(Long playerId) {
        SanctionEntity s = muteCache.get(playerId);
        if (s == null) return false;
        if (!s.isActive()) {
            muteCache.remove(playerId);
            return false;
        }
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
        long days = seconds / 86400;
        seconds %= 86400;
        long hours = seconds / 3600;
        seconds %= 3600;
        long minutes = seconds / 60;
        seconds %= 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (days == 0 && hours == 0 && minutes == 0) sb.append(seconds).append("s");
        String out = sb.toString().trim();
        return out.isEmpty() ? "0m" : out;
    }

    private void deactivateById(Long id) {
        feature.getOrm().runInTransaction(session -> {
            SanctionEntity s = session.get(SanctionEntity.class, id);
            if (s != null && s.isActive()) s.setActive(false);
            return null;
        });
    }

    // ===== LISTING / PAGINATION =====

    /**
     * Count sanctions for a player, optionally active only.
     */
    public long countSanctionsForPlayer(PlayerEntity p, boolean activeOnly) {
        return feature.getOrm().runInTransaction(session ->
                session.createQuery(
                                "select count(s.id) from SanctionEntity s where s.targetPlayer = :p"
                                        + (activeOnly ? " and s.active = true" : ""),
                                Long.class)
                        .setParameter("p", p)
                        .uniqueResult()
        );
    }

    /**
     * Page sanctions for a player, newest first, DB-level pagination.
     */
    public List<SanctionEntity> pageSanctionsForPlayer(PlayerEntity p, boolean activeOnly, int page, int pageSize) {
        int p1 = Math.max(1, page);
        int size = Math.max(1, pageSize);
        int offset = (p1 - 1) * size;

        return feature.getOrm().runInTransaction(session ->
                session.createQuery(
                                "from SanctionEntity s where s.targetPlayer = :p"
                                        + (activeOnly ? " and s.active = true" : "")
                                        + " order by s.createdAt desc",
                                SanctionEntity.class)
                        .setParameter("p", p)
                        .setFirstResult(offset)
                        .setMaxResults(size)
                        .list()
        );
    }

    /**
     * Count all sanctions (any type/target).
     */
    public long countAllSanctions() {
        return feature.getOrm().runInTransaction(session ->
                session.createQuery("select count(s.id) from SanctionEntity s", Long.class)
                        .uniqueResult()
        );
    }

    /**
     * Page all sanctions globally, newest first, DB-level pagination.
     */
    public List<SanctionEntity> pageAllSanctions(int page, int pageSize) {
        int p1 = Math.max(1, page);
        int size = Math.max(1, pageSize);
        int offset = (p1 - 1) * size;

        return feature.getOrm().runInTransaction(session ->
                session.createQuery("from SanctionEntity s order by s.createdAt desc", SanctionEntity.class)
                        .setFirstResult(offset)
                        .setMaxResults(size)
                        .list()
        );
    }

    // ===== UTIL =====

    /**
     * Parses tokens like: "p", "perm", "permanent" (=> null), or "30d", "7d12h", "1w3d", "2h30m", "1mo", "1y", etc.
     */
    public Instant parseLengthToExpiry(String token) {
        if (token == null) throw new IllegalArgumentException("length null");
        String t = token.trim().toLowerCase(Locale.ROOT);
        if (t.equals("p") || t.equals("perm") || t.equals("permanent")) return null; // permanent

        Matcher m = DURATION_TOKEN.matcher(t);
        long totalSeconds = 0;
        int found = 0;
        while (m.find()) {
            found++;
            long n = Long.parseLong(m.group(1));
            String unit = m.group(2).toLowerCase(Locale.ROOT);
            switch (unit) {
                case "y" -> totalSeconds += Duration.ofDays(365).getSeconds() * n;
                case "mo" -> totalSeconds += Duration.ofDays(30).getSeconds() * n;
                case "w" -> totalSeconds += Duration.ofDays(7).getSeconds() * n;
                case "d" -> totalSeconds += Duration.ofDays(n).getSeconds();
                case "h" -> totalSeconds += Duration.ofHours(n).getSeconds();
                case "m" -> totalSeconds += Duration.ofMinutes(n).getSeconds();
                case "s" -> totalSeconds += n;
                default -> throw new IllegalArgumentException("invalid unit");
            }
        }
        if (found == 0 || totalSeconds <= 0) throw new IllegalArgumentException("invalid length");
        return Instant.now().plusSeconds(totalSeconds);
    }

    public String sanitizeReason(String reason) {
        if (reason == null) return "-";
        String trimmed = reason.trim();
        if (trimmed.isEmpty()) return "-";
        if (trimmed.length() > REASON_MAX) return trimmed.substring(0, REASON_MAX);
        return trimmed;
    }

    public MessagePlaceholders placeholdersFor(SanctionEntity s) {
        var ph = MessagePlaceholders.builder();
        String dur = s.isPermanent() ? "permanent" : humanDuration(Instant.now(), s.getExpiresAt());

        // Resolve target label: prefer player username, else IP, else "-"
        String targetLabel = Optional.ofNullable(resolveUsername(s.getTargetPlayer()))
                .orElseGet(() -> s.getTargetIp() != null ? s.getTargetIp() : "-");

        // Resolve actor label: username if actor player known, else actorName if present, else "CONSOLE"
        String actorLabel = Optional.ofNullable(resolveUsername(s.getActorPlayer()))
                .orElse(Optional.ofNullable(s.getActorName()).orElse("CONSOLE"));

        String appealUrl = "-";
        try {
            Object v = feature.getConfigHandler().getSetting("appealURL");
            if (v != null) appealUrl = String.valueOf(v);
        } catch (Throwable ignored) {
        }

        ph.add("reason", Optional.ofNullable(s.getReason()).orElse("-"));
        ph.add("duration", dur);
        ph.add("target", targetLabel);
        ph.add("ip", s.getTargetIp() != null ? s.getTargetIp() : "-");
        ph.add("actor", actorLabel);
        ph.add("appeal", appealUrl);
        return ph.build();
    }

    /**
     * Safely resolve a username from a (possibly detached) PlayerEntity reference.
     */
    private String resolveUsername(PlayerEntity ref) {
        if (ref == null) return null;
        Long id = ref.getId();
        if (id == null) return null;
        return feature.getOrm().runInTransaction(session -> {
            PlayerEntity managed = session.get(PlayerEntity.class, id);
            return managed != null ? managed.getUsername() : null;
        });
    }

    public String humanDuration(Instant from, Instant to) {
        if (to == null) return "permanent";
        long seconds = Math.max(0, to.getEpochSecond() - from.getEpochSecond());
        long days = seconds / 86400;
        seconds %= 86400;
        long hours = seconds / 3600;
        seconds %= 3600;
        long minutes = seconds / 60;
        seconds %= 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (days == 0 && hours == 0 && minutes == 0) sb.append(seconds).append("s");
        String out = sb.toString().trim();
        return out.isEmpty() ? "0m" : out;
    }

    public void broadcastToStaff(String messageKey, MessagePlaceholders ph) {
        String notifyPerm = "proxyfeatures.feature.sanctions.notify";

        feature.getPlugin().getProxy().getAllPlayers().forEach(pl -> {
            if (pl.hasPermission(notifyPerm)) {
                pl.sendMessage(feature.getLocalizationHandler()
                        .getMessage(messageKey).withPlaceholders(ph).forAudience(pl).build());
            }
        });

        // Also log a concise line server-side (no components).
        feature.getLogger().info("[Sanctions] " + messageKey + " " + ph);
    }

    // Deactivate all active BANs for a player (returns true if any changed)
    public boolean deactivateActiveBanForPlayer(PlayerEntity p) {
        return feature.getOrm().runInTransaction(session -> {
            var list = session.createQuery(
                            "FROM SanctionEntity WHERE active = true AND type = :t AND targetPlayer = :tp",
                            SanctionEntity.class)
                    .setParameter("t", SanctionType.BAN)
                    .setParameter("tp", p)
                    .list();
            if (list.isEmpty()) return false;
            for (var s : list) {
                var managed = session.get(SanctionEntity.class, s.getId());
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
                            SanctionEntity.class)
                    .setParameter("t", SanctionType.MUTE)
                    .setParameter("tp", p)
                    .list();
            if (list.isEmpty()) return false;
            for (var s : list) {
                var managed = session.get(SanctionEntity.class, s.getId());
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
    public List<String> suggestActiveTargetNames(
            SanctionType type,
            String startsWith,
            int limit
    ) {
        String prefix = (startsWith == null ? "" : startsWith).toLowerCase(Locale.ROOT) + "%";
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
                            SanctionEntity.class)
                    .setParameter("t", SanctionType.BAN_IP)
                    .setParameter("ip", ip)
                    .list();
            if (list.isEmpty()) return false;
            for (var s : list) {
                var managed = session.get(SanctionEntity.class, s.getId());
                if (managed != null) managed.setActive(false);
            }
            return true;
        });
    }

    // Suggestions: active banned IPs that start with a prefix (limited)
    public List<String> suggestActiveBannedIps(String startsWith, int limit) {
        String prefix = (startsWith == null ? "" : startsWith) + "%";
        return feature.getOrm().runInTransaction(session ->
                session.createQuery(
                                "select s.targetIp from SanctionEntity s " +
                                        "where s.active = true and s.type = :t and s.targetIp is not null " +
                                        "and s.targetIp like :p order by s.targetIp asc",
                                String.class)
                        .setParameter("t", SanctionType.BAN_IP)
                        .setParameter("p", prefix)
                        .setMaxResults(limit)
                        .list()
        );
    }

    /**
     * List sanctions for a specific player, newest first. If activeOnly=true, returns only active ones.
     */
    public List<SanctionEntity> listSanctionsForPlayer(PlayerEntity p, boolean activeOnly) {
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

    /**
     * Resolve a username for a (possibly detached) PlayerEntity safely.
     */
    public Optional<String> usernameOf(PlayerEntity ref) {
        if (ref == null || ref.getId() == null) return Optional.empty();
        return Optional.ofNullable(
                feature.getOrm().runInTransaction(session -> {
                    var managed = session.get(PlayerEntity.class, ref.getId());
                    return managed != null ? managed.getUsername() : null;
                })
        );
    }

    /**
     * Check if a target UUID is currently online and exempt from sanctions.
     */
    public boolean isTargetExempt(String targetUuid) {
        try {
            UUID uuid = UUID.fromString(targetUuid);
            return feature.getPlugin().getProxy().getPlayer(uuid)
                    .map(p -> p.hasPermission("proxyfeatures.feature.sanctions.bypass"))
                    .orElse(false);
        } catch (Throwable ignored) {
            return false;
        }
    }

}
