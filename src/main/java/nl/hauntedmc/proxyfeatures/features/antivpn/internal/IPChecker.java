package nl.hauntedmc.proxyfeatures.features.antivpn.internal;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import nl.hauntedmc.proxyfeatures.features.antivpn.AntiVPN;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

/**
 * Checks an IP address using the IP2Location API.
 */
public class IPChecker {

    private final AntiVPN feature;
    private final IPCheckCache cache = new IPCheckCache();
    private final Gson gson = new Gson();
    private final String apiKey;
    private final int timeout;

    public IPChecker(AntiVPN feature) {
        this.feature = feature;
        this.apiKey = (String) feature.getConfigHandler().getSetting("ip2location_api_key");
        this.timeout = (int) feature.getConfigHandler().getSetting("api_timeout");
    }

    /**
     * Checks the given IP. Uses a cache if available; otherwise, queries the external API.
     *
     * @param ip the IP address to check
     * @return an IPCheckResult containing the country code and proxy flag, or null if the check fails
     */
    public IPCheckResult check(String ip) {
        IPCheckResult cached = cache.get(ip);
        if (cached != null) {
            return cached;
        }

        String urlString = "https://api.ip2location.io/?key=" + apiKey + "&ip=" + ip;

        try {
            URL url = URI.create(urlString).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(timeout);
            connection.setReadTimeout(timeout);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
            connection.setRequestMethod("GET");

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                feature.getPlugin().getLogger().error("IP check failed for {}. Response code: {}", ip, responseCode);
                return null;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder responseBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                responseBuilder.append(line);
            }
            reader.close();

            String response = responseBuilder.toString();
            JsonObject json = gson.fromJson(response, JsonObject.class);

            // Extract the country code and is_proxy flag from the JSON response.
            String countryCode = json.has("country_code") ? json.get("country_code").getAsString() : "";
            boolean isProxy = json.has("is_proxy") && json.get("is_proxy").getAsBoolean();

            IPCheckResult result = new IPCheckResult(countryCode, isProxy, System.currentTimeMillis());
            cache.put(ip, result);
            return result;
        } catch (Exception e) {
            feature.getPlugin().getLogger().error("Error checking IP {}", ip, e);
            return null;
        }
    }
}
