package nl.hauntedmc.proxyfeatures.features.versioncheck.internal;

import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import nl.hauntedmc.proxyfeatures.features.versioncheck.VersionCheck;

public class VersionHandler {

    private final VersionCheck feature;
    private final int minimum_protocol_version;
    private final String friendly_protocol_name;

    public VersionHandler(VersionCheck feature) {
        this.feature = feature;
        minimum_protocol_version = (int) feature.getConfigHandler().getSetting("minimum_protocol_version");
        friendly_protocol_name = (String) feature.getConfigHandler().getSetting("friendly_protocol_name");
    }

    public void checkVersion(LoginEvent event) {
        Player player = event.getPlayer();
        int protocolVersion = player.getProtocolVersion().getProtocol();
        if (isAllowedVersion(protocolVersion)) {
            event.setResult(LoginEvent.ComponentResult.denied(
                    Component.join(
                            JoinConfiguration.separator(Component.text(" ")),
                            Component.text("Verbinding verbroken:", NamedTextColor.RED),
                            feature.getLocalizationHandler().getMessage("versioncheck.unsupported_version")
                                    .forAudience(player)
                                    .with("friendly_protocol_name", friendly_protocol_name)
                                    .build()
                    )
            ));
        }
    }

    public boolean isAllowedVersion(int protocolVersion) {
        return protocolVersion < minimum_protocol_version;
    }

    public int getMinimumProtcolVersion() {
        return minimum_protocol_version;
    }

    public String getFriendlyProtocolName() {
        return friendly_protocol_name;
    }

}
