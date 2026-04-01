package nl.hauntedmc.proxyfeatures.features.votifier.listener;

import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.proxy.Player;
import nl.hauntedmc.proxyfeatures.features.votifier.Votifier;
import nl.hauntedmc.proxyfeatures.features.votifier.internal.VotifierService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.*;

class VotifierPlayerListenerTest {

    @Test
    void postLoginDelegatesToServiceWhenAvailable() {
        Votifier feature = mock(Votifier.class);
        VotifierService service = mock(VotifierService.class);
        when(feature.getService()).thenReturn(service);

        Player player = mock(Player.class);
        PostLoginEvent event = mock(PostLoginEvent.class);
        when(event.getPlayer()).thenReturn(player);

        VotifierPlayerListener listener = new VotifierPlayerListener(feature);
        listener.onPostLogin(event);

        verify(service).onPlayerPostLogin(player);
    }

    @Test
    void disconnectDelegatesToServiceWhenAvailable() {
        Votifier feature = mock(Votifier.class);
        VotifierService service = mock(VotifierService.class);
        when(feature.getService()).thenReturn(service);

        Player player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);

        DisconnectEvent event = mock(DisconnectEvent.class);
        when(event.getPlayer()).thenReturn(player);

        VotifierPlayerListener listener = new VotifierPlayerListener(feature);
        listener.onDisconnect(event);

        verify(service).onPlayerDisconnect(uuid);
    }

    @Test
    void listenerNoopsWhenServiceUnavailable() {
        Votifier feature = mock(Votifier.class);
        when(feature.getService()).thenReturn(null);

        Player player = mock(Player.class);
        PostLoginEvent postLoginEvent = mock(PostLoginEvent.class);
        when(postLoginEvent.getPlayer()).thenReturn(player);

        DisconnectEvent disconnectEvent = mock(DisconnectEvent.class);
        when(disconnectEvent.getPlayer()).thenReturn(player);

        VotifierPlayerListener listener = new VotifierPlayerListener(feature);
        listener.onPostLogin(postLoginEvent);
        listener.onDisconnect(disconnectEvent);

        verify(feature, times(2)).getService();
    }
}
