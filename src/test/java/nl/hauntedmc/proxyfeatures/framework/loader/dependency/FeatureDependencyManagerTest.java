package nl.hauntedmc.proxyfeatures.framework.loader.dependency;

import com.velocitypowered.api.plugin.PluginManager;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.features.VelocityBaseFeature;
import nl.hauntedmc.proxyfeatures.framework.loader.FeatureDescriptor;
import nl.hauntedmc.proxyfeatures.framework.loader.FeatureLoadManager;
import nl.hauntedmc.proxyfeatures.framework.loader.FeatureRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FeatureDependencyManagerTest {

    private ProxyFeatures plugin;
    private FeatureLoadManager featureLoadManager;
    private FeatureRegistry registry;
    private PluginManager pluginManager;
    private FeatureDependencyManager manager;

    @BeforeEach
    void setUp() {
        plugin = mock(ProxyFeatures.class);
        featureLoadManager = mock(FeatureLoadManager.class);
        registry = new FeatureRegistry();
        pluginManager = mock(PluginManager.class);

        when(plugin.getLogger()).thenReturn(ComponentLogger.logger("FeatureDependencyManagerTest"));
        when(plugin.getPluginManager()).thenReturn(pluginManager);
        when(featureLoadManager.getFeatureRegistry()).thenReturn(registry);
        when(featureLoadManager.loadFeature(anyString())).thenReturn(true);
        when(featureLoadManager.getMissingPluginDependencies(anyString())).thenReturn(Set.of());
        when(featureLoadManager.resolveFeatureKey(anyString())).thenAnswer(invocation -> {
            String input = invocation.getArgument(0);
            if (input == null) {
                return null;
            }
            if (registry.getAvailableFeatures().containsKey(input) || registry.isFeatureLoaded(input)) {
                return input;
            }
            return null;
        });

        manager = new FeatureDependencyManager(featureLoadManager, plugin);
    }

    @Test
    void areDependenciesMetReturnsFalseOnCircularDependency() {
        registry.registerAvailableFeature(descriptor("A", Set.of("B"), Set.of()));
        registry.registerAvailableFeature(descriptor("B", Set.of("A"), Set.of()));
        assertFalse(manager.areDependenciesMet("A"));
    }

    @Test
    void areDependenciesMetReturnsFalseWhenDependencyIsMissing() {
        registry.registerAvailableFeature(descriptor("A", Set.of("B"), Set.of()));
        assertFalse(manager.areDependenciesMet("A"));
    }

    @Test
    void areDependenciesMetReturnsFalseWhenDependencyCannotBeLoaded() {
        registry.registerAvailableFeature(descriptor("A", Set.of("B"), Set.of()));
        registry.registerAvailableFeature(descriptor("B", Set.of(), Set.of()));
        when(featureLoadManager.loadFeature("B")).thenReturn(false);
        assertFalse(manager.areDependenciesMet("A"));
    }

    @Test
    void areDependenciesMetReturnsTrueWhenDependenciesAndPluginsAreAvailable() {
        registry.registerAvailableFeature(descriptor("A", Set.of("B"), Set.of("luckperms")));
        registry.registerAvailableFeature(descriptor("B", Set.of(), Set.of()));
        when(featureLoadManager.getMissingPluginDependencies("A")).thenReturn(Set.of());
        assertTrue(manager.areDependenciesMet("A"));
        verify(featureLoadManager).loadFeature("B");
    }

    @Test
    void pluginDependencyChecksAreDelegated() {
        when(featureLoadManager.getMissingPluginDependencies("Queue")).thenReturn(Set.of("a", "b"));
        when(featureLoadManager.getMissingPluginDependencies("Vanish")).thenReturn(Set.of());

        assertFalse(manager.arePluginDependenciesMet("Queue"));
        assertTrue(manager.arePluginDependenciesMet("Vanish"));
    }

    @Test
    void getDependentFeaturesReturnsLoadedFeaturesThatDependOnTarget() {
        VelocityBaseFeature<?> queue = mock(VelocityBaseFeature.class);
        when(queue.getDependencies()).thenReturn(List.of());

        VelocityBaseFeature<?> friends = mock(VelocityBaseFeature.class);
        when(friends.getDependencies()).thenReturn(List.of("Queue"));

        registry.registerAvailableFeature(descriptor("Queue", Set.of(), Set.of()));
        registry.registerAvailableFeature(descriptor("Friends", Set.of("Queue"), Set.of()));
        registry.registerLoadedFeature("Queue", queue);
        registry.registerLoadedFeature("Friends", friends);

        assertEquals(List.of("Friends"), manager.getDependentFeatures("Queue"));
    }

    @Test
    void getDependentFeaturesSkipsNullLoadedEntries() {
        VelocityBaseFeature<?> queue = mock(VelocityBaseFeature.class);
        when(queue.getDependencies()).thenReturn(List.of());

        VelocityBaseFeature<?> friends = mock(VelocityBaseFeature.class);
        when(friends.getDependencies()).thenReturn(List.of("Queue"));

        registry.registerAvailableFeature(descriptor("Queue", Set.of(), Set.of()));
        registry.registerAvailableFeature(descriptor("Ghost", Set.of(), Set.of()));
        registry.registerAvailableFeature(descriptor("Friends", Set.of("Queue"), Set.of()));
        registry.registerLoadedFeature("Queue", queue);
        registry.registerLoadedFeature("Ghost", null);
        registry.registerLoadedFeature("Friends", friends);

        assertEquals(List.of("Friends"), manager.getDependentFeatures("Queue"));
    }

    private FeatureDescriptor descriptor(String name, Set<String> featureDependencies, Set<String> pluginDependencies) {
        return new FeatureDescriptor(
                name,
                VelocityBaseFeature.class.getName(),
                name,
                "1.0",
                featureDependencies,
                pluginDependencies
        );
    }
}
