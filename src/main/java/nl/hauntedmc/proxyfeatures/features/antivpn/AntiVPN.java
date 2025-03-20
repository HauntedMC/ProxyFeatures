package nl.hauntedmc.proxyfeatures.features.antivpn;

import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.features.BaseFeature;
import nl.hauntedmc.proxyfeatures.features.antivpn.listener.AntiVPNListener;
import nl.hauntedmc.proxyfeatures.features.antivpn.meta.Meta;
import nl.hauntedmc.proxyfeatures.features.antivpn.internal.IPChecker;
import nl.hauntedmc.proxyfeatures.localization.MessageMap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The AntiVPN feature checks incoming connections for allowed regions and VPN/proxy usage.
 */
public class AntiVPN extends BaseFeature<Meta> { // Using Object for meta; adjust as needed.

    private IPChecker ipChecker;

    public AntiVPN(ProxyFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public Map<String, Object> getDefaultConfig() {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("enabled", false);
        defaults.put("use_region_check", true);
        defaults.put("use_vpn_check", true);
        defaults.put("allowed_countries", List.of("US", "CA", "GB")); // Allowed ISO country codes.
        defaults.put("ip2location_api_key", ""); // Default API key is empty.
        defaults.put("whitelist_ips", List.of()); // List of IPs to bypass checks.
        defaults.put("api_timeout", 5000); // Timeout in milliseconds.
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messages = new MessageMap();
        messages.add("antivpn.blocked_region", "&7Connection blocked: &cCountry/Region is not allowed&7.<newline><newline>&8If this is wrong please contact support at https://hauntedmc.nl/support");
        messages.add("antivpn.blocked_vpn", "&7Connection blocked: &cVPN/Proxy is not allowed&7.<newline><newline>&8If this is wrong please contact support at https://hauntedmc.nl/support");
        messages.add("antivpn.error", "&7Connection blocked: There was an error verifying your connection. Please try again later.");
        messages.add("antivpn.notify_region", "&7[&cAntiVPN&7] &f{player} &fprobeerde in te loggen vanuit een &cgeblokkeerde regio ({country})&f en is geblokkeerd.");
        messages.add("antivpn.notify_vpn", "&7[&cAntiVPN&7] &f{player} &fprobeerde in te loggen met een &cVPN/Proxy&f en is geblokkeerd.");
        return messages;
    }

    @Override
    public void initialize() {
        this.ipChecker = new IPChecker(this);
        getLifecycleManager().getListenerManager().registerListener(new AntiVPNListener(this));
    }

    @Override
    public void disable() {
    }

    public IPChecker getIPChecker() {
        return ipChecker;
    }
}
