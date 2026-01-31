package nl.hauntedmc.proxyfeatures.features.antivpn;

import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.api.APIRegistry;
import nl.hauntedmc.proxyfeatures.api.io.cache.CacheDirectory;
import nl.hauntedmc.proxyfeatures.api.io.cache.CacheType;
import nl.hauntedmc.proxyfeatures.api.io.cache.FileCacheStore;
import nl.hauntedmc.proxyfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.proxyfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.proxyfeatures.features.VelocityBaseFeature;
import nl.hauntedmc.proxyfeatures.features.antivpn.api.CountryAPI;
import nl.hauntedmc.proxyfeatures.features.antivpn.command.AntiVPNCommand;
import nl.hauntedmc.proxyfeatures.features.antivpn.internal.AntiVPNService;
import nl.hauntedmc.proxyfeatures.features.antivpn.internal.CountryService;
import nl.hauntedmc.proxyfeatures.features.antivpn.internal.MetricsCollector;
import nl.hauntedmc.proxyfeatures.features.antivpn.internal.NotificationService;
import nl.hauntedmc.proxyfeatures.features.antivpn.internal.PersistentIpCache;
import nl.hauntedmc.proxyfeatures.features.antivpn.internal.provider.ProviderChain;
import nl.hauntedmc.proxyfeatures.features.antivpn.internal.provider.ProviderRegistry;
import nl.hauntedmc.proxyfeatures.features.antivpn.listener.AntiVPNListener;
import nl.hauntedmc.proxyfeatures.features.antivpn.meta.Meta;

import java.time.Duration;
import java.util.List;

public class AntiVPN extends VelocityBaseFeature<Meta> {

    private CountryService countryService;
    private MetricsCollector metrics;
    private PersistentIpCache cache;
    private AntiVPNService service;

