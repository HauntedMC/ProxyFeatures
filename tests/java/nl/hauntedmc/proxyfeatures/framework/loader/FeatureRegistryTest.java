package nl.hauntedmc.proxyfeatures.framework.loader;

import nl.hauntedmc.proxyfeatures.features.VelocityBaseFeature;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class FeatureRegistryTest {

    @SuppressWarnings("unchecked")
    @Test
    void tracksAvailableAndLoadedFeatures() {
        FeatureRegistry registry = new FeatureRegistry();

        Class<? extends VelocityBaseFeature<?>> featureClass = (Class<? extends VelocityBaseFeature<?>>) (Class<?>) VelocityBaseFeature.class;
        VelocityBaseFeature<?> loaded = mock(VelocityBaseFeature.class);

        registry.registerAvailableFeature("Queue", featureClass);
        registry.registerLoadedFeature("Queue", loaded);

        assertTrue(registry.isFeatureLoaded("Queue"));
        assertSame(loaded, registry.getLoadedFeature("Queue"));
        assertSame(featureClass, registry.getAvailableFeatures().get("Queue"));
        assertTrue(registry.getLoadedFeatureNames().contains("Queue"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void deregisterRemovesLoadedFeatureAndLoadedFeaturesReturnsCopy() {
        FeatureRegistry registry = new FeatureRegistry();

        Class<? extends VelocityBaseFeature<?>> featureClass = (Class<? extends VelocityBaseFeature<?>>) (Class<?>) VelocityBaseFeature.class;
        VelocityBaseFeature<?> loaded = mock(VelocityBaseFeature.class);

        registry.registerAvailableFeature("Queue", featureClass);
        registry.registerLoadedFeature("Queue", loaded);

        List<VelocityBaseFeature<?>> copy = registry.getLoadedFeatures();
        copy.clear();

        assertEquals(1, registry.getLoadedFeatures().size());

        registry.deregisterLoadedFeature("Queue");
        assertFalse(registry.isFeatureLoaded("Queue"));
        assertNull(registry.getLoadedFeature("Queue"));
    }
}
