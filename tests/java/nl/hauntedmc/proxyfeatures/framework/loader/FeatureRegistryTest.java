package nl.hauntedmc.proxyfeatures.framework.loader;

import org.junit.jupiter.api.Test;
import nl.hauntedmc.proxyfeatures.features.VelocityBaseFeature;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class FeatureRegistryTest {

    @Test
    void tracksAvailableAndLoadedFeatures() {
        FeatureRegistry registry = new FeatureRegistry();

        VelocityBaseFeature<?> loaded = mock(VelocityBaseFeature.class);
        FeatureDescriptor descriptor = new FeatureDescriptor(
                "Queue",
                VelocityBaseFeature.class.getName(),
                "Queue",
                "1.0",
                Set.of(),
                Set.of("luckperms")
        );

        registry.registerAvailableFeature(descriptor);
        registry.registerLoadedFeature("Queue", loaded);

        assertTrue(registry.isFeatureLoaded("Queue"));
        assertSame(loaded, registry.getLoadedFeature("Queue"));
        assertSame(descriptor, registry.getAvailableFeature("Queue"));
        assertTrue(registry.getLoadedFeatureNames().contains("Queue"));
    }

    @Test
    void deregisterRemovesLoadedFeatureAndLoadedFeaturesReturnsCopy() {
        FeatureRegistry registry = new FeatureRegistry();

        VelocityBaseFeature<?> loaded = mock(VelocityBaseFeature.class);
        FeatureDescriptor descriptor = new FeatureDescriptor(
                "Queue",
                VelocityBaseFeature.class.getName(),
                "Queue",
                "1.0",
                Set.of(),
                Set.of()
        );

        registry.registerAvailableFeature(descriptor);
        registry.registerLoadedFeature("Queue", loaded);

        List<VelocityBaseFeature<?>> copy = registry.getLoadedFeatures();
        copy.clear();

        assertEquals(1, registry.getLoadedFeatures().size());

        registry.deregisterLoadedFeature("Queue");
        assertFalse(registry.isFeatureLoaded("Queue"));
        assertNull(registry.getLoadedFeature("Queue"));
    }
}
