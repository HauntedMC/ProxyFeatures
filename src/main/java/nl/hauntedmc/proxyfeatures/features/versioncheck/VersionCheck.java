package nl.hauntedmc.proxyfeatures.features.versioncheck;

import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.features.BaseFeature;
import nl.hauntedmc.proxyfeatures.features.versioncheck.listener.ConnectionListener;
import nl.hauntedmc.proxyfeatures.features.versioncheck.meta.Meta;
import nl.hauntedmc.proxyfeatures.localization.MessageMap;

import java.util.HashMap;
import java.util.Map;

public class VersionCheck extends BaseFeature<Meta> {

    public VersionCheck(ProxyFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public Map<String, Object> getDefaultConfig() {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("enabled", false);
        defaults.put("minimum_protocol_version", 763);
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messageMap = new MessageMap();
        messageMap.add("versioncheck.unsupported_version", "&cOp HauntedMC kun je spelen op versie 1.20 of hoger.");
        return messageMap;
    }

    @Override
    public void initialize() {
        getLifecycleManager().getListenerManager().registerListener(new ConnectionListener(this));
    }

    @Override
    public void disable() {

    }

    public void checkVersion(LoginEvent event) {
        Player player = event.getPlayer();
        int minimum_protocol = (int) getConfigHandler().getSetting("minimum_protocol_version");
        int protocolVersion = player.getProtocolVersion().getProtocol();
        if (protocolVersion < minimum_protocol) {
            event.setResult(LoginEvent.ComponentResult.denied(
                    Component.join(
                            JoinConfiguration.separator(Component.text(" ")),
                            Component.text("Verbinding verbroken:", NamedTextColor.RED),
                            getLocalizationHandler().getMessage("versioncheck.unsupported_version", player)
                    )
            ));
        }
    }
}
