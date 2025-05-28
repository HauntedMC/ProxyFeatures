package nl.hauntedmc.proxyfeatures.features.messager.internal;

import com.velocitypowered.api.proxy.Player;
import nl.hauntedmc.proxyfeatures.features.messager.Messenger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MessagingHandler {
    private final Messenger feature;

    // who each player has blocked
    private final Map<UUID, Set<UUID>> blockMap = new ConcurrentHashMap<>();
    // players who have toggled messaging OFF
    private final Set<UUID> toggledOff = Collections.newSetFromMap(new ConcurrentHashMap<>());
    // players in spy-mode
    private final Set<UUID> spies = Collections.newSetFromMap(new ConcurrentHashMap<>());
    // for /msg reply
    private final Map<UUID, UUID> lastMessageFrom = new ConcurrentHashMap<>();

    public MessagingHandler(Messenger feature) {
        this.feature = feature;
    }


    public void processPrivateMessage(Player sender, Player receiver, String msg) {
        var loc = feature.getLocalizationHandler();
        // self-check
        if (sender.getUniqueId().equals(receiver.getUniqueId())) {
            sender.sendMessage(loc.getMessage("message.self").forAudience(sender).build());
            return;
        }
        // toggles
        if (!isMessagingEnabled(sender.getUniqueId())) {
            sender.sendMessage(loc.getMessage("message.disabled.sender").forAudience(sender).build());
            return;
        }
        if (!isMessagingEnabled(receiver.getUniqueId())) {
            sender.sendMessage(loc.getMessage("message.disabled.receiver").forAudience(sender).build());
            return;
        }
        // blocks
        if (isBlocked(sender.getUniqueId(), receiver.getUniqueId())) {
            sender.sendMessage(loc.getMessage("message.blocked").forAudience(sender).build());
            return;
        }
        sendPrivateMessage(sender, receiver, msg);
    }

    /**
     * Send the two PM messages + broadcast to any spies.
     */
    private void sendPrivateMessage(Player sender, Player receiver, String msg) {
        // record last-sender for reply
        lastMessageFrom.put(receiver.getUniqueId(), sender.getUniqueId());
        lastMessageFrom.put(sender.getUniqueId(), receiver.getUniqueId());

        var loc = feature.getLocalizationHandler();
        var fromCmp = loc.getMessage("message.format.from")
                .withPlaceholders(Map.of(
                        "sender_server", sender.getCurrentServer()
                                .map(s -> s.getServerInfo().getName())
                                .orElse("unknown"),
                        "sender", sender.getUsername(),
                        "message", msg
                )).build();
        var toCmp = loc.getMessage("message.format.to")
                .withPlaceholders(Map.of(
                        "receiver_server", receiver.getCurrentServer()
                                .map(s -> s.getServerInfo().getName())
                                .orElse("unknown"),
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

        // spy observers
        spies.stream()
                .filter(id -> !id.equals(sender.getUniqueId()) && !id.equals(receiver.getUniqueId()))
                .map(feature.getPlugin().getProxy()::getPlayer)
                .flatMap(Optional::stream)
                .forEach(spy -> spy.sendMessage(spyCmp));
    }

    /**
     * True if sender↔receiver are mutually blocked or either has blocked the other.
     */
    public boolean isBlocked(UUID sender, UUID receiver) {
        return blockMap.getOrDefault(sender, Set.of()).contains(receiver)
                || blockMap.getOrDefault(receiver, Set.of()).contains(sender);
    }

    public boolean isBlocking(UUID who, UUID target) {
        return blockMap.getOrDefault(who, Collections.emptySet()).contains(target);
    }

    public void block(UUID who, UUID target) {
        blockMap.computeIfAbsent(who, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
                .add(target);
    }

    public void unblock(UUID who, UUID target) {
        blockMap.getOrDefault(who, Collections.emptySet()).remove(target);
    }

    public void toggleMessaging(UUID who) {
        if (!toggledOff.remove(who)) {
            toggledOff.add(who);
        }
    }

    public boolean isMessagingEnabled(UUID who) {
        return !toggledOff.contains(who);
    }

    public void toggleSpy(UUID who) {
        if (!spies.remove(who)) {
            spies.add(who);
        }
    }

    public boolean isSpy(UUID who) {
        return spies.contains(who);
    }

    public Optional<UUID> getLastRecipient(UUID who) {
        return Optional.ofNullable(lastMessageFrom.get(who));
    }

    /**
     * Clear all in-memory state on plugin disable.
     */
    public void cleanupAll() {
        spies.clear();
        toggledOff.clear();
        lastMessageFrom.clear();
        blockMap.clear();
    }
}
