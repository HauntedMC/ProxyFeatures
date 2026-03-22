package nl.hauntedmc.proxyfeatures.framework.loader;

import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.features.FeatureFactory;
import nl.hauntedmc.proxyfeatures.features.VelocityBaseFeature;
import nl.hauntedmc.proxyfeatures.framework.config.MainConfigHandler;
import nl.hauntedmc.proxyfeatures.framework.loader.dependency.DependencyCheckResult;
import nl.hauntedmc.proxyfeatures.framework.loader.dependency.FeatureDependencyManager;
import nl.hauntedmc.proxyfeatures.framework.loader.disable.FeatureDisableResponse;
import nl.hauntedmc.proxyfeatures.framework.loader.disable.FeatureDisableResult;
import nl.hauntedmc.proxyfeatures.framework.loader.enable.FeatureEnableResponse;
import nl.hauntedmc.proxyfeatures.framework.loader.enable.FeatureEnableResult;
import nl.hauntedmc.proxyfeatures.framework.loader.reload.FeatureReloadResponse;
import nl.hauntedmc.proxyfeatures.framework.loader.reload.FeatureReloadResult;
import nl.hauntedmc.proxyfeatures.framework.loader.softreload.FeatureSoftReloadResponse;
import nl.hauntedmc.proxyfeatures.framework.loader.softreload.FeatureSoftReloadResult;

import java.util.*;

/**
 * Velocity-side FeatureLoadManager with the same semantics as the Bukkit/Server version:
 * - Detailed responses for enable/disable/reload/softreload
 * - Dependency diagnosis (plugin + feature dependencies)
 * - Safe topological init
 * - Cascading disable/reload of dependents
 * - Emits FeatureLoaded/FeatureDisabled events
 */
public final class FeatureLoadManager {

    private final ProxyFeatures plugin;
    private final MainConfigHandler mainConfigHandler;
    private final FeatureRegistry featureRegistry;
    private final FeatureDependencyManager dependencyManager;
    private final nl.hauntedmc.proxyfeatures.framework.localization.LocalizationHandler localizationHandler;

    public FeatureLoadManager(ProxyFeatures plugin) {
        this.plugin = plugin;
        this.mainConfigHandler = plugin.getConfigHandler();
        this.localizationHandler = plugin.getLocalizationHandler();
        this.featureRegistry = new FeatureRegistry();
        this.dependencyManager = new FeatureDependencyManager(this, plugin);
        discoverFeatures();
    }

    /**
     * Uses ClassGraph to dynamically discover all available features.
     */
    private void discoverFeatures() {
        plugin.getLogger().info("[FeatureScanner] Scanning for features...");
        try (var scanResult = new io.github.classgraph.ClassGraph()
                .enableClassInfo()
                .acceptPackages("nl.hauntedmc.proxyfeatures.features")
                .scan()) {
            scanResult.getSubclasses(VelocityBaseFeature.class.getName()).forEach(classInfo -> {
                @SuppressWarnings("unchecked")
                Class<? extends VelocityBaseFeature<?>> featureClass =
                        (Class<? extends VelocityBaseFeature<?>>) (Class<?>) classInfo.loadClass();
                featureRegistry.registerAvailableFeature(classInfo.getSimpleName(), featureClass);
            });
        }
        plugin.getLogger().info("Discovered features: {}", featureRegistry.getAvailableFeatures().keySet());
        mainConfigHandler.cleanupUnusedFeatures(featureRegistry.getAvailableFeatures().keySet());
    }

    /**
     * Initializes all enabled features using topological sorting.
     */
    public void initializeFeatures() {
        Set<String> visited = new HashSet<>();
        List<String> loadOrder = new ArrayList<>();

        for (String featureName : featureRegistry.getAvailableFeatures().keySet()) {
            if (!visited.contains(featureName)) {
                if (!resolveFeatureLoadOrder(featureName, new HashSet<>(), visited, loadOrder)) {
                    plugin.getLogger().error("Dependency cycle detected! Feature loading aborted.");
                    return;
                }
            }
        }

        for (String featureName : loadOrder) {
            loadFeature(featureName);
        }
    }

    /**
     * Recursively determines the correct feature loading order.
     */
    private boolean resolveFeatureLoadOrder(String featureName, Set<String> stack, Set<String> visited, List<String> loadOrder) {
        if (stack.contains(featureName)) return false; // Cycle detected
        if (visited.contains(featureName)) return true;

        Class<? extends VelocityBaseFeature<?>> featureClass = featureRegistry.getAvailableFeatures().get(featureName);
        if (featureClass == null) {
            plugin.getLogger().error("Feature {} declares an unavailable dependency or was not registered.", featureName);
            return false;
        }

        stack.add(featureName);
        visited.add(featureName);

        VelocityBaseFeature<?> feature = FeatureFactory.createFeature(featureClass, plugin);
        if (feature != null) {
            for (String dependency : feature.getDependencies()) {
                if (!resolveFeatureLoadOrder(dependency, stack, visited, loadOrder)) {
                    return false;
                }
            }
        }

        stack.remove(featureName);
        loadOrder.add(featureName);
        return true;
    }

