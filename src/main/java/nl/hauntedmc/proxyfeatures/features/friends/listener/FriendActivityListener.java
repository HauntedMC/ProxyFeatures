package nl.hauntedmc.proxyfeatures.features.friends.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.api.APIRegistry;
import nl.hauntedmc.proxyfeatures.features.friends.Friends;
import nl.hauntedmc.proxyfeatures.features.friends.entity.FriendSnapshot;
import nl.hauntedmc.proxyfeatures.features.friends.entity.FriendsService;
import nl.hauntedmc.proxyfeatures.features.friends.entity.PlayerRef;
import nl.hauntedmc.proxyfeatures.features.vanish.internal.VanishAPI;

import java.util.*;

public final class FriendActivityListener {

    private final Friends feature;
    private final FriendsService svc;
    private final Optional<VanishAPI> vanishApi;

    public FriendActivityListener(Friends feature) {
        this.feature = feature;
        this.svc = feature.getService();
        this.vanishApi = APIRegistry.get(VanishAPI.class);
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        Player subject = event.getPlayer();

        // Do not leak vanished presence/switches of the subject
        if (isVanished(subject)) return;

        String currentServer = event.getServer().getServerInfo().getName();
        Optional<String> previousServer = event.getPreviousServer()
                .map(RegisteredServer::getServerInfo)
                .map(info -> info.getName());

        FriendActivityPolicy.ConnectPlan plan = FriendActivityPolicy.classifyServerConnect(previousServer, currentServer);
        if (plan.type() == FriendActivityPolicy.ConnectType.ONLINE) {
            notifyFriendsOnline(subject, plan.to());
        } else if (plan.type() == FriendActivityPolicy.ConnectType.SWITCH) {
            notifyFriendsSwitch(subject, plan.from(), plan.to());
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player subject = event.getPlayer();
        if (isVanished(subject)) return;
        notifyFriendsOffline(subject);
    }

    private void notifyFriendsOnline(Player subject, String serverName) {
        for (Player friend : onlineFriendsOf(subject)) {
            friend.sendMessage(feature.getLocalizationHandler()
                    .getMessage("friend.notify.online")
                    .with("player", subject.getUsername())
                    .with("server", serverName)
                    .forAudience(friend)
                    .build());
        }
    }

    private void notifyFriendsOffline(Player subject) {
        for (Player friend : onlineFriendsOf(subject)) {
            friend.sendMessage(feature.getLocalizationHandler()
                    .getMessage("friend.notify.offline")
                    .with("player", subject.getUsername())
                    .forAudience(friend)
                    .build());
        }
    }

    private void notifyFriendsSwitch(Player subject, String from, String to) {
        for (Player friend : onlineFriendsOf(subject)) {
            friend.sendMessage(feature.getLocalizationHandler()
                    .getMessage("friend.notify.switch")
                    .with("player", subject.getUsername())
                    .with("from", from)
                    .with("to", to)
                    .forAudience(friend)
                    .build());
        }
    }

    /**
     * Return all online friends (including vanished receivers),
     * so vanished players still receive notifications about their friends.
     * Uses snapshots and direct UUID lookups to avoid scanning all players.
     */
    private List<Player> onlineFriendsOf(Player subject) {
        Optional<PlayerRef> subjRefOpt = svc.getPlayer(subject.getUniqueId().toString());
        if (subjRefOpt.isEmpty()) return Collections.emptyList();

        List<FriendSnapshot> snaps = svc.acceptedFriendSnapshots(subjRefOpt.get());
        if (snaps.isEmpty()) return Collections.emptyList();

        List<UUID> friendIds = FriendActivityPolicy.parseFriendUuids(snaps);
        if (friendIds.isEmpty()) return Collections.emptyList();

        List<Player> result = new ArrayList<>(friendIds.size());
        for (UUID friendId : friendIds) {
            ProxyFeatures.getProxyInstance().getPlayer(friendId).ifPresent(result::add);
        }
        return result;
    }

    private boolean isVanished(Player pl) {
        return vanishApi.map(api -> api.isVanished(pl.getUniqueId())).orElse(false);
    }
}
