package nl.hauntedmc.proxyfeatures.features.antivpn.internal.provider;

import nl.hauntedmc.proxyfeatures.features.antivpn.AntiVPN;
import nl.hauntedmc.proxyfeatures.features.antivpn.internal.provider.ip2location.IP2LocationProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Fix #B: centralized provider construction; adding a provider means:
 * - create a new IPIntelligenceProvider implementation
 * - register it here (switch)
 * - document config keys under providers.<id>.*
 */
public final class ProviderRegistry {

    private ProviderRegistry() {}

    public static ProviderChain buildChain(AntiVPN feature) {
        List<String> order = feature.getConfigHandler().node("providers").get("order").listOf(String.class);
        if (order == null) order = List.of("ip2location");

        List<IPIntelligenceProvider> out = new ArrayList<>();

        for (String raw : order) {
            if (raw == null || raw.isBlank()) continue;
            String id = raw.trim().toLowerCase(Locale.ROOT);

            boolean enabled = feature.getConfigHandler().node("providers").get(id).get("enabled").as(Boolean.class, true);
            if (!enabled) continue;

            switch (id) {
                case "ip2location" -> {
                    IP2LocationProvider p = IP2LocationProvider.fromConfig(feature);
                    if (p != null) out.add(p);
                }
                default -> feature.getLogger().warn("Unknown provider id in providers.order: " + id);
            }
        }

        if (out.isEmpty()) {
            feature.getLogger().warn("No providers enabled; lookups will fail (or be fail-open depending on policy).");
        }

        return new ProviderChain(out);
    }
}
