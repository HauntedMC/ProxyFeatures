package nl.hauntedmc.proxyfeatures.features.queue.util;

import com.velocitypowered.api.proxy.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PriorityResolverTest {

    @Test
    void resolveReturnsHighestGrantedPriorityLevel() {
        Player player = mock(Player.class);
        when(player.hasPermission("proxyfeatures.feature.queue.priority.1")).thenReturn(true);
        when(player.hasPermission("proxyfeatures.feature.queue.priority.2")).thenReturn(false);
        when(player.hasPermission("proxyfeatures.feature.queue.priority.3")).thenReturn(true);

        assertEquals(3, new PriorityResolver().resolve(player));
    }

    @Test
    void resolveReturnsZeroWhenNoPriorityPermissionIsPresent() {
        Player player = mock(Player.class);
        assertEquals(0, new PriorityResolver().resolve(player));
    }
}
