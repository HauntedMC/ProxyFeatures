package nl.hauntedmc.proxyfeatures.features.playerlanguage.service;

import com.velocitypowered.api.proxy.Player;
import nl.hauntedmc.commonlib.localization.Language;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerLanguageEntity;
import nl.hauntedmc.proxyfeatures.features.playerlanguage.PlayerLanguage;
import nl.hauntedmc.proxyfeatures.features.playerlanguage.api.LanguageAPI;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LanguageService implements LanguageAPI {

    private final PlayerLanguage feature;
    private final ORMContext orm;

    // Fast in-memory cache: UUID -> Language
    private final Map<UUID, Language> langCache = new ConcurrentHashMap<>();
    // Optional micro-cache: UUID -> playerId (to avoid re-querying PlayerEntity)
    private final Map<UUID, Long> idCache = new ConcurrentHashMap<>();

    public LanguageService(PlayerLanguage feature, ORMContext orm) {
        this.feature = feature;
        this.orm = orm;
    }

    /** Warm cache on login (non-fatal if PlayerEntity not yet persisted). */
    public void warm(UUID uuid) {
        get(uuid); // lazy loads into cache
        // also try to cache the playerId if possible
        loadPlayerId(uuid).ifPresent(id -> idCache.put(uuid, id));
    }

    /** Forget on quit to keep memory small. */
    public void forget(UUID uuid) {
        langCache.remove(uuid);
        idCache.remove(uuid);
    }

    // === API ===
    @Override
    public Language get(UUID playerUuid) {
        // 1) Fast path: cache
        Language cached = langCache.get(playerUuid);
        if (cached != null) return cached;

        // 2) Load from DB, and if missing, CREATE a default row (NL)
        Language resolved = orm.runInTransaction(session -> {
            // Resolve PlayerEntity; if not there yet, we can’t create the row
            PlayerEntity p = session.createQuery("FROM PlayerEntity WHERE uuid = :u", PlayerEntity.class)
                    .setParameter("u", playerUuid.toString())
                    .uniqueResult();
            if (p == null) {
                // DataRegistry hasn’t persisted the PlayerEntity yet (plugin order/timing).
                // Return NL for now; a later call will create the row once PlayerEntity exists.
                return null;
            }

            Long pid = p.getId();
            idCache.put(playerUuid, pid);

            PlayerLanguageEntity ple = session.get(PlayerLanguageEntity.class, pid);
            if (ple == null) {
                // CREATE default row once and persist it
                ple = new PlayerLanguageEntity();
                ple.setPlayer(p);
                ple.setLanguage(Language.NL);      // default
                ple.setUpdatedAt(Instant.now());
                session.persist(ple);
                return ple.getLanguage();
            } else {
                return ple.getLanguage();
            }
        });

        // If PlayerEntity didn't exist yet, we still return NL (but won’t have persisted).
        if (resolved == null) resolved = Language.NL;

        // 3) Cache it for fast future reads in this session
        langCache.put(playerUuid, resolved);
        return resolved;
    }


    @Override
    public void set(UUID playerUuid, Language language) {
        Objects.requireNonNull(language, "language");

        orm.runInTransaction(session -> {
            // find playerId
            Long pid = idCache.get(playerUuid);
            PlayerEntity p;
            if (pid == null) {
                p = session.createQuery("FROM PlayerEntity WHERE uuid = :u", PlayerEntity.class)
                        .setParameter("u", playerUuid.toString())
                        .uniqueResult();
                if (p == null) {
                    // PlayerEntity not yet persisted by DataRegistry; just exit softly.
                    return null;
                }
                pid = p.getId();
                idCache.put(playerUuid, pid);
            } else {
                p = session.get(PlayerEntity.class, pid);
                if (p == null) {
                    idCache.remove(playerUuid);
                    return null;
                }
            }

            PlayerLanguageEntity ple = session.get(PlayerLanguageEntity.class, pid);
            if (ple == null) {
                ple = new PlayerLanguageEntity();
                ple.setPlayer(p);
                ple.setLanguage(language);
                ple.setUpdatedAt(Instant.now());
                session.persist(ple);
            } else {
                ple.setLanguage(language);
                ple.setUpdatedAt(Instant.now());
                session.merge(ple);
            }
            return null;
        });

        langCache.put(playerUuid, language);
    }

    // === helpers ===

    private Optional<Long> loadPlayerId(UUID uuid) {
        return Optional.ofNullable(orm.runInTransaction(session -> {
            PlayerEntity p = session.createQuery("FROM PlayerEntity WHERE uuid = :u", PlayerEntity.class)
                    .setParameter("u", uuid.toString())
                    .uniqueResult();
            return p != null ? p.getId() : null;
        }));
    }

    // Resolve UUID by (online) name or fall back to DB username lookup.
    public Optional<UUID> resolveUuidByName(String username) {
        if (username == null || username.isBlank()) return Optional.empty();

        // Online (fast path)
        var online = feature.getPlugin().getProxy().getAllPlayers().stream()
                .filter(pl -> pl.getUsername().equalsIgnoreCase(username))
                .map(Player::getUniqueId)
                .findFirst();
        if (online.isPresent()) return online;

        // DB fallback
        return findUuidByName(username);
    }

    /** Exact username match (case-sensitive or insensitive as you prefer); uses DataRegistry. */
    public Optional<UUID> findUuidByName(String username) {
        return Optional.ofNullable(orm.runInTransaction(session -> {
            var p = session.createQuery("FROM PlayerEntity WHERE username = :u", PlayerEntity.class)
                    .setParameter("u", username)
                    .uniqueResult();
            return (p != null && p.getUuid() != null) ? UUID.fromString(p.getUuid()) : null;
        }));
    }
}
