package nl.hauntedmc.proxyfeatures.features.friends.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.proxyfeatures.common.util.APIRegistry;
import nl.hauntedmc.proxyfeatures.features.friends.Friends;
import nl.hauntedmc.proxyfeatures.features.friends.entity.FriendSnapshot;
import nl.hauntedmc.proxyfeatures.features.friends.entity.FriendsService;
import nl.hauntedmc.proxyfeatures.features.vanish.internal.VanishAPI;

import java.util.*;
import java.util.stream.Collectors;

public final class FriendActivityListener {

    private final Friends feature;
    private final FriendsService svc;

    public FriendActivityListener(Friends feature) {
        this.feature = feature;
        this.svc = feature.getService();
    }

    /**
     * Handlet zowel eerste join (online notify) als server switch (switch notify).
     * Eerste join herken je aan de afwezigheid van een previous server.
     */
    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        Player subject = event.getPlayer();

        // Nooit de aanwezigheid/switches van een vanished subject lekken
        if (isVanished(subject)) return;

        String to = event.getServer().getServerInfo().getName();
        Optional<RegisteredServer> prev = event.getPreviousServer();

        if (prev.isPresent()) {
            String from = prev.get().getServerInfo().getName();
            if (!from.equalsIgnoreCase(to)) {
                notifyFriendsSwitch(subject, from, to);
            }
        } else {
            // Eerste succesvolle connect na login → “nu online”
            notifyFriendsOnline(subject, to);
        }
    }

    /**
     * Meld vrienden wanneer de speler de proxy verlaat.
     */
    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player subject = event.getPlayer();
        if (isVanished(subject)) return;
        notifyFriendsOffline(subject);
    }

    // ---------------------
    // Notification helpers
    // ---------------------

    private void notifyFriendsOnline(Player subject, String serverName) {
        for (Player friend : onlineFriendsOf(subject)) {
            friend.sendMessage(feature.getLocalizationHandler()
                    .getMessage("friend.notify.online")
                    .withPlaceholders(Map.of(
                            "player", subject.getUsername(),
                            "server", serverName))
                    .forAudience(friend)
                    .build());
        }
    }

    private void notifyFriendsOffline(Player subject) {
        for (Player friend : onlineFriendsOf(subject)) {
            friend.sendMessage(feature.getLocalizationHandler()
                    .getMessage("friend.notify.offline")
                    .withPlaceholders(Map.of(
                            "player", subject.getUsername()))
                    .forAudience(friend)
                    .build());
        }
    }

    private void notifyFriendsSwitch(Player subject, String from, String to) {
        for (Player friend : onlineFriendsOf(subject)) {
            friend.sendMessage(feature.getLocalizationHandler()
                    .getMessage("friend.notify.switch")
                    .withPlaceholders(Map.of(
                            "player", subject.getUsername(),
                            "from", from,
                            "to", to))
                    .forAudience(friend)
                    .build());
        }
    }

    /**
     * Geeft ALLE online vrienden terug (inclusief vanished ontvangers!),
     * zodat vanished spelers nog steeds meldingen over hun vrienden krijgen.
     * Gebruikt snapshots om lazy loads buiten een tx te vermijden.
     */
    private List<Player> onlineFriendsOf(Player subject) {
        Optional<PlayerEntity> subjEntityOpt = svc.getPlayer(subject.getUniqueId().toString());
        if (subjEntityOpt.isEmpty()) return Collections.emptyList();

        List<FriendSnapshot> snaps = svc.acceptedFriendSnapshots(subjEntityOpt.get());
        if (snaps.isEmpty()) return Collections.emptyList();

        Set<UUID> friendUUIDs = snaps.stream()
                .map(s -> java.util.UUID.fromString(s.uuid()))
                .collect(Collectors.toSet());

        return feature.getPlugin().getProxy().getAllPlayers().stream()
                .filter(pl -> friendUUIDs.contains(pl.getUniqueId()))
                // Belangrijk: géén filter op !isVanished(pl)
                .toList();
    }

    private boolean isVanished(Player pl) {
        return APIRegistry.get(VanishAPI.class)
                .map(api -> api.isVanished(pl.getUniqueId()))
                .orElse(false);
    }
}
