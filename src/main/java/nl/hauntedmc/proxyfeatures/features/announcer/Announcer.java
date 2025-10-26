package nl.hauntedmc.proxyfeatures.features.announcer;

import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.proxyfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.proxyfeatures.features.VelocityBaseFeature;
import nl.hauntedmc.proxyfeatures.features.announcer.internal.AnnouncerHandler;
import nl.hauntedmc.proxyfeatures.features.announcer.meta.Meta;

import java.util.List;

public class Announcer extends VelocityBaseFeature<Meta> {

    public Announcer(ProxyFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);
        defaults.put("message_interval", 200);

        defaults.put("messages", List.of("text1"));
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messages = new MessageMap();
        messages.add("announcer.text1", "&6Announcement Bericht");
        return messages;
    }

    @Override
    public void initialize() {
        AnnouncerHandler announcerHandler = new AnnouncerHandler(this);
        announcerHandler.startAnnouncementCycle();
    }

    @Override
    public void disable() {
    }
}
