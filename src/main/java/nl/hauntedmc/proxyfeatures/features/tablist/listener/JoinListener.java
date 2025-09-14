package nl.hauntedmc.proxyfeatures.features.tablist.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.player.TabListEntry;
import com.velocitypowered.api.util.GameProfile;
import net.kyori.adventure.text.Component;

import java.util.Collections;
import java.util.UUID;

public class JoinListener {

    public JoinListener() {
    }

    @Subscribe
    public void onServerConnected(ServerPostConnectEvent event) {
        Player player = event.getPlayer();
        for (int i = 0; i < 80; i++) {
            TabListEntry entry = TabListEntry.builder()
                    .tabList(player.getTabList())
                    .profile(new GameProfile(UUID.randomUUID(), "", Collections.emptyList()))
                    .displayName(Component.text(""))
                    .listed(true)
                    .listOrder(-1)
                    .build();
            player.getTabList().addEntry(entry);
        }
    }
}
