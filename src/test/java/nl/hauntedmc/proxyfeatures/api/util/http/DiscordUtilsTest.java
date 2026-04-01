package nl.hauntedmc.proxyfeatures.api.util.http;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DiscordUtilsTest {

    @Test
    void sendPayloadReturnsFalseForInvalidOrUnsupportedWebhookUrls() {
        assertFalse(DiscordUtils.sendPayload(null, "{}"));
        assertFalse(DiscordUtils.sendPayload("   ", "{}"));
        assertFalse(DiscordUtils.sendPayload("http://localhost/webhook", "{}"));
        assertFalse(DiscordUtils.sendPayload("not-a-url", "{}"));
    }

    @Test
    void sendPayloadHandlesRuntimeFailuresByReturningFalse() {
        // HTTPS scheme passes validation, but connection should fail in tests.
        assertFalse(DiscordUtils.sendPayload("https://127.0.0.1:1/webhook", "{\"a\":1}"));
    }

    @Test
    void sendPayloadHandlesSuccessNoContentAndErrorResponses() throws Exception {
        URI uri = mock(URI.class);
        URL url = mock(URL.class);
        HttpURLConnection connection = mock(HttpURLConnection.class);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        when(uri.getScheme()).thenReturn("https");
        when(uri.toURL()).thenReturn(url);
        when(url.openConnection()).thenReturn(connection);
        when(connection.getOutputStream()).thenReturn(output);
        when(connection.getInputStream()).thenReturn(new ByteArrayInputStream("ok".getBytes(StandardCharsets.UTF_8)));
        when(connection.getErrorStream()).thenReturn(new ByteArrayInputStream("err".getBytes(StandardCharsets.UTF_8)));

        try (MockedStatic<URI> mockedUri = mockStatic(URI.class)) {
            mockedUri.when(() -> URI.create("https://example.test/hook")).thenReturn(uri);

            when(connection.getResponseCode())
                    .thenReturn(HttpURLConnection.HTTP_OK)
                    .thenReturn(HttpURLConnection.HTTP_NO_CONTENT)
                    .thenReturn(HttpURLConnection.HTTP_BAD_REQUEST);

            assertTrue(DiscordUtils.sendPayload("https://example.test/hook", "{\"a\":1}"));
            assertTrue(DiscordUtils.sendPayload("https://example.test/hook", null));
            assertFalse(DiscordUtils.sendPayload("https://example.test/hook", "{\"a\":1}"));
        }

        String written = output.toString(StandardCharsets.UTF_8);
        assertTrue(written.contains("{\"a\":1}"));
        verify(connection, atLeastOnce()).disconnect();
    }
}
