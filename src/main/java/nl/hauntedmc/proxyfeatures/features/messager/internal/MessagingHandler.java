package nl.hauntedmc.proxyfeatures.features.messager.internal;

import com.velocitypowered.api.proxy.Player;
import nl.hauntedmc.proxyfeatures.features.messager.Messenger;
import nl.hauntedmc.proxyfeatures.features.messager.entity.PlayerMessageSettingsEntity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class MessagingHandler {
    private final Messenger feature;
    private final MessagingSettingsService settingsService;

    // In-memory caches for quick lookup after initial load
    private final Set<UUID> toggledOff = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<UUID> spies = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<UUID, Set<UUID>> blockMap = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> lastMessageFrom = new ConcurrentHashMap<>();

    public MessagingHandler(Messenger feature) {
        this.feature = feature;
        this.settingsService = new MessagingSettingsService(feature);

        // Preload settings for any players already online
        feature.getPlugin().getProxy().getAllPlayers().forEach(this::loadPlayerSettings);
    }

    /**
     * Load this player's persisted settings into the in-memory caches.
     */
    public void loadPlayerSettings(Player player) {
        var loc = feature.getLocalizationHandler();
        UUID playerId = player.getUniqueId();

        settingsService.getPlayerEntity(playerId).ifPresentOrElse(playerEnt -> {
            // Retrieve or create the settings row
            PlayerMessageSettingsEntity settings = settingsService.getOrCreateSettings(playerEnt);

            // msg_toggle (false means toggled off)
            if (!settings.isMsgToggle()) {
                toggledOff.add(playerId);
            }
            // spy mode
            if (settings.isMsgSpy()) {
                spies.add(playerId);
            }
            // blocked players
            Set<UUID> blocked = settings.getBlockedPlayers().stream()
                    .map(e -> UUID.fromString(e.getUuid()))
                    .collect(Collectors.toSet());
            blockMap.put(playerId, blocked);
        }, () -> player.sendMessage(loc.getMessage("message.error.player_not_found")
                .forAudience(player)
                .build()));
    }

    public void processPrivateMessage(Player sender, Player receiver, String msg) {
        var loc = feature.getLocalizationHandler();
        if (sender.getUniqueId().equals(receiver.getUniqueId())) {
            sender.sendMessage(loc.getMessage("message.self").forAudience(sender).build());
            return;
        }
        if (toggledOff.contains(sender.getUniqueId())) {
            sender.sendMessage(loc.getMessage("message.disabled.sender").forAudience(sender).build());
            return;
        }
        if (toggledOff.contains(receiver.getUniqueId())) {
            sender.sendMessage(loc.getMessage("message.disabled.receiver").forAudience(sender).build());
            return;
        }
        if (isBlocked(sender.getUniqueId(), receiver.getUniqueId())) {
            sender.sendMessage(loc.getMessage("message.blocked").forAudience(sender).build());
            return;
        }
        sendPrivateMessage(sender, receiver, msg);
    }

    private void sendPrivateMessage(Player sender, Player receiver, String msg) {
        lastMessageFrom.put(receiver.getUniqueId(), sender.getUniqueId());
        lastMessageFrom.put(sender.getUniqueId(), receiver.getUniqueId());

        var loc = feature.getLocalizationHandler();
        var fromCmp = loc.getMessage("message.format.from")
                .withPlaceholders(Map.of(
                        "sender_server", sender.getCurrentServer()
                                .map(s -> s.getServerInfo().getName()).orElse("unknown"),
                        "sender", sender.getUsername(),
                        "message", msg
                )).build();
        var toCmp = loc.getMessage("message.format.to")
                .withPlaceholders(Map.of(
                        "receiver_server", receiver.getCurrentServer()
                                .map(s -> s.getServerInfo().getName()).orElse("unknown"),
                        "receiver", receiver.getUsername(),
                        "message", msg
                )).build();
        var spyCmp = loc.getMessage("message.format.spy")
                .withPlaceholders(Map.of(
                        "sender", sender.getUsername(),
                        "receiver", receiver.getUsername(),
                        "message", msg
                )).build();

        receiver.sendMessage(fromCmp);
        sender.sendMessage(toCmp);
        spies.stream()
                .filter(id -> !id.equals(sender.getUniqueId()) && !id.equals(receiver.getUniqueId()))
                .map(feature.getPlugin().getProxy()::getPlayer)
                .flatMap(Optional::stream)
                .forEach(spy -> spy.sendMessage(spyCmp));
    }

    public Optional<UUID> getLastRecipient(UUID who) {
        return Optional.ofNullable(lastMessageFrom.get(who));
    }

    public void toggleMessaging(UUID who) {
        boolean nowOff = !toggledOff.remove(who);
        if (nowOff) toggledOff.add(who);
        // persist
        settingsService.getPlayerEntity(who).ifPresentOrElse(ent ->
                        settingsService.setToggle(ent, !nowOff),
                () -> {/* cannot persist; optionally log */});
    }

    public boolean isMessagingEnabled(UUID who) {
        return !toggledOff.contains(who);
    }

    public void toggleSpy(UUID who) {
        boolean wasOn = spies.remove(who);
        if (!wasOn) spies.add(who);
        // persist
        settingsService.getPlayerEntity(who).ifPresentOrElse(ent ->
                        settingsService.setSpy(ent, spies.contains(who)),
                () -> {/* cannot persist; optionally log */});
    }

    public boolean isSpy(UUID who) {
        return spies.contains(who);
    }

    public boolean isBlocked(UUID a, UUID b) {
        return blockMap.getOrDefault(a, Collections.emptySet()).contains(b)
                || blockMap.getOrDefault(b, Collections.emptySet()).contains(a);
    }

    public void block(UUID who, UUID target) {
        blockMap.computeIfAbsent(who, k -> Collections.newSetFromMap(new ConcurrentHashMap<>())).add(target);
        // persist
        settingsService.getPlayerEntity(who).ifPresentOrElse(ent ->
                        settingsService.block(ent, settingsService.getPlayerEntity(target).orElseThrow()),
                () -> {/* cannot persist; optionally log */});
    }

    public void unblock(UUID who, UUID target) {
        blockMap.getOrDefault(who, Collections.emptySet()).remove(target);
        // persist
        settingsService.getPlayerEntity(who).ifPresentOrElse(ent ->
                        settingsService.unblock(ent, settingsService.getPlayerEntity(target).orElseThrow()),
                () -> {/* cannot persist; optionally log */});
    }

    public void cleanupAll() {
        lastMessageFrom.clear();
        toggledOff.clear();
        spies.clear();
        blockMap.clear();
    }
}
