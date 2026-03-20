package nl.hauntedmc.proxyfeatures.framework.lifecycle;

import com.velocitypowered.api.event.EventManager;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class FeatureListenerManagerTest {

    @Test
    void registerAndUnregisterTracksListenerCount() {
        ProxyFeatures plugin = mock(ProxyFeatures.class);
        EventManager eventManager = mock(EventManager.class);
        when(plugin.getEventManager()).thenReturn(eventManager);

        FeatureListenerManager manager = new FeatureListenerManager(plugin);
        Object first = new Object();
        Object second = new Object();

        manager.registerListener(first);
        manager.registerListener(second);

        assertEquals(2, manager.getRegisteredListenerCount());
        verify(eventManager).register(plugin, first);
        verify(eventManager).register(plugin, second);

        manager.unregisterAllListeners();

        assertEquals(0, manager.getRegisteredListenerCount());
        verify(eventManager).unregisterListener(plugin, first);
        verify(eventManager).unregisterListener(plugin, second);
    }
}
