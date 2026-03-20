package nl.hauntedmc.proxyfeatures.features.antivpn.internal;

import nl.hauntedmc.proxyfeatures.features.antivpn.AntiVPN;
import nl.hauntedmc.proxyfeatures.features.antivpn.internal.PersistentIpCache.CacheHit;
import nl.hauntedmc.proxyfeatures.features.antivpn.internal.PersistentIpCache.Source;
import nl.hauntedmc.proxyfeatures.features.antivpn.internal.provider.ProviderChain;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class AntiVPNService {

    public enum PolicyDecision { ALLOW, DENY }

    public record LookupDebug(IPCheckResult result, String cacheSource) {}

    public record Evaluation(
            boolean allowed,
            String denyMessageKey,
            String countryUpper,
            Boolean vpn,
            String providerId,
            String cacheSource,
            String error
    ) {}

    private final AntiVPN feature;
    private final PersistentIpCache cache;
    private final ProviderChain providers;
    private final NotificationService notifications;
    private final MetricsCollector metrics;

    private volatile IpWhitelist whitelist;
    private final Set<String> allowedCountriesUpper;

    private final boolean useRegionCheck;
    private final boolean useVpnCheck;

    private final PolicyDecision onApiError;
    private final PolicyDecision regionUnknown;
    private final PolicyDecision vpnUnknown;

    public AntiVPNService(
            AntiVPN feature,
            PersistentIpCache cache,
            ProviderChain providers,
            NotificationService notifications,
            MetricsCollector metrics
    ) {
        this.feature = feature;
        this.cache = cache;
        this.providers = providers;
        this.notifications = notifications;
        this.metrics = metrics;

        this.whitelist = IpWhitelist.fromConfig(feature);

        this.useRegionCheck = feature.getConfigHandler().get("use_region_check", Boolean.class, true);
        this.useVpnCheck = feature.getConfigHandler().get("use_vpn_check", Boolean.class, true);

        List<String> allowed = feature.getConfigHandler().node("").get("allowed_countries").listOf(String.class);
        if (allowed == null) allowed = List.of();
        Set<String> tmp = new HashSet<>();
        for (String s : allowed) {
            if (s == null) continue;
            String up = s.trim().toUpperCase(Locale.ROOT);
            if (!up.isEmpty()) tmp.add(up);
        }
        this.allowedCountriesUpper = Collections.unmodifiableSet(tmp);

        this.onApiError = parsePolicy(feature.getConfigHandler().node("policy").get("on_api_error").as(String.class, "ALLOW"));
        this.regionUnknown = parsePolicy(feature.getConfigHandler().node("policy").get("region_unknown").as(String.class, "DENY"));
        this.vpnUnknown = parsePolicy(feature.getConfigHandler().node("policy").get("vpn_unknown").as(String.class, "ALLOW"));
    }

    /**
     * Called by command after live config updates.
     */
    public void refreshWhitelistFromConfig() {
        this.whitelist = IpWhitelist.fromConfig(feature);
    }

    public IpWhitelist getWhitelist() {
        return whitelist;
    }

    /**
     * Fix #1: No blocking network I/O on the login thread.
     * The listener runs async=true; here we do async provider calls + timeout.
     */
    public CompletableFuture<Evaluation> evaluate(String ip, String playerName) {
        metrics.incChecks();

        // Whitelist bypass
        if (whitelist.isWhitelisted(ip)) {
            metrics.incAllowed();
            return CompletableFuture.completedFuture(new Evaluation(true, null, "", null, "whitelist", "WHITELIST", null));
        }

        // If neither check is enabled
        if (!useRegionCheck && !useVpnCheck) {
            metrics.incAllowed();
            return CompletableFuture.completedFuture(new Evaluation(true, null, "", null, "disabled", "NONE", null));
        }

        // Cache + provider lookup
        CompletableFuture<IPCheckResult> fut = cache.getOrCompute(ip, () ->
                providers.lookup(ip, useRegionCheck, useVpnCheck)
        );

        // Hard timeout safeguard
        long maxMs = providers.maxTimeoutMillis() + 500L;
        if (maxMs < 1000) maxMs = 1000;

        return fut.orTimeout(maxMs, TimeUnit.MILLISECONDS)
                .handle((res, ex) -> {
                    if (ex != null || res == null) {
                        metrics.incErrors();

                        if (onApiError == PolicyDecision.DENY) {
                            return new Evaluation(false, "antivpn.error", "", null, "", "MISS", ex == null ? "unknown" : ex.getMessage());
                        }

                        // Fail-open (Fix #2) by default
                        metrics.incAllowed();
                        return new Evaluation(true, null, "", null, "", "MISS", ex == null ? "unknown" : ex.getMessage());
                    }

                    // Determine cache source for debug
                    String cacheSource = "MISS";
                    Optional<CacheHit> hit = cache.getIfPresent(ip);
                    if (hit.isPresent()) {
                        Source s = hit.get().source();
                        cacheSource = (s == Source.MEM) ? "MEM" : (s == Source.DISK ? "DISK" : "MISS");
                    }

                    String country = res.countryUpper();
                    Boolean vpn = res.vpn();
                    String provider = res.providerId() == null ? "" : res.providerId();

                    // Region check (Fix #7: unknown country no longer bypasses silently)
                    if (useRegionCheck) {
                        if (country.isBlank()) {
                            if (regionUnknown == PolicyDecision.DENY) {
                                metrics.incDeniedRegion();
                                notifications.notifyRegionUnknownBlocked(playerName);
                                return new Evaluation(false, "antivpn.blocked_region_unknown", "", vpn, provider, cacheSource, null);
                            }
                        } else if (!allowedCountriesUpper.isEmpty() && !allowedCountriesUpper.contains(country)) {
                            metrics.incDeniedRegion();
                            notifications.notifyRegionBlocked(playerName, country);
                            return new Evaluation(false, "antivpn.blocked_region", country, vpn, provider, cacheSource, null);
                        }
                    }

                    // VPN check
                    if (useVpnCheck) {
                        if (vpn == null) {
                            if (vpnUnknown == PolicyDecision.DENY) {
                                metrics.incDeniedVpn();
                                notifications.notifyVpnBlocked(playerName);
                                return new Evaluation(false, "antivpn.blocked_vpn", country, null, provider, cacheSource, null);
                            }
                        } else if (vpn) {
                            metrics.incDeniedVpn();
                            notifications.notifyVpnBlocked(playerName);
                            return new Evaluation(false, "antivpn.blocked_vpn", country, true, provider, cacheSource, null);
                        }
                    }

                    metrics.incAllowed();
                    return new Evaluation(true, null, country, vpn, provider, cacheSource, null);
                });
    }

    /**
     * Command helper: do a raw lookup and show what we got (country/vpn/provider/cache).
     */
    public CompletableFuture<LookupDebug> debugLookup(String ip, boolean needCountry, boolean needVpn) {
        Optional<CacheHit> hit = cache.getIfPresent(ip);
        return hit.map(cacheHit -> CompletableFuture.completedFuture(new LookupDebug(cacheHit.result(), cacheHit.source().name()))).orElseGet(() ->
                cache.getOrCompute(ip, () -> providers.lookup(ip, needCountry, needVpn))
                .thenApply(r -> new LookupDebug(r, "MISS")));
    }

    public PersistentIpCache getCache() {
        return cache;
    }

    private static PolicyDecision parsePolicy(String s) {
        if (s == null) return PolicyDecision.ALLOW;
        String up = s.trim().toUpperCase(Locale.ROOT);
        return "DENY".equals(up) ? PolicyDecision.DENY : PolicyDecision.ALLOW;
    }
}
