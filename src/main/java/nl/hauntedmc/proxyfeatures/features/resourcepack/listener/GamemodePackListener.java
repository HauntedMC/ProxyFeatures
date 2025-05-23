package nl.hauntedmc.proxyfeatures.features.resourcepack.listener;

import com.fasterxml.jackson.core.type.TypeReference;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import nl.hauntedmc.commonlib.util.ConfigParseUtils;
import nl.hauntedmc.proxyfeatures.features.resourcepack.ResourcePack;
import nl.hauntedmc.proxyfeatures.features.resourcepack.internal.PackDefinition;
import nl.hauntedmc.proxyfeatures.features.resourcepack.internal.ResourcePackHandler;
import nl.hauntedmc.proxyfeatures.features.resourcepack.util.ResourceUtils;

import java.util.List;
import java.util.Map;

public class GamemodePackListener {
    private final ResourcePackHandler handler;
    private final Map<String, List<PackDefinition>> modePacks;

    public GamemodePackListener(ResourcePack feature) {
        this.handler = feature.getHandler();
        Object rawModePacks = feature.getConfigHandler().getSetting("mode-packs");
        this.modePacks =
                ConfigParseUtils.convert(
                        rawModePacks,
                        new TypeReference<>() {}
                );
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        RegisteredServer server = event.getServer();
        String mode = server.getServerInfo()
                .getName()
                .toLowerCase();

        List<PackDefinition> defs = modePacks.get(mode);
        if (defs == null) return;
        for (PackDefinition def : defs) {
            byte[] hash = ResourceUtils.hexToBytes(def.hash());
            player.sendResourcePackOffer(
                    handler.buildPackInfo(def.url(), hash)
            );
        }
    }
}