    /**
     * Enable a feature with diagnostics and proper result reporting.
     */
    public FeatureEnableResponse enableFeature(String featureName) {
        if (!featureRegistry.getAvailableFeatures().containsKey(featureName)) {
            plugin.getLogger().warn("Feature not found: {}", featureName);
            return new FeatureEnableResponse(FeatureEnableResult.NOT_FOUND, Set.of(), Set.of());
        }
        if (featureRegistry.isFeatureLoaded(featureName)) {
            plugin.getLogger().warn("Feature already loaded: {}", featureName);
            return new FeatureEnableResponse(FeatureEnableResult.ALREADY_LOADED, Set.of(), Set.of());
        }

        VelocityBaseFeature<?> feature = FeatureFactory.createFeature(featureRegistry.getAvailableFeatures().get(featureName), plugin);
        if (feature == null) {
            return new FeatureEnableResponse(FeatureEnableResult.FAILED, Set.of(), Set.of());
        }

        DependencyCheckResult diag = diagnoseDependencies(feature);
        if (!diag.ok()) {
            if (!diag.missingPluginDependencies().isEmpty()) {
                return new FeatureEnableResponse(FeatureEnableResult.MISSING_PLUGIN_DEPENDENCY, diag.missingPluginDependencies(), diag.missingFeatureDependencies());
            }
            return new FeatureEnableResponse(FeatureEnableResult.MISSING_FEATURE_DEPENDENCY, diag.missingPluginDependencies(), diag.missingFeatureDependencies());
        }

        boolean previousEnabled = mainConfigHandler.isFeatureEnabled(featureName);
        mainConfigHandler.setFeatureEnabled(featureName, true);

        boolean loaded = loadFeature(featureName);
        if (!loaded) {
            mainConfigHandler.setFeatureEnabled(featureName, previousEnabled);
            return new FeatureEnableResponse(FeatureEnableResult.FAILED, Set.of(), Set.of());
        }
        return new FeatureEnableResponse(FeatureEnableResult.SUCCESS, Set.of(), Set.of());
    }

    /**
     * Disable a feature, cascading to dependents first. Returns which dependents were also disabled.
     */
    public FeatureDisableResponse disableFeature(String featureName) {
        VelocityBaseFeature<?> feature = featureRegistry.getLoadedFeature(featureName);
        if (feature == null) {
            plugin.getLogger().warn("Feature not currently loaded: {}", featureName);
            return new FeatureDisableResponse(FeatureDisableResult.NOT_LOADED, featureName, Set.of());
        }

        // Determine and disable dependents first (to avoid dangling refs)
        Set<String> dependents = new LinkedHashSet<>(dependencyManager.getDependentFeatures(featureName));
        for (String dep : dependents) {
            FeatureDisableResponse depResp = disableFeature(dep);
            if (!depResp.success()) {
                plugin.getLogger().warn("Failed to disable dependent feature: {}", dep);
            }
        }

        try {
            feature.cleanup();
            mainConfigHandler.setFeatureEnabled(featureName, false);
            featureRegistry.deregisterLoadedFeature(featureName);
            plugin.getLogger().info("Feature disabled: {}", featureName);
            return new FeatureDisableResponse(FeatureDisableResult.SUCCESS, featureName, dependents);
        } catch (Exception t) {
            plugin.getLogger().error("Disable failed: {}", featureName, t);
            return new FeatureDisableResponse(FeatureDisableResult.FAILED, featureName, dependents);
        }
    }

    /**
     * Soft reload: re-read config + localization for a loaded feature.
     */
    public FeatureSoftReloadResponse softReloadFeature(String featureName) {
        if (!featureRegistry.isFeatureLoaded(featureName)) {
            plugin.getLogger().warn("Feature not currently loaded: {}", featureName);
            return new FeatureSoftReloadResponse(FeatureSoftReloadResult.NOT_LOADED, featureName);
        }
        try {
            VelocityBaseFeature<?> feature = featureRegistry.getLoadedFeature(featureName);
            feature.getConfigHandler().reloadConfig();
            feature.getLocalizationHandler().reloadLocalization();
            plugin.getLogger().info("Feature {} soft reloaded.", featureName);
            return new FeatureSoftReloadResponse(FeatureSoftReloadResult.SUCCESS, featureName);
        } catch (Exception t) {
            plugin.getLogger().error("Soft reload failed for: {}", featureName, t);
            return new FeatureSoftReloadResponse(FeatureSoftReloadResult.FAILED, featureName);
        }
    }

