package nl.hauntedmc.proxyfeatures.features.antivpn.internal.provider;

import nl.hauntedmc.proxyfeatures.features.antivpn.internal.IPCheckResult;

import java.util.concurrent.CompletableFuture;

public interface IPIntelligenceProvider {
    String id();
    long timeoutMillis();
    CompletableFuture<IPCheckResult> lookup(String ip, boolean needCountry, boolean needVpn);
}
