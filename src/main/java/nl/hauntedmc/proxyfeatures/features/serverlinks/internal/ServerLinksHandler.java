package nl.hauntedmc.proxyfeatures.features.serverlinks.internal;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.util.ServerLink;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;

public class ServerLinksHandler {

    private final List<ServerLink> serverLinks;

    public ServerLinksHandler() {
        this.serverLinks = new ArrayList<>();
        initializeServerLinks();
    }

    private void initializeServerLinks() {
        serverLinks.add(ServerLink.serverLink(Component.text("Discord"), "https://www.hauntedmc.nl/discord"));
        serverLinks.add(ServerLink.serverLink(Component.text("Store"), "https:/store.hauntedmc.nl"));
        serverLinks.add(ServerLink.serverLink(Component.text("Vote"), "https://www.hauntedmc.nl/vote"));
        serverLinks.add(ServerLink.serverLink(ServerLink.Type.WEBSITE, "https://www.hauntedmc.nl"));
        serverLinks.add(ServerLink.serverLink(ServerLink.Type.SUPPORT, "https://www.hauntedmc.nl/support"));
        serverLinks.add(ServerLink.serverLink(ServerLink.Type.FORUMS, "https://www.hauntedmc.nl/forums"));
        serverLinks.add(ServerLink.serverLink(ServerLink.Type.COMMUNITY_GUIDELINES, "https://www.hauntedmc.nl/regels"));
    }

    public void applyLinks(Player player) {
        player.setServerLinks(serverLinks);
    }
}