    /**
     * Full reload: reload plugin + feature configs, re-init the feature, and then best-effort reload dependents.
     */
    public FeatureReloadResponse reloadFeature(String featureName) {
        if (!featureRegistry.isFeatureLoaded(featureName)) {
            plugin.getLogger().warn("Feature not currently loaded: {}", featureName);
            return new FeatureReloadResponse(FeatureReloadResult.NOT_LOADED, featureName, Set.of());
        }

        Set<String> reloadedDependents = new LinkedHashSet<>();
        try {
            mainConfigHandler.reloadConfig();
            localizationHandler.reloadLocalization();

            VelocityBaseFeature<?> feature = featureRegistry.getLoadedFeature(featureName);
            feature.cleanup();
            featureRegistry.deregisterLoadedFeature(featureName);

            boolean hasReloaded = loadFeature(featureName);
            if (!hasReloaded) {
                plugin.getLogger().error("Reload failed for: {} (feature did not load back)", featureName);
                return new FeatureReloadResponse(FeatureReloadResult.FAILED, featureName, reloadedDependents);
            }

            plugin.getLogger().info("Feature {} reloaded.", featureName);

            // Reload dependents automatically (best-effort)
            for (String dependent : dependencyManager.getDependentFeatures(featureName)) {
                plugin.getLogger().info("Reloading dependent feature: {}", dependent);
                FeatureReloadResponse depResp = reloadFeature(dependent);
                if (depResp.success()) reloadedDependents.add(dependent);
            }

            return new FeatureReloadResponse(FeatureReloadResult.SUCCESS, featureName, reloadedDependents);
        } catch (Exception t) {
            plugin.getLogger().error("Reload failed for: {}", featureName, t);
            return new FeatureReloadResponse(FeatureReloadResult.FAILED, featureName, reloadedDependents);
        }
    }

    /**
     * Internal: load & initialize a feature if enabled and deps are met.
     * Returns true if the feature ended up loaded.
     */
    public boolean loadFeature(String featureName) {
        if (featureRegistry.isFeatureLoaded(featureName)) {
            plugin.getLogger().warn("Feature already loaded: {}", featureName);
            return false;
        }

        Class<? extends VelocityBaseFeature<?>> featureClass = featureRegistry.getAvailableFeatures().get(featureName);
        if (featureClass == null) {
            plugin.getLogger().error("Feature {} is not registered and cannot be loaded.", featureName);
            return false;
        }

        VelocityBaseFeature<?> feature = FeatureFactory.createFeature(featureClass, plugin);
        if (feature == null) return false;

        mainConfigHandler.registerFeature(featureName);
        mainConfigHandler.injectFeatureDefaults(featureName, feature.getDefaultConfig());
        localizationHandler.registerDefaultMessages(feature.getDefaultMessages());

        if (mainConfigHandler.isFeatureEnabled(featureName)) {
            if (!dependencyManager.areDependenciesMet(feature)) {
                plugin.getLogger().warn("Feature {} is missing dependencies and cannot be enabled.", featureName);
                return false;
            }

            try {
                feature.initialize();
                featureRegistry.registerLoadedFeature(featureName, feature);
                plugin.getLogger().info("Feature loaded: {}", featureName);
                return true;
            } catch (Exception t) {
                plugin.getLogger().error("Feature {} failed during initialization.", featureName, t);
                try {
                    feature.cleanup();
                } catch (Exception cleanupFailure) {
                    plugin.getLogger().error("Feature {} cleanup after failed initialization also failed.", featureName, cleanupFailure);
                }
                return false;
            }
        }

        return false;
    }

    public FeatureRegistry getFeatureRegistry() {
        return featureRegistry;
    }

    public void unloadAllFeatures() {
        plugin.getLogger().info("Unloading all loaded features...");
        List<String> loadedFeatureNames = new ArrayList<>(featureRegistry.getLoadedFeatureNames());
        for (String featureName : loadedFeatureNames) {
            VelocityBaseFeature<?> feature = featureRegistry.getLoadedFeature(featureName);
            if (feature == null) {
                featureRegistry.deregisterLoadedFeature(featureName);
                continue;
            }
            try {
                feature.cleanup();
            } catch (Exception t) {
                plugin.getLogger().error("Error during cleanup of feature {}", feature.getFeatureName(), t);
            } finally {
                featureRegistry.deregisterLoadedFeature(featureName);
            }
        }
        plugin.getLogger().info("All features have been unloaded.");
    }

    /**
     * Diagnose missing plugin + feature dependencies for a feature.
     * For plugin deps we look for an Optional plugin container in Velocity's PluginManager.
     * Velocity doesn't expose "enabled" state, so presence is considered sufficient.
     */
    private DependencyCheckResult diagnoseDependencies(VelocityBaseFeature<?> feature) {
        Set<String> missingPlugins = new LinkedHashSet<>();
        Set<String> missingFeatures = new LinkedHashSet<>();

        try {
            Collection<String> pluginDeps = Optional.ofNullable(feature.getPluginDependencies()).orElse(List.of());
            for (String name : pluginDeps) {
                var optional = plugin.getPluginManager().getPlugin(name);
                if (optional.isEmpty()) {
                    missingPlugins.add(name);
                }
            }
        } catch (Exception t) {
            plugin.getLogger().warn("Failed to read plugin dependencies for {}", feature.getFeatureName(), t);
        }

        // Feature dependencies
        for (String dep : feature.getDependencies()) {
            if (!featureRegistry.isFeatureLoaded(dep)) {
                missingFeatures.add(dep);
            }
        }

        return new DependencyCheckResult(missingPlugins, missingFeatures);
    }
}
