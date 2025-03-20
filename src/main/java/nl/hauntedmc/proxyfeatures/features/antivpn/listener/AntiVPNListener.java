package nl.hauntedmc.proxyfeatures.features.antivpn.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.proxy.InboundConnection;
import nl.hauntedmc.proxyfeatures.common.util.CastUtils;
import nl.hauntedmc.proxyfeatures.features.antivpn.AntiVPN;
import nl.hauntedmc.proxyfeatures.features.antivpn.internal.IPCheckResult;
import nl.hauntedmc.proxyfeatures.features.antivpn.internal.IPChecker;

import java.net.InetSocketAddress;
import java.util.List;

public class AntiVPNListener {

    private final AntiVPN feature;
    private final IPChecker ipChecker;
    private final List<String> whitelist;
    private final List<String> allowedCountries;

    public AntiVPNListener(AntiVPN feature) {
        this.feature = feature;
        this.ipChecker = feature.getIPChecker();
        whitelist = CastUtils.safeCastToList(feature.getConfigHandler().getSetting("whitelist_ips"), String.class);
        allowedCountries = CastUtils.safeCastToList(feature.getConfigHandler().getSetting("allowed_countries"), String.class);
    }

    @Subscribe
    public void onPreLogin(PreLoginEvent event) {
        InboundConnection connection = event.getConnection();
        InetSocketAddress address = connection.getRemoteAddress();
        String ip = address.getAddress().getHostAddress();

        if (whitelist.contains(ip)) {
            return;
        }

        // Perform region check if enabled.
        boolean useRegionCheck = (boolean) feature.getConfigHandler().getSetting("use_region_check");
        if (useRegionCheck) {
            IPCheckResult result = ipChecker.check(ip);

            if (result == null) {
                event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                        feature.getLocalizationHandler().getMessage("antivpn.error", null)
                ));
                return;
            }
            if (!allowedCountries.contains(result.getCountryCode())) {
                event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                        feature.getLocalizationHandler().getMessage("antivpn.blocked_region", null)
                ));
                return;
            }
        }

        // Perform VPN/proxy check if enabled.
        boolean useVpnCheck = (boolean) feature.getConfigHandler().getSetting("use_vpn_check");
        if (useVpnCheck) {
            IPCheckResult result = ipChecker.check(ip);
            if (result == null) {
                event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                        feature.getLocalizationHandler().getMessage("antivpn.error", null)
                ));
                return;
            }
            if (result.isVpn()) {
                event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                        feature.getLocalizationHandler().getMessage("antivpn.blocked_vpn", null)
                ));
            }
        }
    }
}
