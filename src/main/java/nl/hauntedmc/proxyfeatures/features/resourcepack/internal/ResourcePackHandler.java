package nl.hauntedmc.proxyfeatures.features.resourcepack.internal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.velocitypowered.api.event.Continuation;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.player.ResourcePackInfo;
import nl.hauntedmc.proxyfeatures.api.util.parse.ConfigParseUtils;
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
     */
    private void initializePacks() {
        String url = (String) feature.getConfigHandler().getSetting("url");
        byte[] hash = ResourceUtils.hexToBytes((String) feature.getConfigHandler().getSetting("hash"));
        ResourcePackInfo globalInfo = buildPackInfo(url, hash);
        packInfoMap.put("global", globalInfo);

        // Gamemode packs
        Object rawModePacks = feature.getConfigHandler().getSetting("mode-packs");
        if (rawModePacks != null) {
            Map<String, List<PackDefinition>> modePacks = ConfigParseUtils.convert(
                    rawModePacks,
                    new TypeReference<>() {
                    }
            );
            for (Map.Entry<String, List<PackDefinition>> entry : modePacks.entrySet()) {
                String mode = entry.getKey().toLowerCase();
                List<PackDefinition> defs = entry.getValue();
                if (!defs.isEmpty()) {
                    PackDefinition def = defs.getFirst();
                    byte[] modeHash = ResourceUtils.hexToBytes(def.hash());
                    ResourcePackInfo info = buildPackInfo(def.url(), modeHash);
                    packInfoMap.put(mode, info);
                }
            }
        }
    }

    /**
     * Retrieve a pre-built ResourcePackInfo by key ("global" or gamemode name).
     * @param key the identifier for the pack
     * @return the ResourcePackInfo, or null if not found
     */
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
        if (cont != null) {
            cont.resume();
        }
    }
}
