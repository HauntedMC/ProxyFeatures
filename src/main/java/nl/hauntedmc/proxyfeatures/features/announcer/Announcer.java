package nl.hauntedmc.proxyfeatures.features.announcer;

import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.proxyfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.proxyfeatures.features.VelocityBaseFeature;
import nl.hauntedmc.proxyfeatures.features.announcer.internal.AnnouncerHandler;
import nl.hauntedmc.proxyfeatures.features.announcer.meta.Meta;

import java.util.List;

public class Announcer extends VelocityBaseFeature<Meta> {

    private volatile AnnouncerHandler handler;

    public Announcer(ProxyFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);

        // Timing
        defaults.put("message_interval", 200);     // seconds
        defaults.put("initial_delay", 30);         // seconds (prevents instant spam on enable/reload)

        // Mode: SEQUENTIAL | SHUFFLE | WEIGHTED_RANDOM
        defaults.put("mode", "SHUFFLE");

        // Audience filters
        defaults.put("audience.permission", "");               // empty = no permission required
        defaults.put("audience.servers", List.of());           // allowlist; empty = all servers
        defaults.put("audience.exclude_servers", List.of());   // denylist

        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messages = new MessageMap();
        messages.add("announcer.default", "&6Announcement Bericht");
        return messages;
    }

    @Override
    public void initialize() {
        this.handler = new AnnouncerHandler(this);
        this.handler.start();
    }

    @Override
    public void disable() {
        AnnouncerHandler h = this.handler;
        this.handler = null;
        if (h != null) {
            h.stop();
        }
    }
}
