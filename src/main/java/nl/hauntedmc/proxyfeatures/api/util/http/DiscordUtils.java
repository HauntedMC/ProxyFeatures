package nl.hauntedmc.proxyfeatures.api.util.http;

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
    public static void sendPayload(String webhookUrl, String payload) {
        try {
            URL url = URI.create(webhookUrl).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            try (OutputStream os = connection.getOutputStream()) {
                os.write(payload.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            connection.disconnect();
        } catch (Exception ignored) {
        }
    }


}
