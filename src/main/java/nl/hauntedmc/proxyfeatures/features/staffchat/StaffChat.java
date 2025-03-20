package nl.hauntedmc.proxyfeatures.features.staffchat;

import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.features.BaseFeature;
import nl.hauntedmc.proxyfeatures.features.staffchat.internal.ChatChannelHandler;
import nl.hauntedmc.proxyfeatures.features.staffchat.listener.ChatListener;
import nl.hauntedmc.proxyfeatures.features.staffchat.listener.ConnectListener;
import nl.hauntedmc.proxyfeatures.features.staffchat.meta.Meta;
import nl.hauntedmc.proxyfeatures.localization.MessageMap;

import java.util.HashMap;
import java.util.Map;

public class StaffChat extends BaseFeature<Meta> {

    private ChatChannelHandler chatChannelHandler;

    public StaffChat(ProxyFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public Map<String, Object> getDefaultConfig() {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("enabled", false);
        defaults.put("staff_prefix", "!");
        defaults.put("team_prefix", "@");
        defaults.put("admin_prefix", "#");
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messageMap = new MessageMap();
        messageMap.add("staffchat.staff_format", "&8[&bStaffChat&68] &7[{server}] &7{player}: &b{message}");
        messageMap.add("staffchat.team_format", "&8[&eTeamChat&6] &7[{server}] &7{player}: &e{message}");
        messageMap.add("staffchat.admin_format", "&8[&cAdminChat&6] &7[{server}] &7{player}: &c{message}");
        // Extra system messages for staff channel:
        messageMap.add("staffchat.staff_join", "&6[&bStaffChat&6] &a{player} &7is gejoined.");
        messageMap.add("staffchat.staff_leave", "&6[&bStaffChat&6] &c{player} &7is geleaved.");
        messageMap.add("staffchat.staff_switch", "&6[&bStaffChat&6] &a{player} &7is geswitched van &c{from}&7 naar &c{to}&7.");
        return messageMap;
    }

    @Override
    public void initialize() {
        this.chatChannelHandler = new ChatChannelHandler(this);

        getPlugin().getProxy().getAllPlayers().forEach(player -> {
            chatChannelHandler.getChannels().values().forEach(channel -> {
                if (player.hasPermission(channel.getPermission())) {
                    chatChannelHandler.addViewer(channel, player);
                }
            });
        });
        getLifecycleManager().getListenerManager().registerListener(new ChatListener(this));
        getLifecycleManager().getListenerManager().registerListener(new ConnectListener(this));
    }

    @Override
    public void disable() {
    }

    public ChatChannelHandler getChatChannelHandler() {
        return chatChannelHandler;
    }
}
