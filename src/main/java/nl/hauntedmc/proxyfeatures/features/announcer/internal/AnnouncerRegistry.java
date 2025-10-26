package nl.hauntedmc.proxyfeatures.features.announcer.internal;

import nl.hauntedmc.proxyfeatures.api.io.config.ConfigNode;
import nl.hauntedmc.proxyfeatures.features.announcer.Announcer;

import java.util.ArrayList;
import java.util.List;

public class AnnouncerRegistry {

    private final Announcer feature;
    private final List<String> messageKeys = new ArrayList<>();

    public AnnouncerRegistry(Announcer feature) {
        this.feature = feature;
        loadMessagesFromConfig();
    }

    private void loadMessagesFromConfig() {
        ConfigNode msgsNode = feature.getConfigHandler().node("messages");
        try {
            List<String> ids = msgsNode.listOf(String.class);
            if (ids != null) {
                for (String id : ids) {
                    if (id != null && !id.isBlank()) {
                        messageKeys.add("announcer." + id.trim());
                    }
                }
            }
        } catch (IllegalArgumentException ignore) {
        }
    }

    public String get(int index) {
        return messageKeys.get(index % messageKeys.size());
    }

    public int getTotalMessages() {
        return messageKeys.size();
    }
}
