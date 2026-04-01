package nl.hauntedmc.proxyfeatures.api.io.config;

import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfigServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void openCachesYamlFileAndRejectsPathEscape() {
        ConfigService service = new ConfigService(tempDir, LoggerFactory.getLogger(ConfigServiceTest.class), getClass().getClassLoader());
        YamlFile first = service.open("config.yml", false);
        YamlFile second = service.open("config.yml", false);
        assertSame(first, second);
        assertThrows(IllegalArgumentException.class, () -> service.open("../evil.yml", false));
    }

    @Test
    void openCopiesDefaultsWhenResourceExistsOtherwiseCreatesEmptyFile() throws Exception {
        ClassLoader loader = new ClassLoader(getClass().getClassLoader()) {
            @Override
            public InputStream getResourceAsStream(String name) {
                if ("defaults.yml".equals(name)) {
                    return new ByteArrayInputStream("value: 7\n".getBytes(StandardCharsets.UTF_8));
                }
                return null;
            }
        };
        ConfigService service = new ConfigService(tempDir, LoggerFactory.getLogger(ConfigServiceTest.class), loader);

        service.open("defaults.yml", true);
        assertTrue(Files.readString(tempDir.resolve("defaults.yml")).contains("value: 7"));

        service.open("empty.yml", true);
        assertTrue(Files.exists(tempDir.resolve("empty.yml")));
    }

    @Test
    void viewHelpersReturnUsableViews() {
        ComponentLogger logger = ComponentLogger.logger("ConfigServiceTest");
        ProxyFeatures plugin = mock(ProxyFeatures.class);
        when(plugin.getDataDirectory()).thenReturn(tempDir);
        when(plugin.getLogger()).thenReturn(logger);

        ConfigService service = new ConfigService(plugin);
        ConfigView root = service.view("a.yml", false);
        ConfigView scoped = service.view("b.yml", false, "global");

        root.put("x", 1);
        scoped.put("name", "proxy");

        assertEquals(1, root.get("x", Integer.class));
        assertEquals("proxy", scoped.get("name", String.class));
    }

    @Test
    void constructorHandlesNullResourcesAndOpenWrapsIoFailures() throws Exception {
        ConfigService withNullResources = new ConfigService(tempDir, LoggerFactory.getLogger(ConfigServiceTest.class), null);
        assertNotNull(withNullResources.open("null-resource.yml", false));

        Path blocked = tempDir.resolve("blocked-dir");
        Files.writeString(blocked, "x");
        ConfigService failing = new ConfigService(blocked, LoggerFactory.getLogger(ConfigServiceTest.class), getClass().getClassLoader());
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> failing.open("test.yml", false));
        assertTrue(ex.getMessage().contains("Failed to open YAML file"));
    }
}
