package nl.hauntedmc.proxyfeatures.features.messager.internal;

import com.velocitypowered.api.proxy.Player;
import nl.hauntedmc.proxyfeatures.features.messager.Messenger;
import nl.hauntedmc.proxyfeatures.features.messager.entity.PlayerMessageSettingsEntity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class MessagingHandler {
    private final Messenger feature;
    private final MessagingSettingsService settings;
    private final Set<UUID> toggledOff = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<UUID> spies = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<UUID, Set<UUID>> blockMap = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> lastMessageFrom = new ConcurrentHashMap<>();

    public MessagingHandler(Messenger feature) {
        this.feature = feature;
        this.settings = new MessagingSettingsService(feature);

        // Preload online players
        feature.getPlugin().getProxy()
                .getAllPlayers()
                .forEach(this::loadPlayerSettings);
    }

    /**
     * Loads (and creates if needed) both
     * PlayerEntity and Settings in one atomic tx,
     * then initializes the in-memory caches.
     */
    public void loadPlayerSettings(Player player) {
        var loc = feature.getLocalizationHandler();
        UUID id = player.getUniqueId();

        try {
            PlayerMessageSettingsEntity s =
                    settings.loadSettings(id, player.getUsername());

            // messaging toggle
            if (!s.isMsgToggle()) toggledOff.add(id);
            else toggledOff.remove(id);

            // spy mode
            if (s.isMsgSpy()) spies.add(id);
            else spies.remove(id);

            // blocked players
            Set<UUID> blocked = s.getBlockedPlayers().stream()
                    .map(be -> UUID.fromString(be.getUuid()))
                    .collect(Collectors.toSet());
            blockMap.put(id, blocked);

        } catch (Exception ex) {
            feature.getPlugin().getLogger()
                    .error("Failed to load messaging settings for {}", player.getUsername(), ex);
            player.sendMessage(loc.getMessage("message.error.player_not_found")
                    .forAudience(player).build());
        }
    }

    public void processPrivateMessage(Player sender, Player receiver, String msg) {
        var loc = feature.getLocalizationHandler();
        UUID a = sender.getUniqueId(), b = receiver.getUniqueId();

        if (a.equals(b)) {
            sender.sendMessage(loc.getMessage("message.self").forAudience(sender).build());
            return;
        }
        if (toggledOff.contains(a)) {
            sender.sendMessage(loc.getMessage("message.disabled.sender").forAudience(sender).build());
            return;
        }
        if (toggledOff.contains(b)
                && !sender.hasPermission("proxyfeatures.feature.messager.command.toggle.bypass")) {
            sender.sendMessage(loc.getMessage("message.disabled.receiver").forAudience(sender).build());
            return;
        }
        if (isBlocked(a, b)) {
            sender.sendMessage(loc.getMessage("message.blocked").forAudience(sender).build());
            return;
        }
        sendPrivateMessage(sender, receiver, msg);
    }

    private void sendPrivateMessage(Player s, Player r, String msg) {
        // record reply
        lastMessageFrom.put(r.getUniqueId(), s.getUniqueId());
        lastMessageFrom.put(s.getUniqueId(), r.getUniqueId());

        var loc = feature.getLocalizationHandler();
        var from = loc.getMessage("message.format.from")
                .withPlaceholders(Map.of(
                        "sender_server", s.getCurrentServer().map(x -> x.getServerInfo().getName()).orElse("unknown"),
                        "sender", s.getUsername(),
                        "message", msg))
                .forAudience(r)
                .build();
        var to = loc.getMessage("message.format.to")
                .withPlaceholders(Map.of(
                        "receiver_server", r.getCurrentServer().map(x -> x.getServerInfo().getName()).orElse("unknown"),
                        "receiver", r.getUsername(),
                        "message", msg))
                .forAudience(s)
                .build();

        r.sendMessage(from);
        s.sendMessage(to);

        spies.stream()
                .filter(id -> !id.equals(s.getUniqueId()) && !id.equals(r.getUniqueId()))
                .map(id -> feature.getPlugin().getProxy().getPlayer(id))
                .flatMap(Optional::stream)
                .forEach(p -> p.sendMessage(loc.getMessage("message.format.spy")
                        .withPlaceholders(Map.of(
                                "sender", s.getUsername(),
                                "receiver", r.getUsername(),
                                "message", msg))
                        .forAudience(p)
                        .build()));
    }

    public Optional<UUID> getLastRecipient(UUID who) {
        return Optional.ofNullable(lastMessageFrom.get(who));
    }

    public void toggleMessaging(UUID who) {
        boolean nowOff = !toggledOff.remove(who);
        if (nowOff) toggledOff.add(who);

        settings.getPlayerEntity(who)
                .ifPresent(pe -> settings.setToggle(pe, !nowOff));
    }

    public void setSpy(UUID who, boolean enabled) {
        if (enabled) spies.add(who);
        else spies.remove(who);

        settings.getPlayerEntity(who)
                .ifPresent(pe -> settings.setSpy(pe, enabled));
    }

    public boolean isMessagingEnabled(UUID who) {
        return !toggledOff.contains(who);
    }

    public void toggleSpy(UUID who) {
        boolean wasOn = spies.remove(who);
        if (!wasOn) spies.add(who);

        settings.getPlayerEntity(who)
                .ifPresent(pe -> settings.setSpy(pe, spies.contains(who)));
    }

    public boolean isSpy(UUID who) {
        return spies.contains(who);
    }

    public boolean isBlocked(UUID a, UUID b) {
        return blockMap.getOrDefault(a, Set.of()).contains(b)
                || blockMap.getOrDefault(b, Set.of()).contains(a);
    }

    public void block(UUID who, UUID target) {
        blockMap.computeIfAbsent(who, k -> new HashSet<>()).add(target);
        settings.getPlayerEntity(who)
                .ifPresent(pe -> settings.block(pe,
                        settings.getPlayerEntity(target)
                                .orElseThrow()));
    }

    public void unblock(UUID who, UUID target) {
        blockMap.getOrDefault(who, Set.of()).remove(target);
        settings.getPlayerEntity(who)
                .ifPresent(pe -> settings.unblock(pe,
                        settings.getPlayerEntity(target)
                                .orElseThrow()));
    }

    public void cleanupAll() {
        lastMessageFrom.clear();
        toggledOff.clear();
        spies.clear();
        blockMap.clear();
    }
}
