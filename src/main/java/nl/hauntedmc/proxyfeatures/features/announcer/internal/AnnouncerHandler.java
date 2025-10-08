package nl.hauntedmc.proxyfeatures.features.announcer.internal;

import nl.hauntedmc.proxyfeatures.features.announcer.Announcer;
import nl.hauntedmc.proxyfeatures.lifecycle.FeatureTaskManager;
import net.kyori.adventure.text.Component;

import java.time.Duration;

public class AnnouncerHandler {

    private final FeatureTaskManager taskManager;
    private final AnnouncerRegistry announcerRegistry;
    private final int messageInterval;
    private final Announcer feature;

    private int currentMessageIndex = 0;

    public AnnouncerHandler(Announcer feature) {
        this.feature = feature;
        this.taskManager = feature.getLifecycleManager().getTaskManager();
        this.announcerRegistry = new AnnouncerRegistry(feature);
        this.messageInterval = feature.getConfigHandler().getSetting("message_interval", Integer.class, 200);
    }

    public void startAnnouncementCycle() {
        if (announcerRegistry.getTotalMessages() == 0) {
            return;
        }
        taskManager.scheduleRepeatingTask(() -> {
            String messageKey = announcerRegistry.get(currentMessageIndex);
            feature.getPlugin().getProxy().getAllPlayers().forEach(player -> {
                Component message = feature.getLocalizationHandler()
                        .getMessage(messageKey).forAudience(player).build();
                player.sendMessage(message);
            });
            currentMessageIndex = (currentMessageIndex + 1) % announcerRegistry.getTotalMessages();
        }, Duration.ofSeconds(messageInterval));
    }
}
