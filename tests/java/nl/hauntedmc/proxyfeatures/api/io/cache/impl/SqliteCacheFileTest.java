package nl.hauntedmc.proxyfeatures.api.io.cache.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

class SqliteCacheFileTest {

    @TempDir
    Path tempDir;

    @Test
    void createsUnderlyingFileAndSupportsDeleteAndNoopMethods() {
        Path db = tempDir.resolve("nested/cache.db");
        SqliteCacheFile file = new SqliteCacheFile(db.toFile());
        assertTrue(file.getUnderlyingFile().exists());
        assertFalse(file.isEmpty());

        file.cleanupExpired();
        file.delete();
        assertFalse(file.getUnderlyingFile().exists());
    }

    @Test
    void getConnectionEitherReturnsConnectionOrWrapsFailure() {
        SqliteCacheFile file = new SqliteCacheFile(tempDir.resolve("c.db").toFile());
        try {
            assertNotNull(file.getConnection());
            file.getConnection().close();
        } catch (RuntimeException ex) {
            assertNotNull(ex.getCause());
        } catch (Exception ex) {
            fail(ex);
        }
    }

    @Test
    void constructorAndDeleteFailurePathsAreCovered() throws Exception {
        Path parentFile = tempDir.resolve("not-a-dir");
        java.nio.file.Files.writeString(parentFile, "x");
        assertThrows(RuntimeException.class, () -> new SqliteCacheFile(parentFile.resolve("cache.db").toFile()));

        FailingDeleteFile failingDelete = new FailingDeleteFile(tempDir.resolve("fail-delete.db").toString());
        SqliteCacheFile file = new SqliteCacheFile(failingDelete);
        assertDoesNotThrow(file::delete);
    }

    @Test
    void getConnectionWrapsDriverManagerFailure() {
        SqliteCacheFile file = new SqliteCacheFile(tempDir.resolve("driver-fail.db").toFile());
        try (MockedStatic<DriverManager> mocked = mockStatic(DriverManager.class)) {
            mocked.when(() -> DriverManager.getConnection(anyString())).thenThrow(new IllegalStateException("boom"));
            RuntimeException ex = assertThrows(RuntimeException.class, file::getConnection);
            assertNotNull(ex.getCause());
        }
    }

    @Test
    void getConnectionReturnsDriverManagerConnectionWhenAvailable() throws Exception {
        SqliteCacheFile file = new SqliteCacheFile(tempDir.resolve("driver-ok.db").toFile());
        Connection connection = org.mockito.Mockito.mock(Connection.class);
        try (MockedStatic<DriverManager> mocked = mockStatic(DriverManager.class)) {
            mocked.when(() -> DriverManager.getConnection(anyString())).thenReturn(connection);
            assertSame(connection, file.getConnection());
        }
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
