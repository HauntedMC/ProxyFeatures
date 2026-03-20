package nl.hauntedmc.proxyfeatures.features.serverlinks.internal;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.util.ServerLink;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ServerLinksHandlerTest {

    @Test
    void applyLinksSetsExpectedSecureLinks() {
        Player player = mock(Player.class);

        new ServerLinksHandler().applyLinks(player);

        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<List<ServerLink>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(player).setServerLinks(captor.capture());

        List<ServerLink> links = captor.getValue();
        assertEquals(7, links.size());
        assertTrue(links.stream().allMatch(link -> link.getUrl().toString().startsWith("https://")));
        assertEquals("https://store.hauntedmc.nl", links.get(1).getUrl().toString());
        assertTrue(links.get(0).getCustomLabel().isPresent());
        assertEquals(ServerLink.Type.WEBSITE, links.get(3).getBuiltInType().orElseThrow());
    }
}
