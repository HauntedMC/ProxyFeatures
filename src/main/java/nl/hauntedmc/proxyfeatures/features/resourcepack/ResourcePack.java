package nl.hauntedmc.proxyfeatures.features.resourcepack;

import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.proxyfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.proxyfeatures.features.VelocityBaseFeature;
import nl.hauntedmc.proxyfeatures.features.resourcepack.command.ResourcePackCommand;
import nl.hauntedmc.proxyfeatures.features.resourcepack.internal.ResourcePackHandler;
import nl.hauntedmc.proxyfeatures.features.resourcepack.listener.PlayerListener;
import nl.hauntedmc.proxyfeatures.features.resourcepack.listener.ResourcePackStatusListener;
import nl.hauntedmc.proxyfeatures.features.resourcepack.meta.Meta;

import java.util.List;
import java.util.Map;

public class ResourcePack extends VelocityBaseFeature<Meta> {

    private ResourcePackHandler handler;

    public ResourcePack(ProxyFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);
        defaults.put("url", "https://hauntedmc.nl/HauntedMC-GlobalPack.zip");
        // SHA-1 hash of the pack; clients will only redownload if this changes
        defaults.put("hash", "");

        defaults.put("mode-packs", Map.of(
                "skyblock", List.of(
                        Map.of(
                                "url", "https://hauntedmc.nl/HauntedMC-SkyblockPack.zip",
                                "hash", ""
                        )
                ),
                "minigames", List.of(
                        Map.of(
                                "url", "HauntedMC-MinigamesPack.zip",
                                "hash", ""
                        )
                )
        ));
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messages = new MessageMap();
        messages.add("resourcepack.prompt",
                "&eWe willen graag dat je onze resource pack accepteert voor een verbeterde speelervaring.");
        messages.add("resourcepack.accepted",
                "&aBedankt voor het accepteren, de download begint nu.");
        messages.add("resourcepack.loaded",
                "&aDe resource pack is succesvol geladen. Geniet van de verbeterde speelervaring.");
        messages.add("resourcepack.kick_declined",
                "&cJe moet de resource pack accepteren om op deze server te kunnen spelen.");
        messages.add("resourcepack.kick_failed",
                "&cDe resource pack kon niet worden gedownload. Probeer het opnieuw.");
        messages.add("resourcepack.reload_failed",
                "&cHet herladen van de resource pack is mislukt.");
        messages.add("resourcepack.url_invalid",
                "&cDe URL van de resource pack kon niet worden geladen.");
        messages.add("resourcepack.downloaded",
                "&aDe resource pack is succesvol gedownload.");
        messages.add("resourcepack.cmd_usage",
                "&eGebruik: /resourcepack list [<player>]");
        messages.add("resourcepack.cmd_notPlayer",
                "&cAlleen spelers kunnen dit commando uitvoeren.");
        messages.add("resourcepack.cmd_playerNotFound",
                "&cSpeler {player} niet gevonden.");
        messages.add("resourcepack.cmd_header",
                "&aResource pack status voor {player}:");
        messages.add("resourcepack.cmd_entry",
                "&7{pack}: {status}");
        return messages;
    }

    @Override
    public void initialize() {
        this.handler = new ResourcePackHandler(this);
        getLifecycleManager().getListenerManager().registerListener(new PlayerListener(this));
        getLifecycleManager().getListenerManager().registerListener(new ResourcePackStatusListener(this));
        getLifecycleManager().getCommandManager().registerFeatureCommand(new ResourcePackCommand(this));
    }

    @Override
    public void disable() {
    }

    public ResourcePackHandler getResourcePackHandler() {
        return handler;
    }
}