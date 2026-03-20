package nl.hauntedmc.proxyfeatures.api.util.http;

import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
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
    void classCanBeInstantiated() {
        assertNotNull(new SimpleHttpClient());
    }

    @Test
    void postReturnsBodyFor2xxUsingInjectedHttpClient() throws Exception {
        HttpClient mockClient = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<InputStream> response = (HttpResponse<InputStream>) mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(new ByteArrayInputStream("ok".getBytes(StandardCharsets.UTF_8)));
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        HttpClient previous = swapClient(mockClient);
        try {
            String body = SimpleHttpClient.post("http://example.test/path",
                    List.of(
                            new BasicNameValuePair("a b", "x/y"),
                            new BasicNameValuePair("nullable", null)
                    ));
            assertEquals("ok", body);
        } finally {
            swapClient(previous);
        }
    }

    @Test
    void postThrowsForNon2xxAndOversizedResponses() throws Exception {
        HttpClient mockClient = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<InputStream> non2xx = (HttpResponse<InputStream>) mock(HttpResponse.class);
        when(non2xx.statusCode()).thenReturn(500);
        when(non2xx.body()).thenReturn(new ByteArrayInputStream("fail".getBytes(StandardCharsets.UTF_8)));
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(non2xx);

        HttpClient previous = swapClient(mockClient);
        try {
            assertThrows(Exception.class,
                    () -> SimpleHttpClient.post("http://example.test/error", List.of(new BasicNameValuePair("k", "v"))));
        } finally {
            swapClient(previous);
        }

        @SuppressWarnings("unchecked")
        HttpResponse<InputStream> oversized = (HttpResponse<InputStream>) mock(HttpResponse.class);
        when(oversized.statusCode()).thenReturn(200);
        when(oversized.body()).thenReturn(new ByteArrayInputStream(new byte[1024 * 1024 + 2]));
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(oversized);

        previous = swapClient(mockClient);
        try {
            assertThrows(Exception.class,
                    () -> SimpleHttpClient.post("http://example.test/large", List.of()));
        } finally {
            swapClient(previous);
        }
    }

    @Test
    void postRejectsUnsupportedSchemesAndNullUrl() {
        Exception ex = assertThrows(Exception.class,
                () -> SimpleHttpClient.post("file:///tmp/x", List.of()));
        assertTrue(ex.getMessage().contains("Unsupported URI scheme"));

        assertThrows(NullPointerException.class,
                () -> SimpleHttpClient.post(null, List.of(new BasicNameValuePair("a b", "x/y"))));
    }

    private static HttpClient swapClient(HttpClient replacement) throws Exception {
        Field field = SimpleHttpClient.class.getDeclaredField("client");
        field.setAccessible(true);
        HttpClient previous = (HttpClient) field.get(null);

        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        Object base = unsafe.staticFieldBase(field);
        long offset = unsafe.staticFieldOffset(field);
        unsafe.putObject(base, offset, replacement);
        return previous;
    }
}
