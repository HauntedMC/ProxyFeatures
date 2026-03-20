package nl.hauntedmc.proxyfeatures.framework.loader.dependency;

import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.PluginManager;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.features.FeatureFactory;
import nl.hauntedmc.proxyfeatures.features.VelocityBaseFeature;
import nl.hauntedmc.proxyfeatures.framework.loader.FeatureLoadManager;
import nl.hauntedmc.proxyfeatures.framework.loader.FeatureRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

        when(plugin.getLogger()).thenReturn(mock(ComponentLogger.class));
        when(plugin.getPluginManager()).thenReturn(pluginManager);
        when(featureLoadManager.getFeatureRegistry()).thenReturn(registry);
        when(featureLoadManager.loadFeature(anyString())).thenReturn(true);

        manager = new FeatureDependencyManager(featureLoadManager, plugin);
    }

    @SuppressWarnings("unchecked")
    @Test
    void areDependenciesMetReturnsFalseOnCircularDependency() {
        VelocityBaseFeature<?> feature = mock(VelocityBaseFeature.class);
        when(feature.getFeatureName()).thenReturn("A");
        when(feature.getDependencies()).thenReturn(List.of("A"));
        when(feature.getPluginDependencies()).thenReturn(List.of());

        Class<? extends VelocityBaseFeature<?>> type = (Class<? extends VelocityBaseFeature<?>>) (Class<?>) VelocityBaseFeature.class;
        registry.registerAvailableFeature("A", type);

        try (MockedStatic<FeatureFactory> factory = mockStatic(FeatureFactory.class)) {
            factory.when(() -> FeatureFactory.createFeature(any(), eq(plugin))).thenReturn(feature);
            assertFalse(manager.areDependenciesMet(feature));
        }
    }

    @Test
    void areDependenciesMetReturnsFalseWhenDependencyMissingOrInstantiationFails() {
        VelocityBaseFeature<?> feature = mock(VelocityBaseFeature.class);
        when(feature.getFeatureName()).thenReturn("A");
        when(feature.getDependencies()).thenReturn(List.of("B"));
        when(feature.getPluginDependencies()).thenReturn(List.of());

        assertFalse(manager.areDependenciesMet(feature));

        @SuppressWarnings("unchecked")
        Class<? extends VelocityBaseFeature<?>> type = (Class<? extends VelocityBaseFeature<?>>) (Class<?>) VelocityBaseFeature.class;
        registry.registerAvailableFeature("B", type);

        try (MockedStatic<FeatureFactory> factory = mockStatic(FeatureFactory.class)) {
            factory.when(() -> FeatureFactory.createFeature(any(), eq(plugin))).thenReturn(null);
            assertFalse(manager.areDependenciesMet(feature));
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void areDependenciesMetReturnsFalseWhenDependencyCannotBeLoaded() {
        VelocityBaseFeature<?> feature = mock(VelocityBaseFeature.class);
        when(feature.getFeatureName()).thenReturn("A");
        when(feature.getDependencies()).thenReturn(List.of("B"));
        when(feature.getPluginDependencies()).thenReturn(List.of());

        VelocityBaseFeature<?> dep = mock(VelocityBaseFeature.class);
        when(dep.getFeatureName()).thenReturn("B");
        when(dep.getDependencies()).thenReturn(List.of());

        Class<? extends VelocityBaseFeature<?>> type = (Class<? extends VelocityBaseFeature<?>>) (Class<?>) VelocityBaseFeature.class;
        registry.registerAvailableFeature("B", type);
        when(featureLoadManager.loadFeature("B")).thenReturn(false);

        try (MockedStatic<FeatureFactory> factory = mockStatic(FeatureFactory.class)) {
            factory.when(() -> FeatureFactory.createFeature(any(), eq(plugin))).thenReturn(dep);
            assertFalse(manager.areDependenciesMet(feature));
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void areDependenciesMetReturnsTrueWhenDependenciesAndPluginsAreAvailable() {
        VelocityBaseFeature<?> feature = mock(VelocityBaseFeature.class);
        when(feature.getFeatureName()).thenReturn("A");
        when(feature.getDependencies()).thenReturn(List.of("B"));
        when(feature.getPluginDependencies()).thenReturn(List.of("luckperms"));

        VelocityBaseFeature<?> dep = mock(VelocityBaseFeature.class);
        when(dep.getFeatureName()).thenReturn("B");
        when(dep.getDependencies()).thenReturn(List.of());

        Class<? extends VelocityBaseFeature<?>> type = (Class<? extends VelocityBaseFeature<?>>) (Class<?>) VelocityBaseFeature.class;
        registry.registerAvailableFeature("B", type);
        when(pluginManager.getPlugin("luckperms")).thenReturn(Optional.of(mock(PluginContainer.class)));

        try (MockedStatic<FeatureFactory> factory = mockStatic(FeatureFactory.class)) {
            factory.when(() -> FeatureFactory.createFeature(any(), eq(plugin))).thenReturn(dep);
            assertTrue(manager.areDependenciesMet(feature));
            verify(featureLoadManager).loadFeature("B");
        }
    }

    @Test
    void pluginDependencyChecksAndPluginLookupAreDelegated() {
        VelocityBaseFeature<?> feature = mock(VelocityBaseFeature.class);
        when(feature.getFeatureName()).thenReturn("Queue");
        when(feature.getPluginDependencies()).thenReturn(List.of("a", "b"));

        when(pluginManager.getPlugin("a")).thenReturn(Optional.of(mock(PluginContainer.class)));
        when(pluginManager.getPlugin("b")).thenReturn(Optional.empty());

        assertFalse(manager.arePluginDependenciesMet(feature));
        assertTrue(manager.isPluginLoaded("a"));
        assertFalse(manager.isPluginLoaded("b"));
    }

    @Test
    void getDependentFeaturesReturnsLoadedFeaturesThatDependOnTarget() {
        VelocityBaseFeature<?> queue = mock(VelocityBaseFeature.class);
        when(queue.getDependencies()).thenReturn(List.of());

        VelocityBaseFeature<?> friends = mock(VelocityBaseFeature.class);
        when(friends.getDependencies()).thenReturn(List.of("Queue"));

        registry.registerLoadedFeature("Queue", queue);
        registry.registerLoadedFeature("Friends", friends);

        assertEquals(List.of("Friends"), manager.getDependentFeatures("Queue"));
    }
}
