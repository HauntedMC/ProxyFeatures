package nl.hauntedmc.proxyfeatures.api.util.http;

import org.apache.hc.core5.http.NameValuePair;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

public class SimpleHttpClient {

    private static final HttpClient client = HttpClient.newHttpClient();

    public static String post(String url, List<NameValuePair> args) throws Exception {
        String form = args.stream()
                .map(p -> URLEncoder.encode(p.getName(), StandardCharsets.UTF_8)
                        + "=" + URLEncoder.encode(p.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
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