package nl.hauntedmc.proxyfeatures.features.announcer.internal;

import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.features.announcer.Announcer;
import nl.hauntedmc.proxyfeatures.framework.log.FeatureLogger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnnouncerRegistryTest {

    @Test
    void loadsAnnouncementsFromConfigAndAppliesDefaults(@TempDir Path dataDir) throws Exception {
        Files.createDirectories(dataDir.resolve("local"));
        Files.writeString(dataDir.resolve("local/announcer.yml"),
                """
                        messages:
                          text1:
                            weight: 0
                          custom:
                            key: "announcer.custom.key"
                            weight: 3
                        """);

        AnnouncerRegistry registry = new AnnouncerRegistry(announcerFeature(dataDir));
        List<AnnouncerRegistry.Announcement> announcements = registry.announcements();

        assertEquals(2, announcements.size());
        assertEquals("announcer.text1", announcements.getFirst().key());
        assertEquals(1, announcements.getFirst().weight()); // clamped from 0 -> 1
        assertEquals("announcer.custom.key", announcements.get(1).key());
        assertEquals(3, announcements.get(1).weight());
    }

    @Test
    void fallsBackWhenNoMessagesDefined(@TempDir Path dataDir) throws Exception {
        Files.createDirectories(dataDir.resolve("local"));
        Files.writeString(dataDir.resolve("local/announcer.yml"), "messages: {}");

        FeatureLogger logger = mock(FeatureLogger.class);
        AnnouncerRegistry registry = new AnnouncerRegistry(announcerFeature(dataDir, logger));

        assertEquals(1, registry.announcements().size());
        AnnouncerRegistry.Announcement fallback = registry.announcements().getFirst();
        assertEquals(AnnouncerRegistry.FALLBACK_KEY, fallback.key());
        assertEquals(1, fallback.weight());
        verify(logger).warn(contains("No messages found"));
    }

    @Test
    void reloadReplacesCurrentAnnouncementSnapshot(@TempDir Path dataDir) throws Exception {
        Files.createDirectories(dataDir.resolve("local"));
        Path file = dataDir.resolve("local/announcer.yml");
        Files.writeString(file, "messages: {a: {weight: 1}}");

        AnnouncerRegistry registry = new AnnouncerRegistry(announcerFeature(dataDir));
        assertEquals("announcer.a", registry.announcements().getFirst().key());

        Files.writeString(file, "messages: {b: {key: \"announcer.next\", weight: 2}}");
        registry.reload();

        assertEquals(1, registry.announcements().size());
        assertEquals("announcer.next", registry.announcements().getFirst().key());
        assertEquals(2, registry.announcements().getFirst().weight());
    }

    private static Announcer announcerFeature(Path dataDir) {
        return announcerFeature(dataDir, mock(FeatureLogger.class));
    }

    private static Announcer announcerFeature(Path dataDir, FeatureLogger logger) {
        Announcer feature = mock(Announcer.class);
        ProxyFeatures plugin = mock(ProxyFeatures.class);
        ComponentLogger pluginLogger = ComponentLogger.logger("AnnouncerRegistryTest");
        when(feature.getPlugin()).thenReturn(plugin);
        when(feature.getLogger()).thenReturn(logger);
        when(plugin.getDataDirectory()).thenReturn(dataDir);
        when(plugin.getLogger()).thenReturn(pluginLogger);
        return feature;
    }
}
