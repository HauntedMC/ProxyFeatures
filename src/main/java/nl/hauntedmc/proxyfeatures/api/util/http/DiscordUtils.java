package nl.hauntedmc.proxyfeatures.api.util.http;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class DiscordUtils {


    /**
     * Sends the provided JSON payload to the specified Discord webhook URL.
     *
     * @param webhookUrl The webhook URL.
     * @param payload    The JSON payload.
     */
    public static boolean sendPayload(String webhookUrl, String payload) {
        HttpURLConnection connection = null;
        try {
            if (webhookUrl == null || webhookUrl.isBlank()) {
                return false;
            }

            URI uri = URI.create(webhookUrl.trim());
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("https") && !scheme.equalsIgnoreCase("http"))) {
                return false;
            }

            URL url = uri.toURL();
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setDoOutput(true);

            try (OutputStream os = connection.getOutputStream()) {
                String data = payload == null ? "" : payload;
                os.write(data.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            int responseCode = connection.getResponseCode();
            boolean success = responseCode == HttpURLConnection.HTTP_OK
                    || responseCode == HttpURLConnection.HTTP_NO_CONTENT;

            InputStream stream = success ? connection.getInputStream() : connection.getErrorStream();
            if (stream != null) {
                try (BufferedReader ignored = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    while (ignored.readLine() != null) {
                        // drain stream so keep-alive connections remain reusable
                    }
                }
            }

            return success;
        } catch (Exception ex) {
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }


}
