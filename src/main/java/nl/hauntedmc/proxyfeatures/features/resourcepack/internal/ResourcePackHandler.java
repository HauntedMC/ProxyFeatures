package nl.hauntedmc.proxyfeatures.features.resourcepack.internal;

import com.velocitypowered.api.event.Continuation;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.player.ResourcePackInfo;
import nl.hauntedmc.proxyfeatures.api.io.config.ConfigNode;
import nl.hauntedmc.proxyfeatures.api.io.config.ConfigService;
import nl.hauntedmc.proxyfeatures.api.io.config.ConfigView;
import nl.hauntedmc.proxyfeatures.features.resourcepack.ResourcePack;
import nl.hauntedmc.proxyfeatures.features.resourcepack.util.ResourceUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads resource pack definitions from local/resourcepacks.yml using the unified ConfigView API
 * and builds {@link ResourcePackInfo} instances for the global pack and per-gamemode packs.
 */
public class ResourcePackHandler {
    private static final String RESOURCE_FILE = "local/resourcepacks.yml";

    private final ResourcePack feature;
    private final ProxyServer server;
    private final Logger logger;
    private final Map<UUID, Continuation> configurationBlockMap;
    private final Map<String, ResourcePackInfo> packInfoMap;

    public ResourcePackHandler(ResourcePack feature) {
        this.feature = feature;
        this.server = feature.getPlugin().getProxy();
        this.logger = feature.getPlugin().getLogger();
        this.configurationBlockMap = new ConcurrentHashMap<>();
        this.packInfoMap = new ConcurrentHashMap<>();
        initializePacks();
    }

    /**
     * Load global and per-mode packs from local/resourcepacks.yml (copied from JAR if missing).
     */
    private void initializePacks() {
        packInfoMap.clear();

        // Open or create local/resourcepacks.yml with defaults copied from JAR if present
        ConfigView store = new ConfigService(feature.getPlugin()).view(RESOURCE_FILE, /* copyDefaults */ true);

        // ---- Global pack
        ConfigNode global = store.node("global");
        String globalUrl = global.get("url").as(String.class, null);
        String globalHashHex = global.get("hash").as(String.class, "");
        boolean globalForce = global.get("force").as(Boolean.class, true);
        String globalPromptKey = global.get("prompt_key").as(String.class, "resourcepack.prompt");

        if (isPresent(globalUrl)) {
            byte[] hash = normalizedSha1(globalHashHex, "global");
            ResourcePackInfo globalInfo = buildPackInfo(globalUrl, hash, globalForce, globalPromptKey);
            packInfoMap.put("global", globalInfo);
        } else {
            logger.warn("[ProxyFeatures] ResourcePacks: no global.url set in {} — skipping global pack.", RESOURCE_FILE);
        }

        // ---- Mode packs as a MAP (mode -> {url, hash, ...})
        ConfigNode modes = store.node("resourcepacks");
        for (Map.Entry<String, ConfigNode> e : modes.children().entrySet()) {
            String mode = e.getKey();
            ConfigNode def = e.getValue();

            String url = def.get("url").as(String.class, null);
            String hashHex = def.get("hash").as(String.class, "");
            boolean force = def.get("force").as(Boolean.class, globalForce); // inherit global default
            String promptKey = def.get("prompt_key").as(String.class, globalPromptKey);

            if (!isPresent(url)) {
                logger.warn("[ProxyFeatures] ResourcePacks: mode '{}' missing url — skipping.", mode);
                continue;
            }

            byte[] hash = normalizedSha1(hashHex, "mode:" + mode);
            ResourcePackInfo info = buildPackInfo(url, hash, force, promptKey);
            packInfoMap.put(mode.toLowerCase(Locale.ROOT), info);
        }
    }

    /** Retrieve a pre-built ResourcePackInfo by key ("global" or gamemode name). */
    public ResourcePackInfo getPackInfo(@NotNull String key) {
        return packInfoMap.get(key.toLowerCase(Locale.ROOT));
    }

    /** Build a pack from arbitrary url+hash with force + localized prompt key. */
    public @NotNull ResourcePackInfo buildPackInfo(String url, byte[] hash, boolean force, String promptKey) {
        return server.createResourcePackBuilder(url)
                .setHash(hash)
                .setId(UUID.nameUUIDFromBytes(url.getBytes(StandardCharsets.UTF_8)))
                .setPrompt(feature.getLocalizationHandler().getMessage(promptKey).build())
                .setShouldForce(force)
                .build();
    }

    /** For legacy callers that didn’t specify force/prompt. Defaults: force=true, prompt_key=resourcepack.prompt */
    public @NotNull ResourcePackInfo buildPackInfo(String url, byte[] hash) {
        return buildPackInfo(url, hash, true, "resourcepack.prompt");
    }

    public void blockConfiguration(UUID uniqueId, Continuation continuation) {
        Continuation previous = configurationBlockMap.put(uniqueId, continuation);
        if (previous != null && previous != continuation) {
            try {
                previous.resume();
            } catch (Exception ex) {
                logger.debug("[ProxyFeatures] ResourcePacks: failed to resume previous continuation: {}", ex.getMessage());
            }
        }
    }

    public void unblockConfiguration(UUID uniqueId) {
        Continuation cont = configurationBlockMap.remove(uniqueId);
        if (cont != null) {
            try {
                cont.resume();
            } catch (Exception ex) {
                logger.debug("[ProxyFeatures] ResourcePacks: failed to resume continuation: {}", ex.getMessage());
            }
        }
    }

    public void unblockAllConfigurations() {
        for (Continuation cont : configurationBlockMap.values()) {
            try {
                cont.resume();
            } catch (Exception ex) {
                logger.debug("[ProxyFeatures] ResourcePacks: failed to resume continuation during shutdown: {}", ex.getMessage());
            }
        }
        configurationBlockMap.clear();
    }

    /** Validate/normalize SHA-1 hex to 20-byte array, logging a warning and zero-filling if invalid. */
    private byte[] normalizedSha1(String hex, String where) {
        byte[] bytes = ResourceUtils.hexToBytes(hex == null ? "" : hex.trim());
        if (bytes.length != 20) {
            logger.warn("[ProxyFeatures] ResourcePacks: {} hash is missing or not SHA-1 (40 hex chars). Using zeros.", where);
            return new byte[20];
        }
        return bytes;
    }

    private boolean isPresent(String s) {
        return s != null && !s.isBlank();
    }
}
