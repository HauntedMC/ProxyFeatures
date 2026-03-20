package nl.hauntedmc.proxyfeatures.features.antivpn.internal.provider;

import nl.hauntedmc.proxyfeatures.features.antivpn.internal.IPCheckResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

class ProviderChainTest {

    @Test
    void emptyProviderListFailsFast() {
        ProviderChain chain = new ProviderChain(List.of());
        CompletionException ex = assertThrows(CompletionException.class,
                () -> chain.lookup("1.2.3.4", true, true).join());
        assertTrue(ex.getCause().getMessage().contains("No AntiVPN providers configured"));
    }

    @Test
    void lookupMergesDataAndTracksMaxTimeout() {
        StubProvider first = new StubProvider("p1", 1000, (needCountry, needVpn) ->
                CompletableFuture.completedFuture(new IPCheckResult("NL", null, "", 1L)));
        StubProvider second = new StubProvider("p2", 2500, (needCountry, needVpn) ->
                CompletableFuture.completedFuture(new IPCheckResult("", true, "p2", 2L)));

        ProviderChain chain = new ProviderChain(List.of(first, second));
        IPCheckResult result = chain.lookup("1.2.3.4", true, true).join();

        assertEquals(2500L, chain.maxTimeoutMillis());
        assertEquals("NL", result.countryCode());
        assertEquals(true, result.vpn());
        assertEquals("p1", result.providerId());
    }

    @Test
    void lookupShortCircuitsWhenFirstProviderAlreadyHasEnoughData() {
        AtomicInteger secondCalls = new AtomicInteger();
        StubProvider first = new StubProvider("p1", 1000, (needCountry, needVpn) ->
                CompletableFuture.completedFuture(new IPCheckResult("US", false, "p1", 1L)));
        StubProvider second = new StubProvider("p2", 1000, (needCountry, needVpn) -> {
            secondCalls.incrementAndGet();
            return CompletableFuture.completedFuture(new IPCheckResult("CA", true, "p2", 2L));
        });

        ProviderChain chain = new ProviderChain(List.of(first, second));
        IPCheckResult result = chain.lookup("8.8.8.8", true, true).join();

        assertEquals("US", result.countryCode());
        assertEquals(false, result.vpn());
        assertEquals(0, secondCalls.get());
    }

    @Test
    void lookupReportsAggregateFailureWhenNothingUsefulIsReturned() {
        StubProvider failing = new StubProvider("failing", 1000, (needCountry, needVpn) -> {
            CompletableFuture<IPCheckResult> f = new CompletableFuture<>();
            f.completeExceptionally(new RuntimeException("timeout"));
            return f;
        });
        StubProvider nullResult = new StubProvider("nuller", 1000, (needCountry, needVpn) ->
                CompletableFuture.completedFuture(null));

        ProviderChain chain = new ProviderChain(List.of(failing, nullResult));
        CompletionException ex = assertThrows(CompletionException.class,
                () -> chain.lookup("9.9.9.9", true, true).join());

        String msg = ex.getCause().getMessage();
        assertTrue(msg.contains("failing"));
        assertTrue(msg.contains("nuller"));
    }

    private static final class StubProvider implements IPIntelligenceProvider {
        private final String id;
        private final long timeoutMillis;
        private final BiFunction<Boolean, Boolean, CompletableFuture<IPCheckResult>> lookup;

        private StubProvider(String id, long timeoutMillis,
                             BiFunction<Boolean, Boolean, CompletableFuture<IPCheckResult>> lookup) {
            this.id = id;
            this.timeoutMillis = timeoutMillis;
            this.lookup = lookup;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public long timeoutMillis() {
            return timeoutMillis;
        }

        @Override
        public CompletableFuture<IPCheckResult> lookup(String ip, boolean needCountry, boolean needVpn) {
            return lookup.apply(needCountry, needVpn);
        }
    }
}
