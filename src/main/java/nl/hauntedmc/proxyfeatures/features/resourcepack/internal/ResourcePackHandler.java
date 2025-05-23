package nl.hauntedmc.proxyfeatures.features.resourcepack.internal;

import com.velocitypowered.api.event.Continuation;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.player.ResourcePackInfo;
import nl.hauntedmc.proxyfeatures.features.resourcepack.ResourcePack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.UUID;

public class ResourcePackHandler {
    private final ResourcePack feature;
    private final ProxyServer server;
    private final HashMap<UUID, Continuation> configurationBlockMap;

    public ResourcePackHandler(ResourcePack feature) {
        this.feature = feature;
        this.server = feature.getPlugin().getProxy();
        this.configurationBlockMap = new HashMap<>();
    }

    /** Build a pack from arbitrary url+hash. */
    public @NotNull ResourcePackInfo buildPackInfo(String url, byte[] hash) {
        return server.createResourcePackBuilder(url)
                .setHash(hash)
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
        configurationBlockMap.get(uniqueId).resume();
        configurationBlockMap.remove(uniqueId);
    }
}