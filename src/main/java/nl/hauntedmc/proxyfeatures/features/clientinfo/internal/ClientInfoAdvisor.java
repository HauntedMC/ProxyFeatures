package nl.hauntedmc.proxyfeatures.features.clientinfo.internal;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.player.PlayerSettings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.features.clientinfo.ClientInfo;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientInfoAdvisor {

    private static final String FP_NONE = "<none>";

    private final ClientInfo feature;
    private final ClientInfoSettingsService settingsService;

    private final ConcurrentHashMap<UUID, Boolean> notifyEnabledCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> lastNotifyAtMillis = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> lastFingerprint = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> sentThisSession = new ConcurrentHashMap<>();

    private volatile ClientInfoConfig config;

    public ClientInfoAdvisor(ClientInfo feature, ClientInfoSettingsService settingsService) {
        this.feature = feature;
        this.settingsService = settingsService;
        this.config = ClientInfoConfig.load(feature.getConfigHandler());

        ProxyFeatures.getProxyInstance().getAllPlayers().forEach(this::loadPlayerSettings);
    }

    public ClientInfoConfig config() {
        return config;
    }

    public void reloadConfig() {
        this.config = ClientInfoConfig.load(feature.getConfigHandler());
    }

    public void shutdown() {
        notifyEnabledCache.clear();
        lastNotifyAtMillis.clear();
        lastFingerprint.clear();
        sentThisSession.clear();
    }

    public void onDisconnect(UUID uuid) {
        sentThisSession.remove(uuid);
        lastNotifyAtMillis.remove(uuid);
        lastFingerprint.remove(uuid);
        notifyEnabledCache.remove(uuid);
    }

    /**
     * Ensures the DB-backed toggle is loaded once per session.
     *
     * @return true if loaded or already cached; false if loading failed (fail-closed: no notifications).
     */
    public boolean ensurePlayerSettingsLoaded(Player player) {
        UUID uuid = player.getUniqueId();
        if (notifyEnabledCache.containsKey(uuid)) return true;
        return loadPlayerSettings(player);
    }

    /**
     * Loads settings (once) and stores them in-memory for the session.
     *
     * @return true when cache has a value afterwards; false on failure.
     */
    public boolean loadPlayerSettings(Player player) {
        UUID uuid = player.getUniqueId();
        if (notifyEnabledCache.containsKey(uuid)) return true;

        try {
            boolean enabled = settingsService.isNotificationsEnabled(uuid, player.getUsername());
            notifyEnabledCache.put(uuid, enabled);
            return true;
        } catch (Exception e) {
            feature.getLogger().warn("Failed to load ClientInfo settings for " + player.getUsername()
                    + " (" + e.getClass().getSimpleName() + "): " + e.getMessage());
            feature.getLogger().debug(stackTrace(e));
            return false;
        }
    }

    public void toggleNotifications(Player player) {
        UUID uuid = player.getUniqueId();

        boolean nowEnabled = !notifyEnabledCache.getOrDefault(uuid, true);
        notifyEnabledCache.put(uuid, nowEnabled);

        // Persist (DB-backed), but the in-memory value is authoritative during this session.
        settingsService.setNotificationsEnabled(uuid, player.getUsername(), nowEnabled);

        String key = nowEnabled ? "clientinfo.toggle.enabled" : "clientinfo.toggle.disabled";
        player.sendMessage(feature.getLocalizationHandler().getMessage(key).forAudience(player).build());
    }

    public void maybeNotify(UUID uuid) {
        if (!config.notifyEnabled()) return;

        Optional<Player> opt = ProxyFeatures.getProxyInstance().getPlayer(uuid);
        if (opt.isEmpty()) return;

        Player player = opt.get();

        // Respect toggle always: if we cannot determine the value, do not notify.
        if (!ensurePlayerSettingsLoaded(player)) return;

        boolean enabled = notifyEnabledCache.getOrDefault(uuid, true);
        if (!enabled) return;

        if (config.notifyOnlyOncePerSession() && sentThisSession.containsKey(uuid)) {
            return;
        }

        List<Recommendation> recs = evaluate(player);

        // Track "no recommendations" state, so re-triggering the same set later counts as a change.
        if (recs.isEmpty()) {
            lastFingerprint.put(uuid, FP_NONE);
            return;
        }

        String fp = fingerprint(recs);
        String prev = lastFingerprint.put(uuid, fp);
        boolean changed = prev == null || !prev.equals(fp);

        if (config.notifyOnlyIfChanged() && !changed) {
            return;
        }

        long now = System.currentTimeMillis();
        long cooldown = Math.max(0L, config.notifyCooldownMillis());
        long last = lastNotifyAtMillis.getOrDefault(uuid, 0L);

        // If recommendations changed (e.g. due to player adjusting settings),
        // notify immediately even if cooldown has not passed.
        if (!changed && cooldown > 0 && now - last < cooldown) {
            return;
        }

        lastNotifyAtMillis.put(uuid, now);
        player.sendMessage(buildPushRecommendations(player, recs));

        if (config.notifyOnlyOncePerSession()) {
            sentThisSession.put(uuid, Boolean.TRUE);
        }
    }

    public Component buildFullView(CommandSource viewer, Player target) {
        List<Component> lines = new ArrayList<>();

        lines.add(feature.getLocalizationHandler()
                .getMessage("clientinfo.cmd_header")
                .with("player", target.getUsername())
                .forAudience(viewer)
                .build());

        lines.add(feature.getLocalizationHandler()
                .getMessage("clientinfo.cmd_section.settings")
                .forAudience(viewer)
                .build());

        lines.addAll(settingsEntries(target, viewer));

        lines.add(feature.getLocalizationHandler()
                .getMessage("clientinfo.cmd_section.recommendations")
                .forAudience(viewer)
                .build());

        List<Recommendation> recs = evaluate(target);
        if (recs.isEmpty()) {
            lines.add(feature.getLocalizationHandler()
                    .getMessage("clientinfo.no_recommendations")
                    .forAudience(viewer)
                    .build());
        } else {
            lines.addAll(formatRecommendationLines(viewer, recs, true));
            lines.add(feature.getLocalizationHandler()
                    .getMessage("clientinfo.footer_help_hint")
                    .forAudience(viewer)
                    .build());
        }

        return Component.join(JoinConfiguration.newlines(), lines);
    }

    public Component buildRecommendationsOnly(CommandSource viewer, Player target) {
        List<Recommendation> recs = evaluate(target);
        if (recs.isEmpty()) {
            return feature.getLocalizationHandler()
                    .getMessage("clientinfo.no_recommendations")
                    .forAudience(viewer)
                    .build();
        }

        List<Component> lines = new ArrayList<>();
        lines.add(feature.getLocalizationHandler()
                .getMessage("clientinfo.recommend_block_header")
                .forAudience(viewer)
                .build());

        lines.addAll(formatRecommendationLines(viewer, recs, false));

        lines.add(feature.getLocalizationHandler()
                .getMessage("clientinfo.footer_help_hint")
                .forAudience(viewer)
                .build());

        return Component.join(JoinConfiguration.newlines(), lines);
    }

    public Component buildHelp(CommandSource viewer) {
        List<Component> lines = List.of(
                feature.getLocalizationHandler().getMessage("clientinfo.help.header").forAudience(viewer).build(),
                feature.getLocalizationHandler().getMessage("clientinfo.help.render_distance").forAudience(viewer).build(),
                feature.getLocalizationHandler().getMessage("clientinfo.help.chat_mode").forAudience(viewer).build(),
                feature.getLocalizationHandler().getMessage("clientinfo.help.particles").forAudience(viewer).build(),
                feature.getLocalizationHandler().getMessage("clientinfo.help.footer").forAudience(viewer).build()
        );
        return Component.join(JoinConfiguration.newlines(), lines);
    }

    public int sendFullViewOther(CommandSource src, String targetName) {
        var opt = ProxyFeatures.getProxyInstance().getPlayer(targetName);
        if (opt.isEmpty()) {
            src.sendMessage(feature.getLocalizationHandler()
                    .getMessage("clientinfo.cmd_playerNotFound")
                    .with("player", targetName)
                    .forAudience(src)
                    .build());
            return 0;
        }
        src.sendMessage(buildFullView(src, opt.get()));
        return 1;
    }

    public int sendRecommendationsOnlyOther(CommandSource src, String targetName) {
        var opt = ProxyFeatures.getProxyInstance().getPlayer(targetName);
        if (opt.isEmpty()) {
            src.sendMessage(feature.getLocalizationHandler()
                    .getMessage("clientinfo.cmd_playerNotFound")
                    .with("player", targetName)
                    .forAudience(src)
                    .build());
            return 0;
        }
        src.sendMessage(buildRecommendationsOnly(src, opt.get()));
        return 1;
    }

    /* ============================== Evaluation ============================== */

    public List<Recommendation> evaluate(Player player) {
        PlayerSettings s = player.getPlayerSettings();
        EffectiveConfig eff = config.effectiveForServer(currentServerName(player));

        List<Recommendation> out = new ArrayList<>();

        if (eff.checkViewDistance() && s.getViewDistance() < eff.recommendViewDistanceMin()) {
            out.add(rec(player, "clientinfo.setting.view_distance",
                    String.valueOf(s.getViewDistance()),
                    String.valueOf(eff.recommendViewDistanceMin())));
        }

        if (eff.checkChatMode() && s.getChatMode() != eff.recommendChatMode()) {
            out.add(rec(player, "clientinfo.setting.chat_mode",
                    s.getChatMode().name(),
                    eff.recommendChatMode().name()));
        }

        if (eff.checkParticles() && s.getParticleStatus() != eff.recommendParticles()) {
            out.add(rec(player, "clientinfo.setting.particles",
                    s.getParticleStatus().name(),
                    eff.recommendParticles().name()));
        }

        out.sort(Comparator.comparing(Recommendation::id, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    private Recommendation rec(CommandSource viewerForLang, String settingKey, String found, String recommended) {
        Component label = feature.getLocalizationHandler()
                .getMessage(settingKey)
                .forAudience(viewerForLang)
                .build();

        return new Recommendation(settingKey, label, found, recommended);
    }

    private List<Component> formatRecommendationLines(CommandSource viewerForLang, List<Recommendation> recs, boolean includeHeader) {
        List<Component> lines = new ArrayList<>();
        if (includeHeader) {
            lines.add(feature.getLocalizationHandler()
                    .getMessage("clientinfo.header")
                    .forAudience(viewerForLang)
                    .build());
        }

        Component helpBadge = Component.text(" ")
                .append(Component.text("[help]", NamedTextColor.AQUA))
                .clickEvent(ClickEvent.runCommand("/clientinfo help"))
                .hoverEvent(HoverEvent.showText(Component.text("Klik voor uitleg", NamedTextColor.YELLOW)));

        for (Recommendation r : recs) {
            Component line = feature.getLocalizationHandler()
                    .getMessage("clientinfo.recommendation")
                    .with("setting_name", r.settingName())
                    .with("setting_found", r.found())
                    .with("setting_recommended", r.recommended())
                    .with("help", helpBadge)
                    .forAudience(viewerForLang)
                    .build();
            lines.add(line);
        }
        return lines;
    }

    private Component buildPushRecommendations(Player player, List<Recommendation> recs) {
        List<Component> lines = new ArrayList<>();

        lines.add(feature.getLocalizationHandler()
                .getMessage("clientinfo.recommend_block_header")
                .forAudience(player)
                .build());

        lines.addAll(formatRecommendationLines(player, recs, false));

        lines.add(feature.getLocalizationHandler()
                .getMessage("clientinfo.footer_help_hint")
                .forAudience(player)
                .build());

        return Component.join(JoinConfiguration.newlines(), lines);
    }

    private List<Component> settingsEntries(Player target, CommandSource viewerForLang) {
        PlayerSettings s = target.getPlayerSettings();
        ProtocolVersion proto = target.getProtocolVersion();

        String clientVersion = (proto == null)
                ? "Unknown"
                : proto + " (protocol " + proto.getProtocol() + ")";

        String locale = (s.getLocale() == null) ? "unknown" : s.getLocale().toLanguageTag();

        List<Map.Entry<String, String>> pairs = List.of(
                Map.entry("Render Distance", String.valueOf(s.getViewDistance())),
                Map.entry("Chat Mode", s.getChatMode().name()),
                Map.entry("Particles", s.getParticleStatus().name()),
                Map.entry("Language", locale),
                Map.entry("Client Version", clientVersion)
        );

        List<Component> out = new ArrayList<>(pairs.size());
        for (var p : pairs) {
            out.add(feature.getLocalizationHandler()
                    .getMessage("clientinfo.cmd_entry")
                    .with("setting", p.getKey())
                    .with("value", p.getValue())
                    .forAudience(viewerForLang)
                    .build());
        }
        return out;
    }

    private String currentServerName(Player p) {
        return p.getCurrentServer()
                .map(c -> c.getServerInfo().getName())
                .orElse("");
    }

    private static String fingerprint(List<Recommendation> recs) {
        if (recs.isEmpty()) return FP_NONE;

        List<Recommendation> sorted = new ArrayList<>(recs);
        sorted.sort(Comparator.comparing(Recommendation::id, String.CASE_INSENSITIVE_ORDER));

        StringBuilder sb = new StringBuilder(sorted.size() * 32);
        for (int i = 0; i < sorted.size(); i++) {
            Recommendation r = sorted.get(i);
            if (i > 0) sb.append('|');
            sb.append(r.id()).append('=').append(r.found()).append("->").append(r.recommended());
        }
        return sb.toString();
    }

    private static String stackTrace(Throwable t) {
        StringWriter sw = new StringWriter(1024);
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }

    /* ============================== Models ============================== */

    public record Recommendation(String id, Component settingName, String found, String recommended) {
    }

    public record EffectiveConfig(
            boolean checkViewDistance,
            boolean checkChatMode,
            boolean checkParticles,
            int recommendViewDistanceMin,
            PlayerSettings.ChatMode recommendChatMode,
            PlayerSettings.ParticleStatus recommendParticles
    ) {
    }
}
