package nl.hauntedmc.proxyfeatures.features.motd;

import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.features.BaseFeature;
import nl.hauntedmc.proxyfeatures.features.motd.internal.MotdHandler;
import nl.hauntedmc.proxyfeatures.features.motd.listener.PingListener;
import nl.hauntedmc.proxyfeatures.features.motd.meta.Meta;
import nl.hauntedmc.proxyfeatures.localization.MessageMap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Motd extends BaseFeature<Meta> {

    private MotdHandler motdHandler;

    public Motd(ProxyFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public Map<String, Object> getDefaultConfig() {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("enabled", false);
        defaults.put("playerCountMultiplier", 1.0);
        defaults.put("motdline1", "                   <bold><dark_gray>[</bold><bold><aqua>Haunted</bold><bold><gold>MC</bold><bold><dark_gray>]</bold> <red>v1.21.4    ");
        defaults.put("motdline2",
                List.of(
                        "Hype",
                        "Hakken",
                        "Hardgaan",
                        "Hoogtepunten",
                        "Hongerig",
                        "Horizon",
                        "Haarscherp",
                        "Hemels",
                        "Heroïsch",
                        "Harmonieus",
                        "Heldhaftig",
                        "Heftig",
                        "Hardcore",
                        "Heilig",
                        "Heersen",
                        "Hoogstaand",
                        "Hecht",
                        "Humble",
                        "Helden",
                        "Herobrine",
                        "Hip",
                        "Home"
                )
        );
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        return new MessageMap();
    }

    @Override
    public void initialize() {
        this.motdHandler = new MotdHandler(this);
        getLifecycleManager().getListenerManager().registerListener(new PingListener(this));
    }

    @Override
    public void disable() {

    }

    public MotdHandler getMotdHandler() {
        return motdHandler;
    }
}
