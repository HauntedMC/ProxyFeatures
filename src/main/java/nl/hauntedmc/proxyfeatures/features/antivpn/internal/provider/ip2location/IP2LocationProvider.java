package nl.hauntedmc.proxyfeatures.features.antivpn.internal.provider.ip2location;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import nl.hauntedmc.proxyfeatures.features.antivpn.AntiVPN;
import nl.hauntedmc.proxyfeatures.features.antivpn.internal.IPCheckResult;
import nl.hauntedmc.proxyfeatures.features.antivpn.internal.provider.IPIntelligenceProvider;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Fix #1 + #5:
 * - Uses Java HttpClient async (no blocking I/O on login thread)
 * - Robust parsing / tolerant boolean decoding
 */
public final class IP2LocationProvider implements IPIntelligenceProvider {

    private final HttpClient client;

    private final String apiKey;
    private final long timeoutMillis;

    private IP2LocationProvider(String apiKey, long timeoutMillis) {
        this.apiKey = apiKey;
        this.timeoutMillis = timeoutMillis;

        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(500, timeoutMillis)))
                .version(HttpClient.Version.HTTP_2)
                .build();
    }

    public static IP2LocationProvider fromConfig(AntiVPN feature) {
        String key = feature.getConfigHandler().node("providers").get("ip2location").get("api_key").as(String.class, "");
        long timeout = feature.getConfigHandler().node("providers").get("ip2location").get("timeout_millis").as(Long.class, 2500L);

        if (key == null || key.isBlank()) {
            feature.getLogger().warn("Providers.ip2location.api_key is empty; disabling ip2location provider.");
            return null;
        }
        return new IP2LocationProvider(key.trim(), Math.max(500, timeout));
    }

    @Override
    public String id() {
        return "ip2location";
    }

    @Override
    public long timeoutMillis() {
        return timeoutMillis;
    }

    @Override
    public CompletableFuture<IPCheckResult> lookup(String ip, boolean needCountry, boolean needVpn) {
        // Build URL safely
        String url = "https://api.ip2location.io/?key=" + enc(apiKey) + "&ip=" + enc(ip);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMillis))
                .header("User-Agent", "ProxyFeatures-AntiVPN/2.0")
                .GET()
                .build();

        return client.sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(resp -> {
                    int code = resp.statusCode();
                    if (code < 200 || code >= 300) {
                        throw new RuntimeException("HTTP " + code);
                    }

                    String body = resp.body();
                    if (body == null || body.isBlank()) {
                        throw new RuntimeException("Empty response");
                    }

                    JsonObject json = JsonParser.parseString(body).getAsJsonObject();

                    // Detect API error payloads (best-effort)
                    if (json.has("error") || json.has("message")) {
                        String err = safeString(json.get("error"));
                        if (err.isBlank()) err = safeString(json.get("message"));
                        if (!err.isBlank()) throw new RuntimeException(err);
                    }

                    String country = safeString(json.get("country_code"));
                    if (!country.isBlank()) {
                        country = country.trim().toUpperCase(Locale.ROOT);
                        // Keep only plausible ISO2
                        if (country.length() != 2) country = "";
                    }

                    Boolean vpn = parseFlexibleBoolean(json.get("is_proxy"));

                    return IPCheckResult.of(country, vpn, id());
                });
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    private static String safeString(JsonElement el) {
        if (el == null || el.isJsonNull()) return "";
        try {
            return el.getAsString();
        } catch (Throwable t) {
            return "";
        }
    }

    private static Boolean parseFlexibleBoolean(JsonElement el) {
        if (el == null || el.isJsonNull()) return null;

        try {
            if (el.isJsonPrimitive()) {
                var p = el.getAsJsonPrimitive();
                if (p.isBoolean()) return p.getAsBoolean();
                if (p.isNumber()) return p.getAsInt() != 0;
                if (p.isString()) {
                    String s = p.getAsString().trim().toLowerCase(Locale.ROOT);
                    if (s.equals("true") || s.equals("yes") || s.equals("1")) return true;
                    if (s.equals("false") || s.equals("no") || s.equals("0")) return false;
                }
            }
        } catch (Throwable ignored) {}

        return null;
    }
}
