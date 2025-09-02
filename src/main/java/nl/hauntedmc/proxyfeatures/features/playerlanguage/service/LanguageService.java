package nl.hauntedmc.proxyfeatures.features.playerlanguage.service;

import com.velocitypowered.api.proxy.Player;
import nl.hauntedmc.commonlib.localization.Language;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerLanguageEntity;
import nl.hauntedmc.proxyfeatures.features.playerlanguage.PlayerLanguage;
import nl.hauntedmc.proxyfeatures.features.playerlanguage.api.LanguageAPI;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LanguageService implements LanguageAPI {

    private static final String DEFAULT_CODE = "NL";

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

        // 2) Load from DB, and if missing, CREATE a default row (DEFAULT_CODE)
        Language resolved = orm.runInTransaction(session -> {
            // Resolve PlayerEntity; if not there yet, we can’t create the row
            PlayerEntity p = session.createQuery("FROM PlayerEntity WHERE uuid = :u", PlayerEntity.class)
                    .setParameter("u", playerUuid.toString())
                    .uniqueResult();
            if (p == null) {
                // DataRegistry hasn’t persisted the PlayerEntity yet (plugin order/timing).
                // Return default for now; a later call will create the row once PlayerEntity exists.
                return null;
            }

            Long pid = p.getId();
            idCache.put(playerUuid, pid);

            PlayerLanguageEntity ple = session.get(PlayerLanguageEntity.class, pid);
            if (ple == null) {
                // CREATE default row once and persist it (as plain string code)
                ple = new PlayerLanguageEntity();
                ple.setPlayer(p);
                ple.setLanguage(DEFAULT_CODE);
                session.persist(ple);
                return fromCode(DEFAULT_CODE);
            } else {
                return fromCode(ple.getLanguage());
            }
        });

        // If PlayerEntity didn't exist yet, we still return default (but won’t have persisted).
        if (resolved == null) resolved = fromCode(DEFAULT_CODE);

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

            String code = toCode(language);

            PlayerLanguageEntity ple = session.get(PlayerLanguageEntity.class, pid);
            if (ple == null) {
                ple = new PlayerLanguageEntity();
                ple.setPlayer(p);
                ple.setLanguage(code);
                session.persist(ple);
            } else {
                ple.setLanguage(code);
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

    private static String toCode(Language lang) {
        // Normalize to UPPERCASE symbolic code (e.g., "NL", "EN")
        return (lang == null) ? DEFAULT_CODE : lang.name().toUpperCase(Locale.ROOT);
    }

    private static Language fromCode(String code) {
        if (code == null || code.isBlank()) return Language.NL;
        try {
            return Language.valueOf(code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            // Unknown/legacy code stored -> fall back safely
            return Language.NL;
        }
    }
}