    public AntiVPN(ProxyFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();

        defaults.put("enabled", false);

        // Checks
        defaults.put("use_region_check", true);
        defaults.put("use_vpn_check", true);
        defaults.put("allowed_countries",  List.of("US", "CA", "GB", "AU", "NZ", "DE", "FR", "NL", "BE", "IE", "IT", "ES", "SE", "DK", "NO", "FI", "AT", "LU"));

        // Policies
        defaults.put("policy.on_api_error", "ALLOW");          // ALLOW | DENY
        defaults.put("policy.region_unknown", "DENY");         // ALLOW | DENY
        defaults.put("policy.vpn_unknown", "ALLOW");           // ALLOW | DENY

        // Whitelist - supports IP + CIDR + optional private-range auto bypass
        defaults.put("whitelist.allow_private_ranges", false);
        defaults.put("whitelist.entries", List.of());

        // Providers - ordered chain
        defaults.put("providers.order", List.of("proxycheck", "ip2location"));
        defaults.put("providers.ip2location.enabled", true);
        defaults.put("providers.ip2location.api_key", "");
        defaults.put("providers.ip2location.timeout_millis", 2500);

        defaults.put("providers.proxycheck.enabled", true);
        defaults.put("providers.proxycheck.api_key", "");
        defaults.put("providers.proxycheck.base_url", "https://proxycheck.io/v3/");
        defaults.put("providers.proxycheck.api_version", "20-November-2025"); // optional pin
        defaults.put("providers.proxycheck.days", 0); // optional, 0 = no &days flag
        defaults.put("providers.proxycheck.timeout_millis", 2500);
        defaults.put("providers.proxycheck.strict", true);
        defaults.put("providers.proxycheck.risk_threshold", 1); // 1 = any risk > 0 blocks
        defaults.put("providers.proxycheck.min_confidence", 0); // 0 = ignore confidence

        // Cache
        defaults.put("cache.enabled", true);
        defaults.put("cache.ttl_millis", Duration.ofDays(30).toMillis());
        defaults.put("cache.max_entries", 50_000);
        defaults.put("cache.persist", true); // persist across restarts

        // Username staging TTL
        defaults.put("staging.username_ttl_seconds", 600);

        // Notifications
        defaults.put("notify.cooldown_seconds", 30);

        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messages = new MessageMap();

        /* ===================== Player-facing disconnect/kick (EN) ===================== */
        messages.add("antivpn.blocked_region",
                "&7Connection blocked: &cYour country/region is not allowed&7.<newline><newline>&8If this is wrong please contact support at https://hauntedmc.nl/support");
        messages.add("antivpn.blocked_region_unknown",
                "&7Connection blocked: &cWe could not determine your country/region&7.<newline><newline>&8If this is wrong please contact support at https://hauntedmc.nl/support");
        messages.add("antivpn.blocked_vpn",
                "&7Connection blocked: &cVPN/Proxy connections are not allowed&7.<newline><newline>&8If this is wrong please contact support at https://hauntedmc.nl/support");
        messages.add("antivpn.error",
                "&7We couldn't verify your connection right now. Please try again later.");

        /* ===================== Staff notifications (NL) ===================== */
        messages.add("antivpn.notify_region",
                "&7[&cAntiVPN&7] &f{player} &fprobeerde in te loggen vanuit een &cgeblokkeerde regio ({country})&f en is geblokkeerd.");
        messages.add("antivpn.notify_region_unknown",
                "&7[&cAntiVPN&7] &f{player} &fprobeerde in te loggen maar het land was &conbekend&f en is geblokkeerd.");
        messages.add("antivpn.notify_vpn",
                "&7[&cAntiVPN&7] &f{player} &fprobeerde in te loggen met een &cVPN/Proxy&f en is geblokkeerd.");

        /* ===================== Command UX (NL) ===================== */
        messages.add("antivpn.command.usage",
                "&7Gebruik: &f/antivpn <stats|check|whitelist|cache>");

        messages.add("antivpn.command.stats",
                "&7[&cAntiVPN&7] checks={checks}, toegestaan={allowed}, geblokkeerd_regio={denied_region}, geblokkeerd_vpn={denied_vpn}, errors={errors}, cache_mem_hits={cache_mem_hits}, cache_disk_hits={cache_disk_hits}");

        messages.add("antivpn.command.check.result",
                "&7[&cAntiVPN&7] &f{ip}&7 -> land=&f{country}&7, vpn=&f{vpn}&7, provider=&f{provider}&7, cache=&f{cache}");
        messages.add("antivpn.command.check.error",
                "&7[&cAntiVPN&7] &cCheck mislukt:&7 {error}");

        messages.add("antivpn.command.whitelist.added",
                "&7[&cAntiVPN&7] &aToegevoegd&7 aan whitelist: &f{entry}");
        messages.add("antivpn.command.whitelist.removed",
                "&7[&cAntiVPN&7] &aVerwijderd&7 uit whitelist: &f{entry}");
        messages.add("antivpn.command.whitelist.already",
                "&7[&cAntiVPN&7] &eStaat al in de whitelist:&7 {entry}");
        messages.add("antivpn.command.whitelist.not_found",
                "&7[&cAntiVPN&7] &cNiet gevonden in de whitelist:&7 {entry}");
        messages.add("antivpn.command.whitelist.list",
                "&7[&cAntiVPN&7] Whitelist entries (&f{count}&7): &f{entries}");

        messages.add("antivpn.command.cache.cleared",
                "&7[&cAntiVPN&7] &aCache geleegd.");
        messages.add("antivpn.command.cache.stats",
                "&7[&cAntiVPN&7] cache_mem_size={mem_size}, cache_disk_entries={disk_entries}, inflight={inflight}");

        return messages;
    }

    @Override
    public void initialize() {
        this.metrics = new MetricsCollector();

        long userTtlSeconds = getConfigHandler().node("staging").get("username_ttl_seconds").as(Long.class, 600L);
        this.countryService = new CountryService(Duration.ofSeconds(Math.max(10, userTtlSeconds)));

        NotificationService notifications = new NotificationService(this);

        // Persistent cache file (Fix #C)
        CacheDirectory dir = getLifecycleManager().getCacheManager().getCacheDirectory(getFeatureName(), "ip");
        FileCacheStore store = (FileCacheStore) dir.getStore("ip_checks", CacheType.JSON);

        this.cache = new PersistentIpCache(this, store, metrics);

        // Providers chain (Fix #B)
        ProviderChain providerChain = ProviderRegistry.buildChain(this);

        // Service orchestrator
        this.service = new AntiVPNService(this, cache, providerChain, notifications, metrics);

        // API
        APIRegistry.register(CountryAPI.class, countryService);

        // Listener
        getLifecycleManager().getListenerManager().registerListener(new AntiVPNListener(this, service, countryService));

        // Command (Fix #A + whitelist live editing)
        getLifecycleManager().getCommandManager().registerBrigadierCommand(new AntiVPNCommand(this));
    }

    @Override
    public void disable() {
        APIRegistry.unregister(CountryAPI.class);
        if (cache != null) cache.close();
    }

    public CountryService getCountryService() {
        return countryService;
    }

    public AntiVPNService getService() {
        return service;
    }

    public MetricsCollector getMetrics() {
        return metrics;
    }
}
