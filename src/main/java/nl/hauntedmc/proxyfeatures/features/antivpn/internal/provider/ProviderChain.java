package nl.hauntedmc.proxyfeatures.features.antivpn.internal.provider;

import nl.hauntedmc.proxyfeatures.features.antivpn.internal.IPCheckResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class ProviderChain {

    private final List<IPIntelligenceProvider> providers;
    private final long maxTimeoutMillis;

    public ProviderChain(List<IPIntelligenceProvider> providers) {
        this.providers = List.copyOf(providers);

        long m = 0;
        for (IPIntelligenceProvider p : providers) m = Math.max(m, p.timeoutMillis());
        this.maxTimeoutMillis = m;
    }

    public long maxTimeoutMillis() {
        return maxTimeoutMillis;
    }

    /**
     * Try providers in order until we have enough data.
     * Fix #B: Designed for easy extension; add new providers to ProviderRegistry.
     */
    public CompletableFuture<IPCheckResult> lookup(String ip, boolean needCountry, boolean needVpn) {
        if (providers.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalStateException("No AntiVPN providers configured."));
        }

        // sequential async chain
        CompletableFuture<Accum> f = CompletableFuture.completedFuture(new Accum());

        for (IPIntelligenceProvider p : providers) {
            f = f.thenCompose(acc -> {
                if (acc.hasEnough(needCountry, needVpn)) return CompletableFuture.completedFuture(acc);

                return p.lookup(ip, needCountry, needVpn)
                        .handle((res, ex) -> {
                            if (ex != null || res == null) {
                                acc.errors.add(p.id() + ":" + (ex == null ? "unknown" : ex.getMessage()));
                                return acc;
                            }

                            // Merge
                            if (acc.country == null || acc.country.isBlank()) {
                                String c = res.countryCode();
                                if (c != null && !c.isBlank()) acc.country = c;
                            }
                            if (acc.vpn == null) {
                                acc.vpn = res.vpn();
                            }

                            // Record provider used (best-effort: first provider that contributed)
                            if (acc.provider == null || acc.provider.isBlank()) {
                                acc.provider = res.providerId();
                                if (acc.provider == null || acc.provider.isBlank()) acc.provider = p.id();
                            }

                            return acc;
                        });
            });
        }

        return f.thenCompose(acc -> {
            if (!acc.hasAny()) {
                String msg = acc.errors.isEmpty() ? "all providers failed" : String.join("; ", acc.errors);
                return CompletableFuture.failedFuture(new RuntimeException(msg));
            }
            return CompletableFuture.completedFuture(IPCheckResult.of(acc.country == null ? "" : acc.country, acc.vpn, acc.provider));
        });
    }

    private static final class Accum {
        String country;
        Boolean vpn;
        String provider;
        final List<String> errors = new ArrayList<>();

        boolean hasAny() {
            return (country != null && !country.isBlank()) || vpn != null;
        }

        boolean hasEnough(boolean needCountry, boolean needVpn) {
            boolean okCountry = !needCountry || (country != null && !country.isBlank());
            boolean okVpn = !needVpn || vpn != null;
            return okCountry && okVpn;
        }
    }
}
