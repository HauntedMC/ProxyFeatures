package nl.hauntedmc.proxyfeatures.features.antivpn.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.proxy.InboundConnection;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.proxyfeatures.api.util.type.CastUtils;
import nl.hauntedmc.proxyfeatures.features.antivpn.AntiVPN;
import nl.hauntedmc.proxyfeatures.features.antivpn.internal.CountryService;
import nl.hauntedmc.proxyfeatures.features.antivpn.internal.IPCheckResult;
import nl.hauntedmc.proxyfeatures.features.antivpn.internal.IPChecker;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

public class AntiVPNListener {

    private final AntiVPN feature;
    private final IPChecker ipChecker;
    private final CountryService countryService;
    private final List<String> whitelist;
    private final List<String> allowedCountriesUpper;
    private final boolean useRegionCheck;
    private final boolean useVpnCheck;

    public AntiVPNListener(AntiVPN feature, CountryService countryService) {
        this.feature = feature;
        this.ipChecker = feature.getIPChecker();
        this.countryService = countryService;

        this.whitelist = CastUtils.safeCastToList(feature.getConfigHandler().get("whitelist_ips"), String.class);
        List<String> allowed = CastUtils.safeCastToList(feature.getConfigHandler().get("allowed_countries"), String.class);
        this.allowedCountriesUpper = allowed.stream().map(s -> s.toUpperCase(Locale.ROOT)).collect(Collectors.toList());
        this.useRegionCheck = (boolean) feature.getConfigHandler().get("use_region_check");
        this.useVpnCheck = (boolean) feature.getConfigHandler().get("use_vpn_check");
    }

    @Subscribe
    public void onPreLogin(PreLoginEvent event) {
        InboundConnection connection = event.getConnection();
        InetSocketAddress address = connection.getRemoteAddress();
        String ip = address.getAddress().getHostAddress();

        // Whitelisted IPs bypass checks
        if (whitelist.contains(ip)) {
            return;
        }

        // If neither check is enabled, do nothing
        if (!useRegionCheck && !useVpnCheck) {
            return;
        }

        // Single lookup to avoid duplicate API calls
        IPCheckResult result = ipChecker.check(ip);
        if (result == null) {
            event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                    feature.getLocalizationHandler().getMessage("antivpn.error").build()
            ));
            return;
        }

        String playerName = event.getUsername();
        String country = result.countryCode() == null ? "" : result.countryCode().toUpperCase(Locale.ROOT);

        // Region policy
        if (useRegionCheck && !country.isBlank() && !allowedCountriesUpper.contains(country)) {
            // Notify staff about a region block.
            Component notifyMessage = feature.getLocalizationHandler().getMessage("antivpn.notify_region")
                    .with("player", playerName)
                    .with("country", country)
                    .build();
            notifyStaff(notifyMessage);

            event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                    feature.getLocalizationHandler().getMessage("antivpn.blocked_region").build()
            ));
            return;
        }

        // VPN policy
        if (useVpnCheck && result.vpn()) {
            // Notify staff about a VPN/proxy block.
            Component notifyMessage = feature.getLocalizationHandler().getMessage("antivpn.notify_vpn")
                    .with("player", playerName)
                    .build();
            notifyStaff(notifyMessage);

            event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                    feature.getLocalizationHandler().getMessage("antivpn.blocked_vpn").build()
            ));
            return;
        }

        // At this point, login will proceed — stage the country by username.
        if (!country.isBlank()) {
            countryService.stageForUsername(playerName, country);
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

    private void notifyStaff(Component message) {
        feature.getPlugin().getProxy().getAllPlayers().forEach(player -> {
            if (player.hasPermission("proxyfeatures.feature.antivpn.notify")) {
                player.sendMessage(message);
            }
        });
    }
}
