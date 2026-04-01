package nl.hauntedmc.proxyfeatures.api.util.http;

import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SimpleHttpClientTest {

    @Test
    void postReturnsBodyFor2xxUsingInjectedHttpClient() throws Exception {
        HttpClient mockClient = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<InputStream> response = (HttpResponse<InputStream>) mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(new ByteArrayInputStream("ok".getBytes(StandardCharsets.UTF_8)));
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        String body = SimpleHttpClient.post("http://example.test/path",
                List.of(
                        new BasicNameValuePair("a b", "x/y"),
                        new BasicNameValuePair("nullable", null)
                ),
                mockClient);
        assertEquals("ok", body);
    }

    @Test
    void postThrowsForNon2xxAndOversizedResponses() throws Exception {
        HttpClient mockClient = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<InputStream> non2xx = (HttpResponse<InputStream>) mock(HttpResponse.class);
        when(non2xx.statusCode()).thenReturn(500);
        when(non2xx.body()).thenReturn(new ByteArrayInputStream("fail".getBytes(StandardCharsets.UTF_8)));
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(non2xx);

        assertThrows(IOException.class,
                () -> SimpleHttpClient.post(
                        "http://example.test/error",
                        List.of(new BasicNameValuePair("k", "v")),
                        mockClient
                ));

        @SuppressWarnings("unchecked")
        HttpResponse<InputStream> oversized = (HttpResponse<InputStream>) mock(HttpResponse.class);
        when(oversized.statusCode()).thenReturn(200);
        when(oversized.body()).thenReturn(new ByteArrayInputStream(new byte[1024 * 1024 + 2]));
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(oversized);

        assertThrows(IOException.class,
                () -> SimpleHttpClient.post("http://example.test/large", List.of(), mockClient));
    }

    @Test
    void postRejectsUnsupportedSchemesAndNullUrl() {
        Exception ex = assertThrows(IOException.class,
                () -> SimpleHttpClient.post("file:///tmp/x", List.of(), mock(HttpClient.class)));
        assertTrue(ex.getMessage().contains("Unsupported URI scheme"));

        assertThrows(NullPointerException.class,
                () -> SimpleHttpClient.post(null, List.of(new BasicNameValuePair("a b", "x/y"))));
    }
}
