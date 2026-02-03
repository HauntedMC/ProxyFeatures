package nl.hauntedmc.proxyfeatures.features.clientinfo.internal;

import com.velocitypowered.api.proxy.player.PlayerSettings;
import nl.hauntedmc.proxyfeatures.api.io.config.ConfigNode;
import nl.hauntedmc.proxyfeatures.api.io.config.ConfigView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public record ClientInfoConfig(
        boolean notifyEnabled,
        long notifyDebounceMillis,
        long notifyCooldownMillis,
        boolean notifyOnlyIfChanged,
        boolean notifyOnlyOncePerSession,

        // global checks
        boolean checkViewDistance,
        boolean checkChatMode,
        boolean checkParticles,

        // global recommend values
        int recommendViewDistanceMin,
        PlayerSettings.ChatMode recommendChatMode,
        PlayerSettings.ParticleStatus recommendParticles,

        // optional profiles
        List<Profile> profiles
) {

    public static ClientInfoConfig load(ConfigView cfg) {
        boolean notifyEnabled = cfg.get("notify.enabled", Boolean.class, true);
        long debounce = cfg.get("notify.debounce_millis", Long.class, 5000L);
        long cooldown = cfg.get("notify.cooldown_millis", Long.class, 30L * 60L * 1000L);
        boolean onlyChanged = cfg.get("notify.only_send_if_changed", Boolean.class, true);
        boolean onceSession = cfg.get("notify.only_once_per_session", Boolean.class, false);

        boolean checkView = cfg.get("checks.view_distance.enabled", Boolean.class, true);
        boolean checkChat = cfg.get("checks.chat_mode.enabled", Boolean.class, true);
        boolean checkParticles = cfg.get("checks.particles.enabled", Boolean.class, true);

        int viewMin = cfg.get("recommend.view_distance_min", Integer.class, 5);
        PlayerSettings.ChatMode chatMode = parseChatMode(cfg.get("recommend.chat_mode", String.class, "SHOWN"));
        PlayerSettings.ParticleStatus particles = parseParticles(cfg.get("recommend.particles", String.class, "ALL"));

        List<Profile> profiles = parseProfiles(cfg.node("profiles"));

        return new ClientInfoConfig(
                notifyEnabled, debounce, cooldown, onlyChanged, onceSession,
                checkView, checkChat, checkParticles,
                viewMin, chatMode, particles,
                profiles
        );
    }

    public ClientInfoAdvisor.EffectiveConfig effectiveForServer(String serverName) {
        Profile p = resolveProfile(serverName);

        boolean checkView = checkViewDistance;
        boolean checkChat = checkChatMode;
        boolean checkPart = checkParticles;

        int viewMin = recommendViewDistanceMin;
        PlayerSettings.ChatMode chatMode = recommendChatMode;
        PlayerSettings.ParticleStatus particles = recommendParticles;

        if (p != null) {
            if (p.checks.viewDistance != null) checkView = p.checks.viewDistance;
            if (p.checks.chatMode != null) checkChat = p.checks.chatMode;
            if (p.checks.particles != null) checkPart = p.checks.particles;

            if (p.recommend.viewDistanceMin != null) viewMin = p.recommend.viewDistanceMin;
            if (p.recommend.chatMode != null) chatMode = p.recommend.chatMode;
            if (p.recommend.particles != null) particles = p.recommend.particles;
        }

        return new ClientInfoAdvisor.EffectiveConfig(
                checkView,
                checkChat,
                checkPart,
                viewMin,
                chatMode,
                particles
        );
    }

    private Profile resolveProfile(String serverName) {
        if (serverName == null || serverName.isBlank()) return null;
        String s = serverName.toLowerCase(Locale.ROOT);

        for (Profile p : profiles) {
            for (String serv : p.servers) {
                if (serv != null && serv.toLowerCase(Locale.ROOT).equals(s)) return p;
            }
        }
        return null;
    }

    /* ========================= parsing ========================= */

    private static List<Profile> parseProfiles(ConfigNode profilesNode) {
        if (profilesNode == null || profilesNode.isNull()) return List.of();

        List<Profile> out = new ArrayList<>();
        for (String profileName : profilesNode.keys()) {
            ConfigNode p = profilesNode.get(profileName);

            List<String> servers = p.get("servers").listOf(String.class);
            if (servers.isEmpty() && profileName != null && !profileName.isBlank()) {
                servers = List.of(profileName);
            }

            ConfigNode checks = p.get("checks");
            ProfileChecks pc = new ProfileChecks(
                    nullableBool(checks.get("view_distance").raw()),
                    nullableBool(checks.get("chat_mode").raw()),
                    nullableBool(checks.get("particles").raw())
            );

            ConfigNode rec = p.get("recommend");
            ProfileRecommend pr = new ProfileRecommend(
                    nullableInt(rec.get("view_distance_min").raw()),
                    nullableChatMode(rec.get("chat_mode").raw()),
                    nullableParticles(rec.get("particles").raw())
            );

            out.add(new Profile(profileName, List.copyOf(servers), pc, pr));
        }
        return List.copyOf(out);
    }

    private static PlayerSettings.ChatMode parseChatMode(String s) {
        if (s == null) return PlayerSettings.ChatMode.SHOWN;
        try {
            return PlayerSettings.ChatMode.valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return PlayerSettings.ChatMode.SHOWN;
        }
    }

    private static PlayerSettings.ParticleStatus parseParticles(String s) {
        if (s == null) return PlayerSettings.ParticleStatus.ALL;
        try {
            return PlayerSettings.ParticleStatus.valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return PlayerSettings.ParticleStatus.ALL;
        }
    }

    private static Boolean nullableBool(Object o) {
        return (o instanceof Boolean b) ? b : null;
    }

    private static Integer nullableInt(Object o) {
        if (o instanceof Number n) return n.intValue();
        return null;
    }

    private static PlayerSettings.ChatMode nullableChatMode(Object o) {
        if (!(o instanceof String s)) return null;
        return parseChatMode(s);
    }

    private static PlayerSettings.ParticleStatus nullableParticles(Object o) {
        if (!(o instanceof String s)) return null;
        return parseParticles(s);
    }

    /* ========================= profile records ========================= */

    public record Profile(String name, List<String> servers, ProfileChecks checks, ProfileRecommend recommend) {
    }

    public record ProfileChecks(
            Boolean viewDistance,
            Boolean chatMode,
            Boolean particles
    ) {
    }

    public record ProfileRecommend(
            Integer viewDistanceMin,
            PlayerSettings.ChatMode chatMode,
            PlayerSettings.ParticleStatus particles
    ) {
    }
}
