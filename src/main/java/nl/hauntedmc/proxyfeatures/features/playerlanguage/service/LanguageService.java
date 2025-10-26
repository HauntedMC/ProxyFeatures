package nl.hauntedmc.proxyfeatures.features.playerlanguage.service;

import com.velocitypowered.api.proxy.Player;
import nl.hauntedmc.proxyfeatures.api.io.localization.Language;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerLanguageEntity;
import nl.hauntedmc.proxyfeatures.common.util.LanguageUtils;
import nl.hauntedmc.proxyfeatures.features.playerlanguage.PlayerLanguage;
import nl.hauntedmc.proxyfeatures.features.playerlanguage.api.LanguageAPI;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LanguageService implements LanguageAPI {

    // Helper to carry both the language and whether this is a new entry
    private record LoadResult(Language language, boolean createdDefault) {}

    private static final String DEFAULT_CODE = "EN"; // fallback for unknowns

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
        get(uuid); // lazy loads into cache / persists default if possible
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
        // 1) Cache
        Language cached = langCache.get(playerUuid);
        if (cached != null) return cached;

        // Pre-compute smart default
        Language smartDefault = computeDefaultLanguage(playerUuid);

        // 2) Load or create default row
        LoadResult result = orm.runInTransaction(session -> {
            PlayerEntity p = session.createQuery("FROM PlayerEntity WHERE uuid = :u", PlayerEntity.class)
                    .setParameter("u", playerUuid.toString())
                    .uniqueResult();
            if (p == null) {
                // PlayerEntity not yet present; return null -> we'll fall back to smartDefault (no cache/persist yet)
                return null;
            }

            Long pid = p.getId();
            idCache.put(playerUuid, pid);

            PlayerLanguageEntity ple = session.get(PlayerLanguageEntity.class, pid);
            if (ple == null) {
                // First time: persist default and mark as created
                ple = new PlayerLanguageEntity();
                ple.setPlayer(p);
                ple.setLanguage(toCode(smartDefault));
                session.persist(ple);
                return new LoadResult(smartDefault, true);
            } else {
                return new LoadResult(fromCode(ple.getLanguage()), false);
            }
        });

        // If PlayerEntity doesn't exist yet, return smart default without caching or messaging
        if (result == null) {
            return smartDefault;
        }

        Language resolved = result.language();
        // 3) Cache for quick future reads
        langCache.put(playerUuid, resolved);

        // 4) If we just created the default, notify the player (delayed)
        if (result.createdDefault()) {
            feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(() -> feature.getPlugin().getProxy().getPlayer(playerUuid).ifPresent(player -> {
                var msg = feature.getLocalizationHandler()
                        .getMessage("language.default_auto")
                        .with("language", resolved.name())
                        .forAudience(player)
                        .build();
                player.sendMessage(msg);
            }), Duration.ofSeconds(5L));
        }

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

    // --- language code mapping ---

    private static String toCode(Language lang) {
        // Normalize to UPPERCASE symbolic code (e.g., "NL", "DE", "EN")
        return (lang == null) ? DEFAULT_CODE : lang.name().toUpperCase(Locale.ROOT);
    }

    private static Language fromCode(String code) {
        if (code == null || code.isBlank()) return Language.EN;
        try {
            return Language.valueOf(code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            // Unknown/legacy code stored -> fall back safely
            return Language.EN;
        }
    }

    /**
     * Compute a smart default based on the player's country from CountryAPI.
     * NL group -> NL; DE group -> DE; otherwise EN.
     */
    private Language computeDefaultLanguage(UUID uuid) {
        String cc = LanguageUtils.getPlayerCountry(uuid);

        // NL group
        if (cc.equals("NL") || cc.equals("BE") || cc.equals("SR") || cc.equals("CW") || cc.equals("AW") || cc.equals("SX")) {
            return Language.NL;
        }
        // DE group
        if (cc.equals("DE") || cc.equals("LI") || cc.equals("AT") || cc.equals("CH")) {
            return Language.DE;
        }
        // default
        return Language.EN;
    }
}
