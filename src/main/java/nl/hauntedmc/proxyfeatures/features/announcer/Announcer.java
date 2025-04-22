package nl.hauntedmc.proxyfeatures.features.announcer;

import nl.hauntedmc.commonlib.localization.MessageMap;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.features.VelocityBaseFeature;
import nl.hauntedmc.proxyfeatures.features.announcer.internal.AnnouncerHandler;
import nl.hauntedmc.proxyfeatures.features.announcer.meta.Meta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Announcer extends VelocityBaseFeature<Meta> {

    public Announcer(ProxyFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public Map<String, Object> getDefaultConfig() {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("enabled", false);
        defaults.put("message_interval", 200);

        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> msg1 = new HashMap<>();
        msg1.put("message_key", "text1");
        messages.add(msg1);

        defaults.put("messages", messages);
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
