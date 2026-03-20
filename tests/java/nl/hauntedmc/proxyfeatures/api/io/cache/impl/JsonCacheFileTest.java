package nl.hauntedmc.proxyfeatures.api.io.cache.impl;

import nl.hauntedmc.proxyfeatures.api.io.cache.CacheValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonCacheFileTest {

    @TempDir
    Path tempDir;

    @Test
    void putGetListFindAndDeleteWork() {
        Path file = tempDir.resolve("cache.json");
        JsonCacheFile store = new JsonCacheFile(file.toFile());

        CacheValue value = CacheValue.of(Map.of("name", "Remy"), System.currentTimeMillis() + 60_000);
        store.put("player.remy", value);
        assertEquals("Remy", store.get("player.remy").getData().get("name"));
        assertEquals(1, store.listAll().size());
        assertEquals(1, store.find("player\\..*").size());
        assertTrue(store.find("other.*").isEmpty());
        assertFalse(store.isEmpty());

        store.delete();
        assertTrue(store.isEmpty());
        assertFalse(file.toFile().exists());
    }

    @Test
    void cleanupExpiredRemovesEntriesAndDeletesFileWhenEmpty() {
        Path file = tempDir.resolve("expired.json");
        JsonCacheFile store = new JsonCacheFile(file.toFile());
        store.put("a", CacheValue.of(Map.of("x", 1), System.currentTimeMillis() - 5_000));

        store.cleanupExpired();

        assertNull(store.get("a"));
        assertTrue(store.isEmpty());
        assertFalse(file.toFile().exists());
    }

    @Test
    void loadHandlesStringTimestampsAndNonMapValueEntries() throws IOException {
        Path file = tempDir.resolve("manual.json");
        Files.writeString(file, """
                {
                  "string-ts": { "value": {"a":1}, "expirationTimestamp": "9999999999999" },
                  "bad-value": { "value": "not-an-object", "expirationTimestamp": "9999999999999" },
                  "null-entry": null
                }
                """);

        JsonCacheFile store = new JsonCacheFile(file.toFile());
        CacheValue ts = store.get("string-ts");
        assertNotNull(ts);
        assertEquals(1.0, ts.getData().get("a"));

        CacheValue bad = store.get("bad-value");
        assertNotNull(bad);
        assertEquals(Map.of(), bad.getData());

        assertNull(store.get("null-entry"));
    }

    @Test
    void constructorAndSaveFailuresAreWrappedWithIllegalState() throws Exception {
        Path notDirectory = tempDir.resolve("not-directory");
        Files.writeString(notDirectory, "x");
        assertThrows(IllegalStateException.class,
                () -> new JsonCacheFile(notDirectory.resolve("cache.json").toFile()));

        Path directoryPath = tempDir.resolve("dir-as-file.json");
        Files.createDirectory(directoryPath);
        assertThrows(IllegalStateException.class, () -> new JsonCacheFile(directoryPath.toFile()));

        Path saveFail = tempDir.resolve("save-fail.json");
        JsonCacheFile store = new JsonCacheFile(saveFail.toFile());
        Files.delete(saveFail);
        Files.createDirectory(saveFail);
        assertThrows(IllegalStateException.class, () -> store.put("a", CacheValue.of(Map.of("x", 1), -1)));
    }

    @Test
    void deleteFallbackBranchesAndPrivateNormalizersAreCovered() throws Exception {
        Path stickyPath = tempDir.resolve("sticky.json");
        Files.writeString(stickyPath, "{}");
        FailingDeleteFile stickyFile = new FailingDeleteFile(stickyPath.toString());
        JsonCacheFile stickyStore = new JsonCacheFile(stickyFile);

        stickyStore.put("expired", CacheValue.of(Map.of("x", 1), System.currentTimeMillis() - 1_000));
        stickyStore.cleanupExpired();
        stickyStore.delete();

        Method asDataMap = JsonCacheFile.class.getDeclaredMethod("asDataMap", Object.class);
        asDataMap.setAccessible(true);
        LinkedHashMap<Object, Object> raw = new LinkedHashMap<>();
        raw.put(null, "ignored");
        raw.put("ok", 1);
        @SuppressWarnings("unchecked")
        Map<String, Object> normalized = (Map<String, Object>) asDataMap.invoke(null, raw);
        assertEquals(Map.of("ok", 1), normalized);

        Method asLong = JsonCacheFile.class.getDeclaredMethod("asLong", Object.class, long.class);
        asLong.setAccessible(true);
        assertEquals(8L, (long) asLong.invoke(null, 8L, -1L));
        assertEquals(-1L, (long) asLong.invoke(null, "not-a-long", -1L));
        assertEquals(5L, (long) asLong.invoke(null, new Object(), 5L));
    }

    private static final class FailingDeleteFile extends File {
        FailingDeleteFile(String pathname) {
            super(pathname);
        }

        @Override
        public boolean delete() {
            return false;
        }

        @Override
        public boolean exists() {
            return true;
        }
    }
}
