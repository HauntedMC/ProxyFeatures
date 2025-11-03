package nl.hauntedmc.proxyfeatures.features.announcer.internal;

import nl.hauntedmc.proxyfeatures.api.io.config.ConfigNode;
import nl.hauntedmc.proxyfeatures.api.io.config.ConfigService;
import nl.hauntedmc.proxyfeatures.api.io.config.ConfigView;
import nl.hauntedmc.proxyfeatures.features.announcer.Announcer;
import org.slf4j.Logger;

import java.util.*;

/**
 * Loads announcer message keys from local/announcer.yml using the unified ConfigView API.
 */
public class AnnouncerRegistry {

    private static final String RESOURCE_FILE = "local/announcer.yml";

    private final Announcer feature;
    private final Logger logger;
    private final List<String> messageKeys = new ArrayList<>();

    public AnnouncerRegistry(Announcer feature) {
        this.feature = feature;
        this.logger = feature.getPlugin().getLogger();
        loadMessagesFromConfig();
    }

    public void reload() {
        messageKeys.clear();
        loadMessagesFromConfig();
    }

    private void loadMessagesFromConfig() {
        ConfigView store = new ConfigService(feature.getPlugin())
                .view(RESOURCE_FILE, /* copyDefaultsFromJar */ true);

        ConfigNode msgsNode = store.node("messages");
        Map<String, ConfigNode> defs = msgsNode.children(); // <-- type-safe, avoids raw Map

        if (defs.isEmpty()) {
            logger.warn("[ProxyFeatures] Announcer: no messages found in {}. Using 'announcer.default'.", RESOURCE_FILE);
            messageKeys.add("announcer.default");
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
                finalKey = idKey.contains(".") ? idKey.trim() : "announcer." + idKey.trim();
            }

            for (int i = 0; i < weight; i++) {
                messageKeys.add(finalKey);
            }
        }

        if (messageKeys.isEmpty()) {
            logger.warn("[ProxyFeatures] Announcer: all messages invalid/empty in {}. Using 'announcer.default'.", RESOURCE_FILE);
            messageKeys.add("announcer.default");
        } else {
            Collections.shuffle(messageKeys); // shuffle after weight expansion
        }
    }

    public String get(int index) {
        if (messageKeys.isEmpty()) return "announcer.default";
        return messageKeys.get(Math.floorMod(index, messageKeys.size()));
    }

    public int getTotalMessages() {
        return messageKeys.size();
    }
}
