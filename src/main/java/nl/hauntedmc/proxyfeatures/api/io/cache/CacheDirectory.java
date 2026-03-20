package nl.hauntedmc.proxyfeatures.api.io.cache;

import nl.hauntedmc.proxyfeatures.api.io.cache.impl.JsonCacheFile;
import nl.hauntedmc.proxyfeatures.api.io.cache.impl.SqliteCacheFile;

import java.io.File;
import java.io.IOException;

/**
 * Represents a feature-specific subfolder under plugins/.../cache/.
 * Use {@link #getStore} to make per-file stores inside it.
 */
public class CacheDirectory {
    private final File dir;

    /**
     * @param baseCacheFolder plugins/.../cache/
     * @param featureName     e.g. "voteRewards"
     * @param cacheId         e.g. "queue" or player-name
     */
    public CacheDirectory(File baseCacheFolder, String featureName, String cacheId) {
        String safeFeature = sanitizeSegment(featureName, "feature");
        String safeCacheId = sanitizeSegment(cacheId, "cache");
        File candidate = new File(baseCacheFolder, safeFeature + "-" + safeCacheId);
        try {
            File base = baseCacheFolder.getCanonicalFile();
            File normalized = candidate.getCanonicalFile();
            if (!normalized.toPath().startsWith(base.toPath())) {
                throw new IllegalArgumentException("Cache directory escapes base folder");
            }
            this.dir = normalized;
        } catch (IOException e) {
            throw new IllegalStateException("Could not resolve cache directory", e);
        }

        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Could not create cache directory: " + dir);
        }
    }

    /**
     * The underlying directory on disk.
     */
    public File getDirectory() {
        return dir;
    }

    /**
     * Create or open a cache store file inside this directory.
     *
     * @param fileName name without extension — e.g. a player name or "logs"
     * @param type     YAML, JSON, or SQLITE
     */
    public CacheStore getStore(String fileName, CacheType type) {
        File file;
        String safeName = sanitizeSegment(fileName, "store");
        return switch (type) {
            case JSON -> {
                file = new File(dir, safeName + ".json");
                yield new JsonCacheFile(file);
            }
            case SQLITE -> {
                file = new File(dir, safeName + ".db");
                yield new SqliteCacheFile(file);
            }
        };
    }

    private static String sanitizeSegment(String raw, String def) {
        if (raw == null || raw.isBlank()) return def;
        String normalized = raw.trim()
                .replace('\\', '_')
                .replace('/', '_')
                .replace("..", "_");
        return normalized.isBlank() ? def : normalized;
    }
}
