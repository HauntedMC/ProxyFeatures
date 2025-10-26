// src/main/java/nl/hauntedmc/proxyfeatures/features/proxyinfo/ProxyInfo.java
package nl.hauntedmc.proxyfeatures.features.proxyinfo;

import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.proxyfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.proxyfeatures.features.VelocityBaseFeature;
import nl.hauntedmc.proxyfeatures.features.proxyinfo.command.ProxyInfoCommand;
import nl.hauntedmc.proxyfeatures.features.proxyinfo.meta.Meta;

import java.time.Instant;

public class ProxyInfo extends VelocityBaseFeature<Meta> {
    private final Instant startTime;

    public ProxyInfo(ProxyFeatures plugin) {
        super(plugin, new Meta());
        this.startTime = Instant.now();
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messages = new MessageMap();
        messages.add("proxyinfo.cmd_usage", "&eUsage: /proxyinfo");
        messages.add("proxyinfo.cmd_header", "&eProxy Info:");
        messages.add("proxyinfo.cmd_entry", "  &f{setting}: &a{value}");
        return messages;
    }

    @Override
    public void initialize() {
        // register the /proxyinfo command
        getLifecycleManager()
                .getCommandManager()
                .registerFeatureCommand(new ProxyInfoCommand(this));
    }

    @Override
    public void disable() {
        // nothing special
    }

    /**
     * @return when this proxy instance started (for uptime calc)
     */
    public Instant getStartTime() {
        return startTime;
    }
}
