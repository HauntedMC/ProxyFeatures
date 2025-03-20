package nl.hauntedmc.proxyfeatures.features.playerlist;

import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.features.BaseFeature;
import nl.hauntedmc.proxyfeatures.features.playerlist.command.GlobalListCommand;
import nl.hauntedmc.proxyfeatures.features.playerlist.command.ListCommand;
import nl.hauntedmc.proxyfeatures.features.playerlist.internal.PlayerListHandler;
import nl.hauntedmc.proxyfeatures.features.playerlist.meta.Meta;
import nl.hauntedmc.proxyfeatures.localization.MessageMap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlayerList extends BaseFeature<Meta> {

    private PlayerListHandler playerListHandler;

    public PlayerList(ProxyFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public Map<String, Object> getDefaultConfig() {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("enabled", false);
        defaults.put("blacklist", List.of());
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messageMap = new MessageMap();

        // Command messages:
        messageMap.add("playerlist.not_on_server", "&cJe bent niet verbonden met een server.");
        messageMap.add("playerlist.server_not_found", "&cDe server {server} bestaat niet.");
        messageMap.add("playerlist.command_only_players", "&cDit commando kan alleen door spelers worden gebruikt.");
        messageMap.add("playerlist.usage", "&cGebruik: /list [server]");

        // Global list messages:
        messageMap.add("playerlist.total_players_none", "&eEr zijn geen spelers online op HauntedMC.");
        messageMap.add("playerlist.total_players_one", "&eEr is 1 speler online op HauntedMC.");
        messageMap.add("playerlist.total_players_multiple", "&eEr zijn {count} spelers online op HauntedMC.");
        messageMap.add("playerlist.server_bullet_online", "&a●");
        messageMap.add("playerlist.server_bullet_offline", "&c●");
        messageMap.add("playerlist.server_dash", "&7 - ");
        messageMap.add("playerlist.server_online_count", "&e{online}");
        messageMap.add("playerlist.server_name_current", "&a{server}");
        messageMap.add("playerlist.server_name_other", "&7{server}");
        messageMap.add("playerlist.server_connect", " &8[&7verbinden&8]");
        messageMap.add("playerlist.server_connect_hover", "&7Verbind met {server}");
        messageMap.add("playerlist.server_players", " &8[&7spelers&8]");
        messageMap.add("playerlist.server_players_hover", "&7Bekijk spelers op {server}");
        messageMap.add("playerlist.global_tip", "&bTip: &7Gebruik &e/list&7 voor de spelerlijst van deze server.");

        // Player list messages:
        messageMap.add("playerlist.server_count_none", "&eEr zijn geen spelers op {server}.");
        messageMap.add("playerlist.server_count_one", "&eEr is 1 speler online op {server}.");
        messageMap.add("playerlist.server_count_multiple", "&eEr zijn {count} spelers online op {server}.");
        messageMap.add("playerlist.server_players_list", "&b  Spelers: &7{players}");
        messageMap.add("playerlist.server_tip_global", "&bTip: &7Gebruik &e/glist&7 voor een globaal overzicht.");

        return messageMap;
    }


    @Override
    public void initialize() {
        this.playerListHandler = new PlayerListHandler(this);
        getLifecycleManager().getCommandManager().registerFeatureCommand(new ListCommand(this));
        getLifecycleManager().getCommandManager().registerFeatureCommand(new GlobalListCommand(this));
    }

    @Override
    public void disable() {
        // Optionally add cleanup logic.
    }

    public PlayerListHandler getPlayerListHandler() {
        return playerListHandler;
    }
}
