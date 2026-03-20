package nl.hauntedmc.proxyfeatures.api.io.resource;

import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResourceHandlerTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsSavesAndReloadsExistingResourceFile() throws Exception {
        Path file = tempDir.resolve("lang/messages.yml");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "general:\n  usage: \"hello\"\n");

        ProxyFeatures plugin = mockPlugin();
        ResourceHandler handler = new ResourceHandler(plugin, "lang/messages.yml");
        assertNotNull(handler.getConfig());
        assertEquals("hello", handler.getConfig().node("general", "usage").getString());

        handler.getConfig().node("general", "usage").raw("updated");
        handler.save();
        handler.reload();
        assertEquals("updated", handler.getConfig().node("general", "usage").getString());
    }

    @Test
    void rejectsPathsThatEscapeDataDirectory() {
        ProxyFeatures plugin = mockPlugin();
        assertThrows(IllegalArgumentException.class, () -> new ResourceHandler(plugin, "../outside.yml"));
    }

    @Test
    void copiesDefaultResourceWhenMissing() throws Exception {
        ProxyFeatures plugin = mockPlugin();
        Path copied = tempDir.resolve("resource-default.yml");
        assertFalse(Files.exists(copied));

        ResourceHandler handler = new ResourceHandler(plugin, "resource-default.yml");

        assertNotNull(handler.getConfig());
        assertTrue(Files.exists(copied));
        assertTrue(Files.readString(copied).contains("from-test-resource"));
    }

    @Test
    void ensureAndSaveErrorBranchesAreHandled() throws Exception {
        ProxyFeatures plugin = mockPlugin();

        Path blockedParent = tempDir.resolve("blocked");
        Files.writeString(blockedParent, "x");
        assertDoesNotThrow(() -> new ResourceHandler(plugin, "blocked/child.yml"));

        Path path = tempDir.resolve("save-fail.yml");
        Files.writeString(path, "v: 1\n");
        ResourceHandler handler = new ResourceHandler(plugin, "save-fail.yml");
        Files.delete(path);
        Files.createDirectory(path);

        assertDoesNotThrow(handler::save);
    }

    private ProxyFeatures mockPlugin() {
        ProxyFeatures plugin = mock(ProxyFeatures.class);
        when(plugin.getDataDirectory()).thenReturn(tempDir);
        when(plugin.getLogger()).thenReturn(mock(ComponentLogger.class));
        return plugin;
    }
}
