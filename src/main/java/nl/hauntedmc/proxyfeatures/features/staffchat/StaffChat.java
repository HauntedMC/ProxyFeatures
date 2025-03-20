package nl.hauntedmc.proxyfeatures.features.staffchat;

import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.features.BaseFeature;
import nl.hauntedmc.proxyfeatures.features.staffchat.internal.StaffChatHandler;
import nl.hauntedmc.proxyfeatures.features.staffchat.listener.ChatListener;
import nl.hauntedmc.proxyfeatures.features.staffchat.listener.ConnectListener;
import nl.hauntedmc.proxyfeatures.features.staffchat.meta.Meta;
import nl.hauntedmc.proxyfeatures.localization.MessageMap;

import java.util.HashMap;
import java.util.Map;

public class StaffChat extends BaseFeature<Meta> {

    private StaffChatHandler staffChatHandler;

    public StaffChat(ProxyFeatures plugin) {
        super(plugin, new Meta()); // No meta needed; otherwise create one similar to PlayerList or Logger.
    }

    @Override
    public Map<String, Object> getDefaultConfig() {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("enabled", false);
        defaults.put("prefix", "!");
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messageMap = new MessageMap();
        messageMap.add("staffchat.format", "&6[&bStaffChat&6] &7[{server}] &7{player}: &6{message}");
        return messageMap;
    }

    @Override
    public void initialize() {
        this.staffChatHandler = new StaffChatHandler(this);

        getPlugin().getProxy().getAllPlayers().forEach(player -> {
            if (player.hasPermission("proxyfeatures.feature.staffchat.staff")) {
                staffChatHandler.addViewer(player);
            }
        });

        getLifecycleManager().getListenerManager().registerListener(new ChatListener(this));
        getLifecycleManager().getListenerManager().registerListener(new ConnectListener(this));
    }

    @Override
    public void disable() {
    }

    public StaffChatHandler getStaffChatHandler() {
        return staffChatHandler;
    }
}
