package nl.hauntedmc.proxyfeatures.features.votifier.internal;

import nl.hauntedmc.proxyfeatures.features.votifier.Votifier;

import java.time.ZoneId;

public record VotifierConfig(
        String host,
        int port,
        int readTimeoutMillis,
        int backlog,
        int maxPacketBytes,
        boolean generateKeys,
        int keyBits,
        String publicKeyFile,
        String privateKeyFile,
        String allowlistCsv,
        String denylistCsv,
        boolean redisEnabled,
        String redisChannel,
        boolean publishLegacyChannel,
        String legacyChannel,
        boolean statsEnabled,
        ZoneId statsZone,
        int resetCheckMinutes,
        int streakGapHours,
        int dumpTopN,
        String dumpFilePrefix,
        boolean logVotes,
        boolean remindEnabled,
        int remindThresholdHours,
        int remindInitialDelayMinutes,
        int remindIntervalMinutes,
        String voteUrl
) {

    public static VotifierConfig load(Votifier feature) {
        var root = feature.getConfigHandler().node();

        String host = root.get("host").as(String.class, "0.0.0.0");
        int port = clamp(root.get("port").as(Integer.class, 8199), 1, 65535);

        int timeout = clamp(root.get("readTimeoutMillis").as(Integer.class, 5000), 1000, 60_000);
        int backlog = clamp(root.get("backlog").as(Integer.class, 50), 10, 10_000);
        int maxPacket = clamp(root.get("maxPacketBytes").as(Integer.class, 8192), 256, 1024 * 1024);

        boolean genKeys = root.get("generateKeys").as(Boolean.class, true);
        int keyBits = clamp(root.get("keyBits").as(Integer.class, 2048), 2048, 8192);

        String pub = safeFile(root.get("publicKeyFile").as(String.class, "public.key"));
        String priv = safeFile(root.get("privateKeyFile").as(String.class, "private.key"));

        String allow = root.get("allowlist").as(String.class, "");
        String deny = root.get("denylist").as(String.class, "");

        var redis = root.get("redis");
        boolean redisEnabled = redis.get("enabled").as(Boolean.class, true);
        String channel = redis.get("channel").as(String.class, "proxy.votifier.vote");
        boolean publishLegacy = redis.get("publish_legacy_channel").as(Boolean.class, true);
        String legacyChannel = redis.get("legacy_channel").as(String.class, "vote");

        var stats = root.get("stats");
        boolean statsEnabled = stats.get("enabled").as(Boolean.class, true);
        ZoneId zone = parseZone(stats.get("timezone").as(String.class, "Europe/Amsterdam"));
        int checkMinutes = clamp(stats.get("reset_check_minutes").as(Integer.class, 5), 1, 1440);
        int streakGapHours = clamp(stats.get("streak_gap_hours").as(Integer.class, 36), 1, 168);
        int dumpTopN = clamp(stats.get("dump_top_n").as(Integer.class, 100), 1, 1000);
        String dumpPrefix = safePrefix(stats.get("dump_file_prefix").as(String.class, "votifier-top100"));

        var logging = root.get("logging");
        boolean logVotes = logging.get("log_votes").as(Boolean.class, true);

        var remind = root.get("remind");
        boolean remindEnabled = remind.get("enabled").as(Boolean.class, true);
        int thresholdHours = clamp(remind.get("threshold_hours").as(Integer.class, 24), 1, 24 * 365);
        int initialDelayMinutes = clamp(remind.get("initial_delay_minutes").as(Integer.class, 5), 0, 24 * 60);
        int intervalMinutes = clamp(remind.get("interval_minutes").as(Integer.class, 60), 1, 24 * 60);

        var vote = root.get("vote");
        String voteUrl = vote.get("url").as(String.class, "https://hauntedmc.nl/vote");

        return new VotifierConfig(
                host,
                port,
                timeout,
                backlog,
                maxPacket,
                genKeys,
                keyBits,
                pub,
                priv,
                allow,
                deny,
                redisEnabled,
                channel,
                publishLegacy,
                legacyChannel,
                statsEnabled,
                zone,
                checkMinutes,
                streakGapHours,
                dumpTopN,
                dumpPrefix,
                logVotes,
                remindEnabled,
                thresholdHours,
                initialDelayMinutes,
                intervalMinutes,
                voteUrl == null ? "" : voteUrl.trim()
        );
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static ZoneId parseZone(String raw) {
        try {
            if (raw == null || raw.isBlank()) return ZoneId.systemDefault();
            return ZoneId.of(raw.trim());
        } catch (Exception ignored) {
            return ZoneId.systemDefault();
        }
    }

    private static String safeFile(String s) {
        if (s == null || s.isBlank()) return "key.pem";
        String t = s.trim().replace("\\", "/");
        while (t.startsWith("/")) t = t.substring(1);
        if (t.contains("..")) t = t.replace("..", "");
        return t.isBlank() ? "key.pem" : t;
    }

    private static String safePrefix(String s) {
        if (s == null || s.isBlank()) return "votifier-top100";
        String t = s.trim().replace("\\", "/");
        while (t.startsWith("/")) t = t.substring(1);
        if (t.contains("..")) t = t.replace("..", "");
        return t.isBlank() ? "votifier-top100" : t;
    }
}