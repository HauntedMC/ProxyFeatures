package nl.hauntedmc.proxyfeatures.features.antivpn.internal.provider.proxycheck;

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
 * ProxyCheck v3 provider:
 * - Country: location.country_code
 * - "VPN/Proxy": computed from detections (very strict options supported)
 * Endpoint (v3):
 *   https://proxycheck.io/v3/<ip>?key=<token>[&ver=...][&days=...]
 */
public final class ProxyCheckProvider implements IPIntelligenceProvider {

    private final HttpClient client;

    private final String apiKey;
    private final String baseUrl;     // should end with "/"
    private final String apiVersion;  // optional &ver=...
    private final int days;           // optional &days=...
    private final long timeoutMillis;

    // Strictness controls
    private final boolean strict;          // if true, treat hosting/anonymous/etc as proxy too
    private final int riskThreshold;       // if >0, risk >= threshold => proxy=true (very strict default: 1)
    private final int minConfidence;       // if >0 and confidence < min => return vpn=null (unknown)

    private ProxyCheckProvider(
            String apiKey,
            String baseUrl,
            String apiVersion,
            int days,
            long timeoutMillis,
            boolean strict,
            int riskThreshold,
            int minConfidence
    ) {
        this.apiKey = apiKey;
        this.baseUrl = normalizeBase(baseUrl);
        this.apiVersion = apiVersion == null ? "" : apiVersion.trim();
        this.days = Math.max(0, days);
        this.timeoutMillis = Math.max(500L, timeoutMillis);

        this.strict = strict;
        this.riskThreshold = Math.max(0, riskThreshold);
        this.minConfidence = Math.max(0, minConfidence);

        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(this.timeoutMillis))
                .version(HttpClient.Version.HTTP_2)
                .build();
    }

    public static ProxyCheckProvider fromConfig(AntiVPN feature) {
        var node = feature.getConfigHandler().node("providers").get("proxycheck");

        boolean enabled = node.get("enabled").as(Boolean.class, true);
        if (!enabled) return null;

        String key = node.get("api_key").as(String.class, "");
        if (key == null || key.isBlank()) {
            feature.getLogger().warn("Providers.proxycheck.api_key is empty; disabling proxycheck provider.");
            return null;
        }

        String baseUrl = node.get("base_url").as(String.class, "https://proxycheck.io/v3/");
        long timeout = node.get("timeout_millis").as(Long.class, 2500L);

        // Optional: pin v3 version for stability (ProxyCheck docs mention &ver=...).
        String ver = node.get("api_version").as(String.class, "20-November-2025");

        // Optional: extend TTL behaviour (docs mention &days=... exists in v3).
        int days = node.get("days").as(Integer.class, 0);

        // Strictness knobs
        boolean strict = node.get("strict").as(Boolean.class, true);

        // Very strict default: any non-zero risk blocks (0 = clean).
        int riskThreshold = node.get("risk_threshold").as(Integer.class, 1);

        // If you want to reduce false positives: set e.g. 85/90. 0 disables.
        int minConfidence = node.get("min_confidence").as(Integer.class, 0);

        return new ProxyCheckProvider(
                key.trim(),
                baseUrl,
                ver,
                days,
                timeout,
                strict,
                riskThreshold,
                minConfidence
        );
    }

    @Override
    public String id() {
        return "proxycheck";
    }

    @Override
    public long timeoutMillis() {
        return timeoutMillis;
    }

    @Override
    public CompletableFuture<IPCheckResult> lookup(String ip, boolean needCountry, boolean needVpn) {
        String url = buildUrl(ip);

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
                    if (body == null || body.isBlank()) throw new RuntimeException("Empty response");

                    JsonObject root = JsonParser.parseString(body).getAsJsonObject();

                    String status = safeString(root.get("status")).toLowerCase(Locale.ROOT);
                    if (!status.equals("ok") && !status.equals("warning")) {
                        String msg = safeString(root.get("message"));
                        if (msg.isBlank()) msg = "proxycheck status=" + status;
                        throw new RuntimeException(msg);
                    }

                    JsonObject ipObj = pickIpObject(root, ip);
                    if (ipObj == null) {
                        throw new RuntimeException("Missing result object for IP");
                    }

                    String country = "";
                    if (needCountry) {
                        JsonObject loc = asObject(ipObj.get("location"));
                        if (loc != null) {
                            country = safeString(loc.get("country_code")).trim().toUpperCase(Locale.ROOT);
                            if (country.length() != 2) country = "";
                        }
                    }

                    Boolean vpn = null;
                    if (needVpn) {
                        JsonObject det = asObject(ipObj.get("detections"));
                        vpn = computeVpn(det);
                    }

                    return IPCheckResult.of(country, vpn, id());
                });
    }

    /* ============================ Logic ============================ */

    private Boolean computeVpn(JsonObject detections) {
        if (detections == null) return null;

        int confidence = safeInt(detections.get("confidence"), -1);
        if (minConfidence > 0 && confidence >= 0 && confidence < minConfidence) {
            // Treat as unknown so your AntiVPN policy (vpn_unknown) decides.
            return null;
        }

        boolean proxy = bool(detections.get("proxy"));
        boolean vpn = bool(detections.get("vpn"));
        boolean tor = bool(detections.get("tor"));
        boolean compromised = bool(detections.get("compromised"));
        boolean scraper = bool(detections.get("scraper"));

        boolean hosting = bool(detections.get("hosting"));
        boolean anonymous = bool(detections.get("anonymous"));

        int risk = safeInt(detections.get("risk"), -1);

        boolean detected = proxy || vpn || tor || compromised || scraper;

        // "Very strict": include hosting + anonymous too.
        if (strict) detected = detected || hosting || anonymous;

        // Optional: treat any (or high) risk as proxy/vpn.
        if (riskThreshold > 0 && risk >= riskThreshold) detected = true;

        return detected;
    }

    /* ============================ URL + JSON helpers ============================ */

    private String buildUrl(String ip) {
        // v3 expects the IP in the path segment; encode to support IPv6 safely.
        StringBuilder sb = new StringBuilder();
        sb.append(baseUrl).append(encPath(ip)).append("?key=").append(encQuery(apiKey));

        if (!apiVersion.isBlank()) {
            sb.append("&ver=").append(encQuery(apiVersion));
        }
        if (days > 0) {
            sb.append("&days=").append(days);
        }

        return sb.toString();
    }

    private static String normalizeBase(String baseUrl) {
        String b = (baseUrl == null || baseUrl.isBlank()) ? "https://proxycheck.io/v3/" : baseUrl.trim();
        if (!b.endsWith("/")) b += "/";
        return b;
    }

    private static String encQuery(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    private static String encPath(String s) {
        // URLEncoder is query-oriented but is fine here since IP strings have no spaces;
        // it percent-encodes IPv6 ":" which is safe in the path segment.
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String safeString(JsonElement el) {
        if (el == null || el.isJsonNull()) return "";
        try {
            return el.getAsString();
        } catch (Throwable t) {
            return "";
        }
    }

    private static int safeInt(JsonElement el, int def) {
        if (el == null || el.isJsonNull()) return def;
        try {
            if (el.isJsonPrimitive()) {
                var p = el.getAsJsonPrimitive();
                if (p.isNumber()) return p.getAsInt();
                if (p.isString()) {
                    String s = p.getAsString().trim();
                    if (s.isEmpty()) return def;
                    return Integer.parseInt(s);
                }
            }
        } catch (Throwable ignored) {}
        return def;
    }

    private static boolean bool(JsonElement el) {
        if (el == null || el.isJsonNull()) return false;
        try {
            if (el.isJsonPrimitive()) {
                var p = el.getAsJsonPrimitive();
                if (p.isBoolean()) return p.getAsBoolean();
                if (p.isNumber()) return p.getAsInt() != 0;
                if (p.isString()) {
                    String s = p.getAsString().trim().toLowerCase(Locale.ROOT);
                    return s.equals("true") || s.equals("yes") || s.equals("1");
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static JsonObject asObject(JsonElement el) {
        if (el == null || el.isJsonNull()) return null;
        try {
            return el.getAsJsonObject();
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * v3 response key for the IP is typically exactly the IP string,
     * but we fall back to "first object whose key looks like an ip".
     */
    private static JsonObject pickIpObject(JsonObject root, String ip) {
        if (root == null) return null;
        if (ip != null && root.has(ip) && root.get(ip).isJsonObject()) {
            return root.getAsJsonObject(ip);
        }

        for (var e : root.entrySet()) {
            String k = e.getKey();
            if (k == null) continue;
            if (!(k.contains(".") || k.contains(":"))) continue; // rough "looks like ip"
            if (e.getValue() != null && e.getValue().isJsonObject()) {
                return e.getValue().getAsJsonObject();
            }
        }

        return null;
    }
}
