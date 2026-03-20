package nl.hauntedmc.proxyfeatures.api.io.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class YamlFileTest {

    @TempDir
    Path tempDir;

    @Test
    void readWriteMutateAndContainsWork() throws IOException {
        Path path = tempDir.resolve("config.yml");
        Files.createFile(path);
        YamlFile yaml = new YamlFile(path, mock(Logger.class));

        yaml.setRawAndSave("global.name", "proxy");
        assertEquals("proxy", yaml.getRaw("global.name"));
        assertTrue(yaml.contains("global.name"));
        assertFalse(yaml.contains("global.missing"));

        yaml.mutateAndSave(root -> root.node("global", "enabled").raw(true));
        assertEquals(true, yaml.getRaw("global.enabled"));

        yaml.setRawAndSave("", Map.of("x", 1));
        assertEquals(1, ((Map<?, ?>) yaml.getRaw("")).get("x"));

        assertArrayEquals(new Object[]{"a", "b", "c"}, YamlFile.splitPath("a.b.c"));
        assertArrayEquals(new Object[0], YamlFile.splitPath(""));
    }

    @Test
    void reloadHandlesMalformedYamlGracefully() throws IOException {
        Path path = tempDir.resolve("broken.yml");
        Files.writeString(path, "global: [broken");
        YamlFile yaml = new YamlFile(path, mock(Logger.class));

        assertDoesNotThrow(yaml::reload);
        assertNull(yaml.getRaw("global.name"));
        assertFalse(yaml.contains("global.name"));
    }

    @Test
    void containsRootAndErrorBranchesAreHandled() throws Exception {
        Path path = tempDir.resolve("edge.yml");
        Files.createFile(path);
        Logger logger = mock(Logger.class);
        YamlFile yaml = new YamlFile(path, logger);
        yaml.setRawAndSave("a", 1);

        assertTrue(yaml.contains(""));

        Field rootField = YamlFile.class.getDeclaredField("root");
        rootField.setAccessible(true);
        Object originalRoot = rootField.get(yaml);
        rootField.set(yaml, null);
        assertNull(yaml.getRaw("a"));
        yaml.setRawAndSave("b", 2);
        rootField.set(yaml, originalRoot);

        Files.delete(path);
        Files.createDirectory(path);
        assertDoesNotThrow(yaml::saveNow);
    }
}
