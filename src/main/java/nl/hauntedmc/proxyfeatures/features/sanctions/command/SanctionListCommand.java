package nl.hauntedmc.proxyfeatures.features.sanctions.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import nl.hauntedmc.proxyfeatures.commands.FeatureCommand;
import nl.hauntedmc.proxyfeatures.features.sanctions.Sanctions;
import nl.hauntedmc.proxyfeatures.features.sanctions.entity.SanctionEntity;
import nl.hauntedmc.proxyfeatures.features.sanctions.entity.SanctionType;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SanctionListCommand extends FeatureCommand {

    private final Sanctions feature;

    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    public SanctionListCommand(Sanctions feature) { this.feature = feature; }

    @Override
    public void execute(Invocation inv) {
        CommandSource src = inv.source();
        String[] a = inv.arguments();

        if (a.length != 2) {
            sendMsg(src, "sanctions.usage.sanctionlist");
            return;
        }

        String targetName = a[0];
        String modeToken  = a[1].toLowerCase(Locale.ROOT);

        boolean activeOnly;
        if (modeToken.equals("active")) activeOnly = true;
        else if (modeToken.equals("all")) activeOnly = false;
        else {
            sendMsg(src, "sanctions.usage.sanctionlist");
            return;
        }

        var targetOpt = feature.getServiceLookup().byName(targetName);
        if (targetOpt.isEmpty()) { sendMsg(src, "sanctions.not_found"); return; }
        PlayerEntity target = targetOpt.get();

        List<SanctionEntity> list = feature.getService().listSanctionsForPlayer(target, activeOnly);

        String modeLabel = activeOnly ? "actief" : "alles";
        if (list.isEmpty()) {
            sendMsg(src, "sanctions.list.empty",
                    Map.of("player", target.getUsername(), "mode", modeLabel));
            return;
        }

        // header
        Map<String,String> headerPh = new HashMap<>();
        headerPh.put("player", target.getUsername());
        headerPh.put("mode", modeLabel);
        headerPh.put("count", String.valueOf(list.size()));
        sendMsg(src, "sanctions.list.header", headerPh);

        sendMsg(src, "sanctions.list.entry.separator");

        // entries
        for (SanctionEntity s : list) {
            Map<String,String> ph = new HashMap<>();
            ph.put("id", String.valueOf(s.getId()));
            ph.put("type", typeLabel(s.getType()));
            ph.put("status", s.isActive() ? "&aActief" : "&cInactief");

            // Prefer stored actorName fallback when actor entity missing
            String actor =
                    feature.getService().usernameOf(s.getActorPlayer())
                            .orElse(s.getActorName() == null ? "CONSOLE" : s.getActorName());
            ph.put("actor", actor);

            ph.put("created", fmt(s.getCreatedAt()));

            // Correct total duration = from createdAt to expiresAt (not now)
            String totalDuration = s.isPermanent()
                    ? "permanent"
                    : feature.getService().humanDuration(
                    Optional.ofNullable(s.getCreatedAt()).orElse(Instant.now()),
                    s.getExpiresAt());

            // If still active and temporary, also show remaining time
            String durationDisplay = totalDuration;
            if (s.isActive() && !s.isPermanent() && s.getExpiresAt() != null) {
                String remaining = feature.getService().humanDuration(Instant.now(), s.getExpiresAt());
                durationDisplay = totalDuration + " (resterend: " + remaining + ")";
            }
            ph.put("duration", durationDisplay);

            ph.put("reason", (s.getReason() == null || s.getReason().isBlank()) ? "-" : s.getReason());

            // Line 1
            sendMsg(src, "sanctions.list.entry.line1", ph);
            // Line 2
            sendMsg(src, "sanctions.list.entry.line2", ph);
            // Optional expiry line
            if (!s.isPermanent() && s.getExpiresAt() != null) {
                sendMsg(src, "sanctions.list.entry.line2b",
                        Map.of("expires", fmt(s.getExpiresAt())));
            }
            // Line 3
            sendMsg(src, "sanctions.list.entry.line3", ph);

            sendMsg(src, "sanctions.list.entry.separator");
        }
    }

    @Override
    public boolean hasPermission(Invocation inv) {
        return inv.source().hasPermission("proxyfeatures.feature.sanctions.command.sanctionlist");
    }

    @Override public String getName() { return "sanctionlist"; }

    @Override public String[] getAliases() { return new String[0]; }

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        String[] a = invocation.arguments();
        if (a.length == 0 || (a.length == 1 && a[0].isEmpty())) {
            // Suggest online names
            List<String> names = feature.getPlugin().getProxy().getAllPlayers().stream()
                    .map(Player::getUsername)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
            return CompletableFuture.completedFuture(names);
        }
        if (a.length == 1) {
            String partial = a[0].toLowerCase(Locale.ROOT);
            List<String> names = feature.getPlugin().getProxy().getAllPlayers().stream()
                    .map(Player::getUsername)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(partial))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
            return CompletableFuture.completedFuture(names);
        }
        if (a.length == 2) {
            String partial = a[1].toLowerCase(Locale.ROOT);
            List<String> modes = Stream.of("active", "all")
                    .filter(m -> m.startsWith(partial))
                    .collect(Collectors.toList());
            return CompletableFuture.completedFuture(modes);
        }
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    // helpers
    private void sendMsg(CommandSource src, String key) {
        src.sendMessage(feature.getLocalizationHandler().getMessage(key).forAudience(src).build());
    }
    private void sendMsg(CommandSource src, String key, Map<String, String> ph) {
        src.sendMessage(feature.getLocalizationHandler().getMessage(key).withPlaceholders(ph).forAudience(src).build());
    }

    private String fmt(Instant t) {
        return t == null ? "-" : TS.format(t);
    }

    private String typeLabel(SanctionType t) {
        return switch (t) {
            case BAN    -> "Ban";
            case BAN_IP -> "IP-Ban";
            case MUTE   -> "Mute";
            case WARN   -> "Warn";
            case KICK   -> "Kick";
        };
    }
}
