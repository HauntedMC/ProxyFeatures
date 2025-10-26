package nl.hauntedmc.proxyfeatures.features.antivpn;

import nl.hauntedmc.proxyfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.proxyfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.common.util.APIRegistry;
import nl.hauntedmc.proxyfeatures.features.VelocityBaseFeature;
import nl.hauntedmc.proxyfeatures.features.antivpn.api.CountryAPI;
import nl.hauntedmc.proxyfeatures.features.antivpn.listener.AntiVPNListener;
import nl.hauntedmc.proxyfeatures.features.antivpn.meta.Meta;
import nl.hauntedmc.proxyfeatures.features.antivpn.internal.CountryService;
import nl.hauntedmc.proxyfeatures.features.antivpn.internal.IPChecker;

import java.util.List;

/**
 * The AntiVPN feature checks incoming connections for allowed regions and VPN/proxy usage,
 * and exposes a CountryAPI to fetch a player's country code.
 */
public class AntiVPN extends VelocityBaseFeature<Meta> {

    private IPChecker ipChecker;

    public AntiVPN(ProxyFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
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
        CountryService countryService = new CountryService();

        // API: register in the proxy APIRegistry
        APIRegistry.register(CountryAPI.class, countryService);

        // Listener (uses ipChecker + countryService)
        getLifecycleManager().getListenerManager().registerListener(new AntiVPNListener(this, countryService));
    }

    @Override
    public void disable() {
        APIRegistry.unregister(CountryAPI.class);
    }

    public IPChecker getIPChecker() {
        return ipChecker;
    }
}
