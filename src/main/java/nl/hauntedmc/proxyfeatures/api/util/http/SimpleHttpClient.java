package nl.hauntedmc.proxyfeatures.api.util.http;

import org.apache.hc.core5.http.NameValuePair;

import java.io.IOException;
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

    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public static String post(String url, List<NameValuePair> args) throws Exception {
        List<NameValuePair> safeArgs = args == null ? List.of() : args;
        String form = safeArgs.stream()
                .filter(Objects::nonNull)
                .map(p -> URLEncoder.encode(String.valueOf(p.getName()), StandardCharsets.UTF_8)
                        + "=" + URLEncoder.encode(String.valueOf(p.getValue()), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(Objects.requireNonNull(url, "url")))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return response.body();
        } else {
            throw new IOException("Unexpected response code: " + response.statusCode());
        }
    }
}
