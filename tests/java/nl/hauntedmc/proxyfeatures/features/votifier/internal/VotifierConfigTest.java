package nl.hauntedmc.proxyfeatures.features.votifier.internal;

import nl.hauntedmc.proxyfeatures.api.io.config.ConfigNode;
import nl.hauntedmc.proxyfeatures.features.votifier.Votifier;
import nl.hauntedmc.proxyfeatures.framework.config.FeatureConfigHandler;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VotifierConfigTest {

    @Test
    void loadAppliesBoundsAndSanitizesPathLikeValues() {
        Votifier feature = featureWithConfig(Map.ofEntries(
                Map.entry("host", "127.0.0.1"),
                Map.entry("port", 99_999),
                Map.entry("readTimeoutMillis", 100),
                Map.entry("backlog", 99_999),
                Map.entry("maxPacketBytes", 100),
                Map.entry("generateKeys", false),
                Map.entry("keyBits", 1024),
                Map.entry("publicKeyFile", "/../public.pem"),
                Map.entry("privateKeyFile", "   "),
                Map.entry("allowlist", "1.2.3.4"),
                Map.entry("denylist", "5.6.7.8"),
                Map.entry("redis", Map.of(
                        "enabled", false,
                        "channel", "votes.custom",
                        "publish_legacy_channel", false,
                        "legacy_channel", "legacy.custom"
                )),
                Map.entry("stats", Map.of(
                        "enabled", true,
                        "timezone", "Invalid/Zone",
                        "reset_check_minutes", 0,
                        "streak_gap_hours", 999,
                        "dump_top_n", 0,
                        "dump_file_prefix", "/../"
                )),
                Map.entry("logging", Map.of("log_votes", false)),
                Map.entry("remind", Map.of(
                        "enabled", true,
                        "threshold_hours", 0,
                        "initial_delay_minutes", -5,
                        "interval_minutes", 0
                )),
                Map.entry("vote", Map.of("url", "  https://example.invalid/vote  "))
        ));

        VotifierConfig cfg = VotifierConfig.load(feature);

        assertEquals("127.0.0.1", cfg.host());
        assertEquals(65535, cfg.port());
        assertEquals(1000, cfg.readTimeoutMillis());
        assertEquals(10_000, cfg.backlog());
        assertEquals(256, cfg.maxPacketBytes());
        assertFalse(cfg.generateKeys());
        assertEquals(2048, cfg.keyBits());
        assertEquals("public.pem", cfg.publicKeyFile());
        assertEquals("key.pem", cfg.privateKeyFile());
        assertEquals("1.2.3.4", cfg.allowlistCsv());
        assertEquals("5.6.7.8", cfg.denylistCsv());
        assertFalse(cfg.redisEnabled());
        assertEquals("votes.custom", cfg.redisChannel());
        assertFalse(cfg.publishLegacyChannel());
        assertEquals("legacy.custom", cfg.legacyChannel());
        assertTrue(cfg.statsEnabled());
        assertEquals(ZoneId.systemDefault(), cfg.statsZone());
        assertEquals(1, cfg.resetCheckMinutes());
        assertEquals(168, cfg.streakGapHours());
        assertEquals(1, cfg.dumpTopN());
        assertEquals("votifier-top100", cfg.dumpFilePrefix());
        assertFalse(cfg.logVotes());
        assertTrue(cfg.remindEnabled());
        assertEquals(1, cfg.remindThresholdHours());
        assertEquals(0, cfg.remindInitialDelayMinutes());
        assertEquals(1, cfg.remindIntervalMinutes());
        assertEquals("https://example.invalid/vote", cfg.voteUrl());
    }

    @Test
    void loadUsesExpectedDefaultsWhenConfigIsEmpty() {
        VotifierConfig cfg = VotifierConfig.load(featureWithConfig(Map.of()));

        assertEquals("0.0.0.0", cfg.host());
        assertEquals(8199, cfg.port());
        assertEquals(5000, cfg.readTimeoutMillis());
        assertEquals(50, cfg.backlog());
        assertEquals(8192, cfg.maxPacketBytes());
        assertTrue(cfg.generateKeys());
        assertEquals(2048, cfg.keyBits());
        assertEquals("public.key", cfg.publicKeyFile());
        assertEquals("private.key", cfg.privateKeyFile());
        assertEquals("", cfg.allowlistCsv());
        assertEquals("", cfg.denylistCsv());
        assertTrue(cfg.redisEnabled());
        assertEquals("proxy.votifier.vote", cfg.redisChannel());
        assertTrue(cfg.publishLegacyChannel());
        assertEquals("vote", cfg.legacyChannel());
        assertTrue(cfg.statsEnabled());
        assertEquals(ZoneId.of("Europe/Amsterdam"), cfg.statsZone());
        assertEquals(5, cfg.resetCheckMinutes());
        assertEquals(36, cfg.streakGapHours());
        assertEquals(100, cfg.dumpTopN());
        assertEquals("votifier-top100", cfg.dumpFilePrefix());
        assertTrue(cfg.logVotes());
        assertTrue(cfg.remindEnabled());
        assertEquals(24, cfg.remindThresholdHours());
        assertEquals(5, cfg.remindInitialDelayMinutes());
        assertEquals(60, cfg.remindIntervalMinutes());
        assertEquals("https://hauntedmc.nl/vote", cfg.voteUrl());
    }

    private static Votifier featureWithConfig(Map<String, Object> rootData) {
        Votifier feature = mock(Votifier.class);
        FeatureConfigHandler cfg = mock(FeatureConfigHandler.class);
        when(feature.getConfigHandler()).thenReturn(cfg);
        when(cfg.node()).thenReturn(ConfigNode.ofRaw(rootData, "<root>"));
        return feature;
    }
}
