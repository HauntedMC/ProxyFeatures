package nl.hauntedmc.proxyfeatures.features.votifier.messaging;

import nl.hauntedmc.dataprovider.database.messaging.MessagingDataAccess;
import nl.hauntedmc.proxyfeatures.features.votifier.Votifier;
import nl.hauntedmc.proxyfeatures.framework.log.FeatureLogger;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class EventBusHandlerTest {

    @Test
    void publishVoteDelegatesToRedisBus() {
        MessagingDataAccess redis = mock(MessagingDataAccess.class);
        Votifier feature = mock(Votifier.class);
        VoteMessage message = new VoteMessage("site", "Remy", "127.0.0.1", 1L);
        when(redis.publish("votes", message)).thenReturn(CompletableFuture.completedFuture(null));

        EventBusHandler handler = new EventBusHandler(feature, redis);
        handler.publishVote(message, "votes");

        verify(redis).publish(eq("votes"), eq(message));
    }

    @Test
    void publishFailureIsLogged() {
        MessagingDataAccess redis = mock(MessagingDataAccess.class);
        Votifier feature = mock(Votifier.class);
        FeatureLogger logger = mock(FeatureLogger.class);
        when(feature.getLogger()).thenReturn(logger);

        VoteMessage message = new VoteMessage("site", "Remy", "127.0.0.1", 1L);
        when(redis.publish("votes", message))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("down")));

        EventBusHandler handler = new EventBusHandler(feature, redis);
        handler.publishVote(message, "votes");

        verify(logger).error(contains("Failed to publish vote message"));
    }

    @Test
    void publishSuccessDoesNotLogErrors() {
        MessagingDataAccess redis = mock(MessagingDataAccess.class);
        Votifier feature = mock(Votifier.class);
        FeatureLogger logger = mock(FeatureLogger.class);
        when(feature.getLogger()).thenReturn(logger);

        VoteMessage message = new VoteMessage("site", "Remy", "127.0.0.1", 1L);
        when(redis.publish("votes", message)).thenReturn(CompletableFuture.completedFuture(null));

        EventBusHandler handler = new EventBusHandler(feature, redis);
        handler.publishVote(message, "votes");

        verifyNoInteractions(logger);
    }
}
