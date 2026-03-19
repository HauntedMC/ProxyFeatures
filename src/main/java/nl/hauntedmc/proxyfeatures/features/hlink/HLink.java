package nl.hauntedmc.proxyfeatures.features.hlink;

import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.proxyfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.proxyfeatures.features.VelocityBaseFeature;
import nl.hauntedmc.proxyfeatures.features.hlink.command.HLinkCommand;
import nl.hauntedmc.proxyfeatures.features.hlink.command.LinkCommand;
import nl.hauntedmc.proxyfeatures.features.hlink.command.RegisterCommand;
import nl.hauntedmc.proxyfeatures.features.hlink.internal.HLinkHandler;
import nl.hauntedmc.proxyfeatures.features.hlink.internal.hook.LuckPermsHook;
import nl.hauntedmc.proxyfeatures.features.hlink.meta.Meta;

public class HLink extends VelocityBaseFeature<Meta> {

    private HLinkHandler hlinkHandler;

    public HLink(ProxyFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);
        defaults.put("api-key", "<key>");
        defaults.put("website-url", "https://hauntedmc.nl");
        defaults.put("full-friendly-urls-enabled", true);
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messageMap = new MessageMap();
        messageMap.add("hlink.clickLink", "&eKlik hier om jouw account te linken");
        messageMap.add("hlink.errorAlreadyRegistered", "&cJe hebt al een account gelinked aan onze website!");
        messageMap.add("hlink.header", "&7&m------------&6&l[&b&lHLink&6&l]&7&m-----------");
        messageMap.add("hlink.errorAlreadyLinked", "&cJe hebt al een website account gelinked aan dit minecraft account.");
        messageMap.add("hlink.footer", "&7&m------------------------------");
        messageMap.add("hlink.errorCreatingKey", "&cFout bij linken van account. Maak een support ticket aan voor hulp.");
        messageMap.add("hlink.linkMessage", "&eJe account is succesvol gelinkt aan de website.");
        messageMap.add("hlink.syncUsage", "&cGebruik: /hlink sync <speler>");
        messageMap.add("hlink.syncNotOnline", "&cSpeler &6{player}&c niet gevonden.");
        messageMap.add("hlink.syncSuccess", "&aSpeler &6{player}&a gesynchroniseerd.");
        return messageMap;
    }

    @Override
    public void initialize() {
        this.hlinkHandler = new HLinkHandler(this);
        getLifecycleManager().getCommandManager().registerFeatureCommand(new LinkCommand(this));
        getLifecycleManager().getCommandManager().registerFeatureCommand(new RegisterCommand(this));
        getLifecycleManager().getCommandManager().registerFeatureCommand(new HLinkCommand(this));
        LuckPermsHook.subscribeLuckPermsHook(this);
    }

    @Override
    public void disable() {
        LuckPermsHook.unsubscribeLuckPermsHook();
        if (hlinkHandler != null) {
            hlinkHandler.shutdown();
        }
    }

    public HLinkHandler getHLinkHandler() {
        return hlinkHandler;
    }
}
