package nl.hauntedmc.proxyfeatures.features.clientinfo.internal;

import com.velocitypowered.api.proxy.player.PlayerSettings;
import nl.hauntedmc.proxyfeatures.api.io.config.ConfigNode;
import nl.hauntedmc.proxyfeatures.api.io.config.ConfigTypes;
import nl.hauntedmc.proxyfeatures.api.io.config.ConfigView;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClientInfoConfigTest {

    @Test
    void loadParsesGlobalSettingsAndProfileOverrides() {
        ConfigView view = configView(Map.of(
                "notify", Map.of(
                        "enabled", false,
                        "debounce_millis", 2500L,
                        "cooldown_millis", 60_000L,
                        "only_send_if_changed", true,
                        "only_once_per_session", true
                ),
                "checks", Map.of(
                        "view_distance", Map.of("enabled", true),
                        "chat_mode", Map.of("enabled", true),
                        "particles", Map.of("enabled", false)
                ),
                "recommend", Map.of(
                        "view_distance_min", 6,
                        "chat_mode", "commands_only",
                        "particles", "minimal"
                ),
                "profiles", Map.of(
                        "skyblock", Map.of(
                                "servers", List.of("SkyBlock", "SB"),
                                "checks", Map.of("view_distance", false),
                                "recommend", Map.of(
                                        "view_distance_min", 12,
                                        "chat_mode", "hidden",
                                        "particles", "decreased"
                                )
                        ),
                        "pvp", Map.of(
                                "checks", Map.of("chat_mode", false),
                                "recommend", Map.of("particles", "all")
                        )
                )
        ));

        ClientInfoConfig cfg = ClientInfoConfig.load(view);
        assertFalse(cfg.notifyEnabled());
        assertEquals(2500L, cfg.notifyDebounceMillis());
        assertEquals(60_000L, cfg.notifyCooldownMillis());
        assertTrue(cfg.notifyOnlyIfChanged());
        assertTrue(cfg.notifyOnlyOncePerSession());
        assertEquals(2, cfg.profiles().size());

        ClientInfoAdvisor.EffectiveConfig skyblock = cfg.effectiveForServer("sb");
        assertFalse(skyblock.checkViewDistance());
        assertTrue(skyblock.checkChatMode());
        assertFalse(skyblock.checkParticles());
        assertEquals(12, skyblock.recommendViewDistanceMin());
        assertEquals(PlayerSettings.ChatMode.HIDDEN, skyblock.recommendChatMode());
        assertEquals(PlayerSettings.ParticleStatus.DECREASED, skyblock.recommendParticles());

        ClientInfoAdvisor.EffectiveConfig pvpByNameFallback = cfg.effectiveForServer("PVP");
        assertTrue(pvpByNameFallback.checkViewDistance());
        assertFalse(pvpByNameFallback.checkChatMode());
        assertFalse(pvpByNameFallback.checkParticles());
        assertEquals(6, pvpByNameFallback.recommendViewDistanceMin());
        assertEquals(PlayerSettings.ChatMode.COMMANDS_ONLY, pvpByNameFallback.recommendChatMode());
        assertEquals(PlayerSettings.ParticleStatus.ALL, pvpByNameFallback.recommendParticles());

        ClientInfoAdvisor.EffectiveConfig unknown = cfg.effectiveForServer("hub");
        assertTrue(unknown.checkViewDistance());
        assertTrue(unknown.checkChatMode());
        assertFalse(unknown.checkParticles());
        assertEquals(6, unknown.recommendViewDistanceMin());
        assertEquals(PlayerSettings.ChatMode.COMMANDS_ONLY, unknown.recommendChatMode());
        assertEquals(PlayerSettings.ParticleStatus.MINIMAL, unknown.recommendParticles());
    }

    @Test
    void invalidModesFallBackToSafeDefaults() {
        ConfigView view = configView(Map.of(
                "recommend", Map.of(
                        "chat_mode", "not-a-mode",
                        "particles", "bad-particles"
                ),
                "profiles", Map.of(
                        "minigame", Map.of(
                                "servers", List.of("mg"),
                                "recommend", Map.of(
                                        "chat_mode", "also-invalid",
                                        "particles", "still-invalid"
                                )
                        )
                )
        ));

        ClientInfoConfig cfg = ClientInfoConfig.load(view);
        assertEquals(PlayerSettings.ChatMode.SHOWN, cfg.recommendChatMode());
        assertEquals(PlayerSettings.ParticleStatus.ALL, cfg.recommendParticles());

        ClientInfoAdvisor.EffectiveConfig mg = cfg.effectiveForServer("mg");
        assertEquals(PlayerSettings.ChatMode.SHOWN, mg.recommendChatMode());
        assertEquals(PlayerSettings.ParticleStatus.ALL, mg.recommendParticles());
    }

    @Test
    void blankOrNullServerUsesGlobalConfiguration() {
        ClientInfoConfig cfg = ClientInfoConfig.load(configView(Map.of(
                "recommend", Map.of(
                        "view_distance_min", 9,
                        "chat_mode", "shown",
                        "particles", "all"
                )
        )));

        ClientInfoAdvisor.EffectiveConfig blank = cfg.effectiveForServer("  ");
        ClientInfoAdvisor.EffectiveConfig nil = cfg.effectiveForServer(null);

        assertEquals(9, blank.recommendViewDistanceMin());
        assertEquals(9, nil.recommendViewDistanceMin());
        assertEquals(PlayerSettings.ChatMode.SHOWN, blank.recommendChatMode());
        assertEquals(PlayerSettings.ParticleStatus.ALL, nil.recommendParticles());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ConfigView configView(Map<String, Object> raw) {
        ConfigNode root = ConfigNode.ofRaw(raw, "<root>");
        ConfigView view = mock(ConfigView.class);

        when(view.get(anyString(), any(Class.class), any())).thenAnswer(invocation -> {
            String path = invocation.getArgument(0);
            Class<?> type = invocation.getArgument(1);
            Object def = invocation.getArgument(2);
            return ConfigTypes.convertOrDefault(root.getAt(path).raw(), (Class) type, def);
        });

        when(view.node(anyString())).thenAnswer(invocation -> {
            String path = invocation.getArgument(0);
            return root.getAt(path);
        });

        return view;
    }
}
