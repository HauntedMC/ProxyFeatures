package nl.hauntedmc.proxyfeatures.features.antivpn.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.proxy.InboundConnection;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.proxyfeatures.features.antivpn.AntiVPN;
import nl.hauntedmc.proxyfeatures.features.antivpn.internal.AntiVPNService;
import nl.hauntedmc.proxyfeatures.features.antivpn.internal.CountryService;

import java.net.InetSocketAddress;
import java.util.Locale;
import java.util.UUID;

/**
 * Fix #1: event handler is async so no blocking I/O on login thread.
 */
public final class AntiVPNListener {

    private final AntiVPN feature;
    private final AntiVPNService service;
    private final CountryService countryService;

    public AntiVPNListener(AntiVPN feature, AntiVPNService service, CountryService countryService) {
        this.feature = feature;
        this.service = service;
        this.countryService = countryService;
    }

    @Subscribe(priority = 20, async = true)
    public void onPreLogin(PreLoginEvent event) {
        InboundConnection connection = event.getConnection();
        InetSocketAddress address = connection.getRemoteAddress();
        if (address == null) {
            return;
        }
        String ip = address.getAddress() != null
                ? address.getAddress().getHostAddress()
                : address.getHostString();
        if (ip == null || ip.isBlank()) {
            return;
        }
        String playerName = event.getUsername();

        AntiVPNService.Evaluation eval;
        try {
            eval = service.evaluate(ip, playerName).join();
        } catch (Exception ex) {
            feature.getLogger().warn("[AntiVPN] Pre-login evaluation failed for '" + playerName + "': " + ex.getMessage());
            return;
        }

        if (!eval.allowed()) {
            Component msg = feature.getLocalizationHandler()
                    .getMessage(eval.denyMessageKey() == null ? "antivpn.error" : eval.denyMessageKey())
                    .build();

            event.setResult(PreLoginEvent.PreLoginComponentResult.denied(msg));
            return;
        }

        // Stage country if known (Fix #6 handled by TTL-based staging)
        String country = eval.countryUpper();
        if (country != null && !country.isBlank() && playerName != null && !playerName.isBlank()) {
            countryService.stageForUsername(playerName, country.toUpperCase(Locale.ROOT));
        }
    }

    @Subscribe(priority = 20)
    public void onPostLogin(PostLoginEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        String name = event.getPlayer().getUsername();
        countryService.promoteToUuid(name, uuid);
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        countryService.clear(event.getPlayer().getUniqueId());
    }
}
