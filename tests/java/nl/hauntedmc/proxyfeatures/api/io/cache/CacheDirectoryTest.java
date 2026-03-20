package nl.hauntedmc.proxyfeatures.api.io.cache;

import nl.hauntedmc.proxyfeatures.api.io.cache.impl.JsonCacheFile;
import nl.hauntedmc.proxyfeatures.api.io.cache.impl.SqliteCacheFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CacheDirectoryTest {

    @TempDir
    Path tempDir;

    @Test
    void createsSanitizedDirectoryAndBuildsRequestedStoreTypes() {
        CacheDirectory directory = new CacheDirectory(tempDir.toFile(), "../feature", "  cache/id ");
        assertTrue(directory.getDirectory().exists());
        assertTrue(directory.getDirectory().getName().contains("feature"));
        assertTrue(directory.getDirectory().getName().contains("cache"));

        CacheStore json = directory.getStore("../players", CacheType.JSON);
        CacheStore sqlite = directory.getStore("db", CacheType.SQLITE);
        assertInstanceOf(JsonCacheFile.class, json);
        assertInstanceOf(SqliteCacheFile.class, sqlite);
    }

    @Test
    void defaultsAreUsedForNullAndBlankSegments() {
        CacheDirectory directory = new CacheDirectory(tempDir.toFile(), null, "   ");
        assertTrue(directory.getDirectory().getName().contains("feature-cache"));
        assertTrue(directory.getStore("", CacheType.JSON).getUnderlyingFile().getName().startsWith("store"));

        CacheDirectory trimmedToBlank = new CacheDirectory(tempDir.toFile(), "\u0000", "cache");
        assertTrue(trimmedToBlank.getDirectory().getName().startsWith("feature-"));
    }

    @Test
    void constructorFailsWhenDirectoryCannotBeCreated() throws IOException {
        Path baseFile = tempDir.resolve("base-file");
        Files.writeString(baseFile, "x");
        assertThrows(IllegalStateException.class, () -> new CacheDirectory(baseFile.toFile(), "feature", "cache"));
    }

    @Test
    void constructorRejectsEscapingPathsAndWrapsCanonicalResolutionFailure() {
        FileWithCanonicalMismatch mismatch = new FileWithCanonicalMismatch(tempDir.resolve("mismatch").toString());
        IllegalArgumentException escaped = assertThrows(IllegalArgumentException.class,
                () -> new CacheDirectory(mismatch, "feature", "cache"));
        assertTrue(escaped.getMessage().contains("escapes base folder"));

        FileWithCanonicalFailure failing = new FileWithCanonicalFailure(tempDir.resolve("iofail").toString());
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new CacheDirectory(failing, "feature", "cache"));
        assertTrue(ex.getMessage().contains("Could not resolve cache directory"));
    }

    private static final class FileWithCanonicalMismatch extends java.io.File {
        FileWithCanonicalMismatch(String pathname) {
            super(pathname);
        }

        @Override
        public java.io.File getCanonicalFile() {
            return new java.io.File(getParentFile(), "other");
        }
    }

    private static final class FileWithCanonicalFailure extends java.io.File {
        FileWithCanonicalFailure(String pathname) {
            super(pathname);
        }

        @Override
        public java.io.File getCanonicalFile() throws IOException {
            throw new IOException("boom");
        }
    }
}
