package nl.hauntedmc.proxyfeatures.framework.loader;

import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.PluginManager;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.proxyfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.proxyfeatures.features.FeatureFactory;
import nl.hauntedmc.proxyfeatures.features.VelocityBaseFeature;
import nl.hauntedmc.proxyfeatures.framework.config.FeatureConfigHandler;
import nl.hauntedmc.proxyfeatures.framework.config.MainConfigHandler;
import nl.hauntedmc.proxyfeatures.framework.loader.disable.FeatureDisableResult;
import nl.hauntedmc.proxyfeatures.framework.loader.enable.FeatureEnableResult;
import nl.hauntedmc.proxyfeatures.framework.loader.reload.FeatureReloadResult;
import nl.hauntedmc.proxyfeatures.framework.loader.softreload.FeatureSoftReloadResult;
import nl.hauntedmc.proxyfeatures.framework.localization.LocalizationHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class FeatureLoadManagerTest {

    private ProxyFeatures plugin;
    private MainConfigHandler mainConfig;
    private LocalizationHandler localization;
    private ComponentLogger logger;
    private PluginManager pluginManager;

    @BeforeEach
    void setUp() {
        plugin = mock(ProxyFeatures.class);
        mainConfig = mock(MainConfigHandler.class);
        localization = mock(LocalizationHandler.class);
        logger = mock(ComponentLogger.class);
        pluginManager = mock(PluginManager.class);

        when(plugin.getConfigHandler()).thenReturn(mainConfig);
        when(plugin.getLocalizationHandler()).thenReturn(localization);
        when(plugin.getLogger()).thenReturn(logger);
        when(plugin.getPluginManager()).thenReturn(pluginManager);
        when(mainConfig.isFeatureEnabled(anyString())).thenReturn(true);
    }

    @SuppressWarnings("unchecked")
    @Test
    void constructorDiscoversFeaturesAndCallsCleanupUnused() {
        FeatureLoadManager manager = new FeatureLoadManager(plugin);
        verify(mainConfig).cleanupUnusedFeatures(anySet());

        Class<? extends VelocityBaseFeature<?>> token = (Class<? extends VelocityBaseFeature<?>>) (Class<?>) VelocityBaseFeature.class;
        manager.getFeatureRegistry().registerAvailableFeature("Queue", token);
        assertTrue(manager.getFeatureRegistry().getAvailableFeatures().containsKey("Queue"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void initializeFeaturesComputesLoadOrderAndAbortsOnCycles() {
        FeatureLoadManager raw = new FeatureLoadManager(plugin);
        FeatureLoadManager manager = spy(raw);
        clearRegistry(manager);

        Class<? extends VelocityBaseFeature<?>> token = (Class<? extends VelocityBaseFeature<?>>) (Class<?>) VelocityBaseFeature.class;
        manager.getFeatureRegistry().registerAvailableFeature("A", token);
        manager.getFeatureRegistry().registerAvailableFeature("B", token);

        VelocityBaseFeature<?> a = feature("A", List.of("B"), List.of());
        VelocityBaseFeature<?> b = feature("B", List.of(), List.of());

        try (MockedStatic<FeatureFactory> factory = mockStatic(FeatureFactory.class)) {
            factory.when(() -> FeatureFactory.createFeature(any(), eq(plugin))).thenReturn(a, b);
            doReturn(true).when(manager).loadFeature(anyString());

            manager.initializeFeatures();
            verify(manager).loadFeature("B");
            verify(manager).loadFeature("A");
        }

        clearRegistry(manager);
        manager.getFeatureRegistry().registerAvailableFeature("A", token);
        manager.getFeatureRegistry().registerAvailableFeature("B", token);
        VelocityBaseFeature<?> cycleA = feature("A", List.of("B"), List.of());
        VelocityBaseFeature<?> cycleB = feature("B", List.of("A"), List.of());

        try (MockedStatic<FeatureFactory> factory = mockStatic(FeatureFactory.class)) {
            factory.when(() -> FeatureFactory.createFeature(any(), eq(plugin))).thenReturn(cycleA, cycleB);
            reset(manager);
            manager.initializeFeatures();
            verify(manager, never()).loadFeature(anyString());
        }

        clearRegistry(manager);
        manager.getFeatureRegistry().getAvailableFeatures().put("Ghost", null);
        manager.initializeFeatures();
    }

    @SuppressWarnings("unchecked")
    @Test
    void enableFeatureCoversAllResultTypes() {
        FeatureLoadManager manager = new FeatureLoadManager(plugin);
        clearRegistry(manager);

        assertEquals(FeatureEnableResult.NOT_FOUND, manager.enableFeature("Missing").result());

        VelocityBaseFeature<?> loaded = feature("Queue", List.of(), List.of());
        Class<? extends VelocityBaseFeature<?>> token = (Class<? extends VelocityBaseFeature<?>>) (Class<?>) VelocityBaseFeature.class;
        manager.getFeatureRegistry().registerAvailableFeature("Queue", token);
        manager.getFeatureRegistry().registerLoadedFeature("Queue", loaded);
        assertEquals(FeatureEnableResult.ALREADY_LOADED, manager.enableFeature("Queue").result());

        manager.getFeatureRegistry().deregisterLoadedFeature("Queue");

        try (MockedStatic<FeatureFactory> factory = mockStatic(FeatureFactory.class)) {
            factory.when(() -> FeatureFactory.createFeature(any(), eq(plugin))).thenReturn(null);
            assertEquals(FeatureEnableResult.FAILED, manager.enableFeature("Queue").result());
        }

        VelocityBaseFeature<?> missingPlugin = feature("Queue", List.of(), List.of("luckperms"));
        try (MockedStatic<FeatureFactory> factory = mockStatic(FeatureFactory.class)) {
            factory.when(() -> FeatureFactory.createFeature(any(), eq(plugin))).thenReturn(missingPlugin);
            when(pluginManager.getPlugin("luckperms")).thenReturn(Optional.empty());
            assertEquals(FeatureEnableResult.MISSING_PLUGIN_DEPENDENCY, manager.enableFeature("Queue").result());
        }

        VelocityBaseFeature<?> missingFeature = feature("Queue", List.of("Friends"), List.of());
        try (MockedStatic<FeatureFactory> factory = mockStatic(FeatureFactory.class)) {
            factory.when(() -> FeatureFactory.createFeature(any(), eq(plugin))).thenReturn(missingFeature);
            assertEquals(FeatureEnableResult.MISSING_FEATURE_DEPENDENCY, manager.enableFeature("Queue").result());
        }

        VelocityBaseFeature<?> throwingPluginDeps = feature("Queue", List.of(), List.of());
        when(throwingPluginDeps.getPluginDependencies()).thenThrow(new RuntimeException("boom"));
        try (MockedStatic<FeatureFactory> factory = mockStatic(FeatureFactory.class)) {
            factory.when(() -> FeatureFactory.createFeature(any(), eq(plugin))).thenReturn(throwingPluginDeps);
            FeatureLoadManager successAfterWarning = spy(manager);
            doReturn(true).when(successAfterWarning).loadFeature("Queue");
            assertEquals(FeatureEnableResult.SUCCESS, successAfterWarning.enableFeature("Queue").result());
        }

        FeatureLoadManager failing = spy(manager);
        VelocityBaseFeature<?> failingFeature = feature("Queue", List.of(), List.of());
        try (MockedStatic<FeatureFactory> factory = mockStatic(FeatureFactory.class)) {
            factory.when(() -> FeatureFactory.createFeature(any(), eq(plugin))).thenReturn(failingFeature);
            doReturn(false).when(failing).loadFeature("Queue");
            assertEquals(FeatureEnableResult.FAILED, failing.enableFeature("Queue").result());
            verify(mainConfig, atLeastOnce()).setFeatureEnabled("Queue", true);
        }

        FeatureLoadManager success = spy(manager);
        VelocityBaseFeature<?> successFeature = feature("Queue", List.of(), List.of());
        try (MockedStatic<FeatureFactory> factory = mockStatic(FeatureFactory.class)) {
            factory.when(() -> FeatureFactory.createFeature(any(), eq(plugin))).thenReturn(successFeature);
            doReturn(true).when(success).loadFeature("Queue");
            assertEquals(FeatureEnableResult.SUCCESS, success.enableFeature("Queue").result());
        }
    }

    @Test
    void disableSoftReloadAndReloadHandleSuccessAndFailures() {
        FeatureLoadManager manager = new FeatureLoadManager(plugin);
        clearRegistry(manager);

        assertEquals(FeatureDisableResult.NOT_LOADED, manager.disableFeature("Queue").result());
        assertEquals(FeatureSoftReloadResult.NOT_LOADED, manager.softReloadFeature("Queue").result());
        assertEquals(FeatureReloadResult.NOT_LOADED, manager.reloadFeature("Queue").result());

        VelocityBaseFeature<?> queue = feature("Queue", List.of(), List.of());
        VelocityBaseFeature<?> dependent = feature("Dependent", List.of("Queue"), List.of());
        doThrow(new RuntimeException("cleanup failed")).when(dependent).cleanup();
        manager.getFeatureRegistry().registerLoadedFeature("Queue", queue);
        manager.getFeatureRegistry().registerLoadedFeature("Dependent", dependent);

        assertEquals(FeatureDisableResult.SUCCESS, manager.disableFeature("Queue").result());

        clearRegistry(manager);
        VelocityBaseFeature<?> failDisable = feature("Fail", List.of(), List.of());
        doThrow(new RuntimeException("boom")).when(failDisable).cleanup();
        manager.getFeatureRegistry().registerLoadedFeature("Fail", failDisable);
        assertEquals(FeatureDisableResult.FAILED, manager.disableFeature("Fail").result());

        clearRegistry(manager);
        VelocityBaseFeature<?> soft = feature("Soft", List.of(), List.of());
        manager.getFeatureRegistry().registerLoadedFeature("Soft", soft);
        assertEquals(FeatureSoftReloadResult.SUCCESS, manager.softReloadFeature("Soft").result());

        VelocityBaseFeature<?> softFail = feature("SoftFail", List.of(), List.of());
        FeatureConfigHandler cfg = mock(FeatureConfigHandler.class);
        when(softFail.getConfigHandler()).thenReturn(cfg);
        doThrow(new RuntimeException("boom")).when(cfg).reloadConfig();
        manager.getFeatureRegistry().registerLoadedFeature("SoftFail", softFail);
        assertEquals(FeatureSoftReloadResult.FAILED, manager.softReloadFeature("SoftFail").result());
    }

    @Test
    void reloadFeatureCoversFailedAndSuccessPathsIncludingDependents() {
        FeatureLoadManager base = new FeatureLoadManager(plugin);
        FeatureLoadManager manager = spy(base);
        clearRegistry(manager);

        VelocityBaseFeature<?> queue = feature("Queue", List.of(), List.of());
        VelocityBaseFeature<?> dependent = feature("Dependent", List.of("Queue"), List.of());
        manager.getFeatureRegistry().registerLoadedFeature("Queue", queue);
        manager.getFeatureRegistry().registerLoadedFeature("Dependent", dependent);

        doReturn(false).when(manager).loadFeature("Queue");
        assertEquals(FeatureReloadResult.FAILED, manager.reloadFeature("Queue").result());

        manager.getFeatureRegistry().registerLoadedFeature("Queue", queue);
        doThrow(new RuntimeException("boom")).when(mainConfig).reloadConfig();
        assertEquals(FeatureReloadResult.FAILED, manager.reloadFeature("Queue").result());
        reset(mainConfig);

        manager.getFeatureRegistry().registerLoadedFeature("Queue", queue);
        manager.getFeatureRegistry().registerLoadedFeature("Dependent", dependent);
        reset(manager);
        doReturn(true).when(manager).loadFeature(anyString());
        assertEquals(FeatureReloadResult.SUCCESS, manager.reloadFeature("Queue").result());
    }

    @SuppressWarnings("unchecked")
    @Test
    void loadFeatureCoversRegistrationDependencyAndInitializationBranches() {
        FeatureLoadManager manager = new FeatureLoadManager(plugin);
        clearRegistry(manager);

        assertFalse(manager.loadFeature("missing"));

        Class<? extends VelocityBaseFeature<?>> token = (Class<? extends VelocityBaseFeature<?>>) (Class<?>) VelocityBaseFeature.class;
        manager.getFeatureRegistry().registerAvailableFeature("Queue", token);
        manager.getFeatureRegistry().registerLoadedFeature("Queue", feature("Queue", List.of(), List.of()));
        assertFalse(manager.loadFeature("Queue"));
        manager.getFeatureRegistry().deregisterLoadedFeature("Queue");

        VelocityBaseFeature<?> feature = feature("Queue", List.of(), List.of());
        try (MockedStatic<FeatureFactory> factory = mockStatic(FeatureFactory.class)) {
            factory.when(() -> FeatureFactory.createFeature(any(), eq(plugin))).thenReturn(null);
            assertFalse(manager.loadFeature("Queue"));
        }

        when(mainConfig.isFeatureEnabled("Queue")).thenReturn(false);
        try (MockedStatic<FeatureFactory> factory = mockStatic(FeatureFactory.class)) {
            factory.when(() -> FeatureFactory.createFeature(any(), eq(plugin))).thenReturn(feature);
            assertFalse(manager.loadFeature("Queue"));
            verify(mainConfig).registerFeature("Queue");
            verify(mainConfig).injectFeatureDefaults(eq("Queue"), any(ConfigMap.class));
            verify(localization).registerDefaultMessages(any(MessageMap.class));
        }

        when(mainConfig.isFeatureEnabled("Queue")).thenReturn(true);
        when(pluginManager.getPlugin("required")).thenReturn(Optional.empty());
        VelocityBaseFeature<?> depsFeature = feature("Queue", List.of(), List.of("required"));
        try (MockedStatic<FeatureFactory> factory = mockStatic(FeatureFactory.class)) {
            factory.when(() -> FeatureFactory.createFeature(any(), eq(plugin))).thenReturn(depsFeature);
            assertFalse(manager.loadFeature("Queue"));
        }

        when(pluginManager.getPlugin("required")).thenReturn(Optional.of(mock(PluginContainer.class)));
        VelocityBaseFeature<?> success = feature("Queue", List.of(), List.of());
        try (MockedStatic<FeatureFactory> factory = mockStatic(FeatureFactory.class)) {
            factory.when(() -> FeatureFactory.createFeature(any(), eq(plugin))).thenReturn(success);
            assertTrue(manager.loadFeature("Queue"));
            assertTrue(manager.getFeatureRegistry().isFeatureLoaded("Queue"));
        }

        manager.getFeatureRegistry().deregisterLoadedFeature("Queue");
        VelocityBaseFeature<?> initFail = feature("Queue", List.of(), List.of());
        doThrow(new RuntimeException("boom")).when(initFail).initialize();
        try (MockedStatic<FeatureFactory> factory = mockStatic(FeatureFactory.class)) {
            factory.when(() -> FeatureFactory.createFeature(any(), eq(plugin))).thenReturn(initFail);
            assertFalse(manager.loadFeature("Queue"));
            verify(initFail).cleanup();
        }

        VelocityBaseFeature<?> initFailCleanupFail = feature("Queue", List.of(), List.of());
        doThrow(new RuntimeException("boom")).when(initFailCleanupFail).initialize();
        doThrow(new RuntimeException("cleanup boom")).when(initFailCleanupFail).cleanup();
        try (MockedStatic<FeatureFactory> factory = mockStatic(FeatureFactory.class)) {
            factory.when(() -> FeatureFactory.createFeature(any(), eq(plugin))).thenReturn(initFailCleanupFail);
            assertFalse(manager.loadFeature("Queue"));
        }
    }

    @Test
    void unloadAllFeaturesHandlesCleanupErrorsAndNullEntries() {
        FeatureLoadManager manager = new FeatureLoadManager(plugin);
        clearRegistry(manager);

        VelocityBaseFeature<?> ok = feature("Queue", List.of(), List.of());
        VelocityBaseFeature<?> failing = feature("Fail", List.of(), List.of());
        doThrow(new RuntimeException("boom")).when(failing).cleanup();

        manager.getFeatureRegistry().registerLoadedFeature("Queue", ok);
        manager.getFeatureRegistry().registerLoadedFeature("Fail", failing);
        manager.getFeatureRegistry().registerLoadedFeature("NullFeature", null);

        manager.unloadAllFeatures();
        assertTrue(manager.getFeatureRegistry().getLoadedFeatureNames().isEmpty());
    }

    private VelocityBaseFeature<?> feature(String name, List<String> dependencies, List<String> pluginDependencies) {
        VelocityBaseFeature<?> feature = mock(VelocityBaseFeature.class);
        when(feature.getFeatureName()).thenReturn(name);
        when(feature.getDependencies()).thenReturn(dependencies);
        when(feature.getPluginDependencies()).thenReturn(pluginDependencies);
        when(feature.getDefaultConfig()).thenReturn(new ConfigMap());
        when(feature.getDefaultMessages()).thenReturn(new MessageMap());

        FeatureConfigHandler cfg = mock(FeatureConfigHandler.class);
        when(feature.getConfigHandler()).thenReturn(cfg);
        when(feature.getLocalizationHandler()).thenReturn(localization);
        return feature;
    }

    private void clearRegistry(FeatureLoadManager manager) {
        manager.getFeatureRegistry().getAvailableFeatures().clear();
        manager.getFeatureRegistry().getLoadedFeatureNames().clear();
    }
}
