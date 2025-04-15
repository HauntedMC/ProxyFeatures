package nl.hauntedmc.proxyfeatures.features.announcer.internal;

import nl.hauntedmc.proxyfeatures.features.announcer.Announcer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AnnouncerRegistry {

    private final Announcer feature;
    private final List<String> messageKeys = new ArrayList<>();

    public AnnouncerRegistry(Announcer feature) {
        this.feature = feature;
        loadMessagesFromConfig();
    }

    private void loadMessagesFromConfig() {
        Object raw = feature.getConfigHandler().getSetting("messages");
        if (raw instanceof List<?> messageList) {
            for (Object obj : messageList) {
                if (obj instanceof Map<?, ?> map) {
                    String key = map.get("message_key").toString();
                    messageKeys.add("announcer."+key);
                }
            }
        }
    }

    public List<String> getMessageKeys() {
        return messageKeys;
    }

    public String get(int index) {
        return messageKeys.get(index % messageKeys.size());
    }

    public int getTotalMessages() {
        return messageKeys.size();
    }
}
