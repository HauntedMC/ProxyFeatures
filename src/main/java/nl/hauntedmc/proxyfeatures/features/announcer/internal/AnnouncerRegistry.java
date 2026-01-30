package nl.hauntedmc.proxyfeatures.features.announcer.internal;

import nl.hauntedmc.proxyfeatures.api.io.config.ConfigNode;
import nl.hauntedmc.proxyfeatures.api.io.config.ConfigService;
import nl.hauntedmc.proxyfeatures.api.io.config.ConfigView;
import nl.hauntedmc.proxyfeatures.features.announcer.Announcer;
import nl.hauntedmc.proxyfeatures.framework.log.FeatureLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class AnnouncerRegistry {

    private static final String RESOURCE_FILE = "local/announcer.yml";
    public static final String FALLBACK_KEY = "announcer.text1";

    public record Announcement(String key, int weight) {
    }

    private final Announcer feature;
    private final FeatureLogger logger;

    private final List<Announcement> announcements = new ArrayList<>();
    private long totalWeight = 0;

    public AnnouncerRegistry(Announcer feature) {
        this.feature = feature;
        this.logger = feature.getLogger(); // ✅ requested change
        loadMessagesFromConfig();
    }

    public void reload() {
        announcements.clear();
        totalWeight = 0;
        loadMessagesFromConfig();
    }

    public List<Announcement> announcements() {
        return Collections.unmodifiableList(announcements);
    }

    private void loadMessagesFromConfig() {
        ConfigView store = new ConfigService(feature.getPlugin())
                .view(RESOURCE_FILE, /* copyDefaultsFromJar */ true);

        ConfigNode msgsNode = store.node("messages");
        Map<String, ConfigNode> defs = msgsNode.children();

        if (defs.isEmpty()) {
            logger.warn("No messages found in " + RESOURCE_FILE + ". Using fallback '" + FALLBACK_KEY + "'.");
            addFallback();
            return;
        }

        for (Map.Entry<String, ConfigNode> e : defs.entrySet()) {
            String idKey = e.getKey();
            if (idKey == null || idKey.isBlank()) continue;

            ConfigNode spec = e.getValue();

            String explicitKey = spec.get("key").as(String.class, null);
            int weight = spec.get("weight").as(Integer.class, 1);
            if (weight < 1) weight = 1;

            final String finalKey;
            if (explicitKey != null && !explicitKey.isBlank()) {
                finalKey = explicitKey.trim();
            } else {
                String trimmed = idKey.trim();
                finalKey = trimmed.contains(".") ? trimmed : "announcer." + trimmed;
            }

            announcements.add(new Announcement(finalKey, weight));
            totalWeight += weight;
        }

        if (announcements.isEmpty() || totalWeight <= 0) {
            logger.warn("All messages invalid/empty in " + RESOURCE_FILE + ". Using fallback '" + FALLBACK_KEY + "'.");
            announcements.clear();
            totalWeight = 0;
            addFallback();
        }
    }

    private void addFallback() {
        announcements.add(new Announcement(FALLBACK_KEY, 1));
        totalWeight = 1;
    }
}
