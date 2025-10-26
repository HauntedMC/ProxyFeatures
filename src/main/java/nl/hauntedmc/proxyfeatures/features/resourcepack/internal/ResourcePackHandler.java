package nl.hauntedmc.proxyfeatures.features.resourcepack.internal;

import com.velocitypowered.api.event.Continuation;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.player.ResourcePackInfo;
import nl.hauntedmc.proxyfeatures.api.io.config.ConfigNode;
import nl.hauntedmc.proxyfeatures.api.io.config.ConfigTypes;
import nl.hauntedmc.proxyfeatures.features.resourcepack.ResourcePack;
import nl.hauntedmc.proxyfeatures.features.resourcepack.util.ResourceUtils;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ResourcePackHandler {
    private final ResourcePack feature;
    private final ProxyServer server;
    private final Map<UUID, Continuation> configurationBlockMap;
    private final Map<String, ResourcePackInfo> packInfoMap;

    public ResourcePackHandler(ResourcePack feature) {
        this.feature = feature;
        this.server = feature.getPlugin().getProxy();
        this.configurationBlockMap = new HashMap<>();
        this.packInfoMap = new HashMap<>();
        initializePacks();
    }

    /**
     * Load global and gamemode-specific packs from config and build ResourcePackInfo objects.
     * Uses our own ConfigNode/ConfigTypes (no Jackson).
     */
    private void initializePacks() {
        // Global pack
        String globalUrl = feature.getConfigHandler().getSetting("url", String.class);
        String globalHashHex = feature.getConfigHandler().getSetting("hash", String.class, "");
        byte[] globalHash = ResourceUtils.hexToBytes(globalHashHex);

        ResourcePackInfo globalInfo = buildPackInfo(globalUrl, globalHash);
        packInfoMap.put("global", globalInfo);

        // Mode packs: Map<String, List<Map<?, ?>>>
        ConfigNode modesNode = feature.getConfigHandler().node("mode-packs");
        for (Map.Entry<String, ConfigNode> entry : modesNode.children().entrySet()) {
            String mode = entry.getKey().toLowerCase();
            ConfigNode modeNode = entry.getValue();

            // Use wildcard Map to avoid raw-type warnings
            @SuppressWarnings("unchecked")
            List<Map<?, ?>> defs = (List<Map<?, ?>>) (List<?>) modeNode.listOf(Map.class);
            if (defs == null || defs.isEmpty()) continue;

            Map<?, ?> def0 = defs.getFirst(); // or defs.get(0)
            String url = ConfigTypes.convertOrDefault(def0.get("url"), String.class, null);
            String hashHex = ConfigTypes.convertOrDefault(def0.get("hash"), String.class, "");

            if (url == null || url.isBlank()) continue;

            byte[] hash = ResourceUtils.hexToBytes(hashHex);
            ResourcePackInfo info = buildPackInfo(url, hash);
            packInfoMap.put(mode, info);
        }
    }

    /** Retrieve a pre-built ResourcePackInfo by key ("global" or gamemode name). */
    public ResourcePackInfo getPackInfo(@NotNull String key) {
        return packInfoMap.get(key.toLowerCase());
    }

    /** Build a pack from arbitrary url+hash. */
    public @NotNull ResourcePackInfo buildPackInfo(String url, byte[] hash) {
        return server.createResourcePackBuilder(url)
                .setHash(hash)
                .setId(UUID.nameUUIDFromBytes(url.getBytes()))
                .setPrompt(feature.getLocalizationHandler()
                        .getMessage("resourcepack.prompt")
                        .build())
                .setShouldForce(true)
                .build();
    }

    public void blockConfiguration(UUID uniqueId, Continuation continuation) {
        configurationBlockMap.put(uniqueId, continuation);
    }

    public void unblockConfiguration(UUID uniqueId) {
        Continuation cont = configurationBlockMap.remove(uniqueId);
        if (cont != null) cont.resume();
    }
}
