package nl.hauntedmc.proxyfeatures.features.sanctions.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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

        int pageSize = getPageSize();

        // Patterns supported:
        //  - /sanctionlist                      -> recent global, page 1
        //  - /sanctionlist <page>               -> recent global, specific page
        //  - /sanctionlist <player> [page]      -> player's all (default), paged
        //  - /sanctionlist <player> all|active [page] -> player's filtered, paged

        if (a.length == 0) {
            showRecent(src, 1, pageSize);
            return;
        }

        if (a.length == 1) {
            Optional<Integer> asInt = parsePositiveInt(a[0]);
            if (asInt.isPresent()) {
                showRecent(src, asInt.get(), pageSize);
                return;
            }
            // Treat as player, default mode=all, page=1
            showPlayerList(src, a[0], false, 1, pageSize);
            return;
        }

        // a.length >= 2
        String playerToken = a[0];
        Optional<Integer> maybePageSecond = parsePositiveInt(a[1]);

        if (maybePageSecond.isPresent()) {
            // /sanctionlist <player> <page>
            showPlayerList(src, playerToken, false, maybePageSecond.get(), pageSize);
            return;
        }

        String modeToken = a[1].toLowerCase(Locale.ROOT);
        if (!modeToken.equals("all") && !modeToken.equals("active")) {
            sendMsg(src, "sanctions.usage.sanctionlist");
            return;
        }
        boolean activeOnly = modeToken.equals("active");

        int page = 1;
        if (a.length >= 3) {
            Optional<Integer> maybePageThird = parsePositiveInt(a[2]);
            if (maybePageThird.isEmpty()) {
                sendMsg(src, "sanctions.usage.sanctionlist");
                return;
            }
            page = maybePageThird.get();
        }

        showPlayerList(src, playerToken, activeOnly, page, pageSize);
    }

    private void showRecent(CommandSource src, int page, int pageSize) {
        long total = feature.getService().countAllSanctions();
        if (total == 0) {
            sendMsg(src, "sanctions.list.recent.empty");
            return;
        }
        int pages = (int) Math.max(1, Math.ceil(total / (double) pageSize));
        int pg = clampPage(page, pages);

        // Header
        sendMsg(src, "sanctions.list.recent.header", Map.of("count", String.valueOf(total)));
        sendMsg(src, "sanctions.list.entry.separator");

        // Page slice, newest first
        List<SanctionEntity> list = feature.getService().pageAllSanctions(pg, pageSize);
        renderEntries(src, list);

        // Footer
        sendMsg(src, "sanctions.list.page", Map.of(
                "page", String.valueOf(pg),
                "pages", String.valueOf(pages),
                "size", String.valueOf(pageSize)
        ));
    }

    private void showPlayerList(CommandSource src, String playerName, boolean activeOnly, int page, int pageSize) {
        var targetOpt = feature.getServiceLookup().byName(playerName);
        if (targetOpt.isEmpty()) { sendMsg(src, "sanctions.not_found"); return; }
        PlayerEntity target = targetOpt.get();

        long total = feature.getService().countSanctionsForPlayer(target, activeOnly);
        String modeLabel = raw(src, activeOnly ? "sanctions.mode.active" : "sanctions.mode.all");
        if (total == 0) {
            sendMsg(src, "sanctions.list.empty",
                    Map.of("player", target.getUsername(), "mode", modeLabel));
            return;
        }

        int pages = (int) Math.max(1, Math.ceil(total / (double) pageSize));
        int pg = clampPage(page, pages);

        Map<String,String> headerPh = new HashMap<>();
        headerPh.put("player", target.getUsername());
        headerPh.put("mode", modeLabel);
        headerPh.put("count", String.valueOf(total));
        sendMsg(src, "sanctions.list.header", headerPh);
        sendMsg(src, "sanctions.list.entry.separator");

        List<SanctionEntity> list = feature.getService().pageSanctionsForPlayer(target, activeOnly, pg, pageSize);
        renderEntries(src, list);

        sendMsg(src, "sanctions.list.page", Map.of(
                "page", String.valueOf(pg),
                "pages", String.valueOf(pages),
                "size", String.valueOf(pageSize)
        ));
    }

    private void renderEntries(CommandSource src, List<SanctionEntity> list) {
        for (SanctionEntity s : list) {
            Map<String,String> ph = new HashMap<>();
            ph.put("id", String.valueOf(s.getId()));
            ph.put("type", typeLabel(src, s.getType()));
            ph.put("status", raw(src, s.isActive() ? "sanctions.status.active" : "sanctions.status.inactive"));

            // Prefer stored actorName fallback when actor entity missing
            String actor =
                    feature.getService().usernameOf(s.getActorPlayer())
                            .orElse(s.getActorName() == null ? "CONSOLE" : s.getActorName());
            ph.put("actor", actor);

            ph.put("created", fmt(s.getCreatedAt()));

            String totalDuration = s.isPermanent()
                    ? raw(src, "sanctions.duration.permanent")
                    : feature.getService().humanDuration(
                    Optional.ofNullable(s.getCreatedAt()).orElse(Instant.now()),
                    s.getExpiresAt());

            String durationDisplay = totalDuration;
            if (s.isActive() && !s.isPermanent() && s.getExpiresAt() != null) {
                String remaining = feature.getService().humanDuration(Instant.now(), s.getExpiresAt());
                durationDisplay = raw(src, "sanctions.duration.remaining_fmt",
                        Map.of("total", totalDuration, "remaining", remaining));
            }
            ph.put("duration", durationDisplay);
            ph.put("reason", (s.getReason() == null || s.getReason().isBlank()) ? "-" : s.getReason());

            // Lines
            sendMsg(src, "sanctions.list.entry.line1", ph);
            sendMsg(src, "sanctions.list.entry.line2", ph);
            if (!s.isPermanent() && s.getExpiresAt() != null) {
                sendMsg(src, "sanctions.list.entry.line2b",
                        Map.of("expires", fmt(s.getExpiresAt())));
            }
            sendMsg(src, "sanctions.list.entry.line3", ph);
            sendMsg(src, "sanctions.list.entry.separator");
        }
    }

    private int getPageSize() {
        try {
            Object raw = feature.getConfigHandler().getSetting("sanctionListPageSize");
            int v;
            if (raw instanceof Number n) v = n.intValue();
            else if (raw instanceof String s) v = Integer.parseInt(s);
            else v = 5;
            return Math.max(1, v);
        } catch (Exception e) {
            return 5;
        }
    }

    private Optional<Integer> parsePositiveInt(String s) {
        try {
            int v = Integer.parseInt(s);
            return v >= 1 ? Optional.of(v) : Optional.empty();
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private int clampPage(int page, int totalPages) {
        if (totalPages <= 0) return 1;
        if (page < 1) return 1;
        return Math.min(page, totalPages);
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

        // For first arg: suggest online names or page numbers (we'll stick to names for simplicity)
        if (a.length == 0 || (a.length == 1 && a[0].isEmpty())) {
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
        // optional page; we won't suggest numbers dynamically
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    // === helpers ===
    private void sendMsg(CommandSource src, String key) {
        src.sendMessage(feature.getLocalizationHandler().getMessage(key).forAudience(src).build());
    }

    private void sendMsg(CommandSource src, String key, Map<String, String> ph) {
        src.sendMessage(feature.getLocalizationHandler().getMessage(key).withPlaceholders(ph).forAudience(src).build());
    }

    private String fmt(Instant t) { return t == null ? "-" : TS.format(t); }

    private String typeLabel(CommandSource src, SanctionType t) {
        return switch (t) {
            case BAN    -> raw(src, "sanctions.type.ban");
            case BAN_IP -> raw(src, "sanctions.type.ban_ip");
            case MUTE   -> raw(src, "sanctions.type.mute");
            case WARN   -> raw(src, "sanctions.type.warn");
            case KICK   -> raw(src, "sanctions.type.kick");
        };
    }

    /** Render a localization key to a legacy-ampersand string (so it can be injected as a placeholder). */
    private String raw(CommandSource src, String key) {
        return LegacyComponentSerializer.legacyAmpersand().serialize(
                feature.getLocalizationHandler().getMessage(key).forAudience(src).build()
        );
    }

    /** Same as {@link #raw(CommandSource, String)} but with placeholders. */
    private String raw(CommandSource src, String key, Map<String,String> ph) {
        return LegacyComponentSerializer.legacyAmpersand().serialize(
                feature.getLocalizationHandler().getMessage(key).withPlaceholders(ph).forAudience(src).build()
        );
    }
}
