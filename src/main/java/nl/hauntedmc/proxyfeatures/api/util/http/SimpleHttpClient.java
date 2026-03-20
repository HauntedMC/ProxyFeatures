package nl.hauntedmc.proxyfeatures.api.util.http;

import org.apache.hc.core5.http.NameValuePair;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.time.Duration;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class SimpleHttpClient {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;

    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public static String post(String url, List<NameValuePair> args) throws Exception {
        List<NameValuePair> safeArgs = args == null ? List.of() : args;
        String form = safeArgs.stream()
                .filter(Objects::nonNull)
                .map(p -> encodeFormComponent(p.getName()) + "=" + encodeFormComponent(p.getValue()))
                .collect(Collectors.joining("&"));

        URI uri = URI.create(Objects.requireNonNull(url, "url"));
        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("https") && !scheme.equalsIgnoreCase("http"))) {
            throw new IOException("Unsupported URI scheme for HTTP client: " + scheme);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        String body;
        try (InputStream stream = response.body()) {
            byte[] bytes = stream.readNBytes(MAX_RESPONSE_BYTES + 1);
            if (bytes.length > MAX_RESPONSE_BYTES) {
                throw new IOException("Response too large");
            }
            body = new String(bytes, StandardCharsets.UTF_8);
        }
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return body;
        } else {
            throw new IOException("Unexpected response code: " + response.statusCode());
        }
    }

    private static String encodeFormComponent(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
