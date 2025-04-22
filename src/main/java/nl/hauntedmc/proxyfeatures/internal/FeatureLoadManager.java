package nl.hauntedmc.proxyfeatures.internal;

import nl.hauntedmc.commonlib.featureapi.event.FeatureDisabledEvent;
import nl.hauntedmc.commonlib.featureapi.event.FeatureEventManager;
import nl.hauntedmc.commonlib.featureapi.event.FeatureLoadedEvent;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.features.VelocityBaseFeature;
import nl.hauntedmc.proxyfeatures.config.MainConfigHandler;
import nl.hauntedmc.proxyfeatures.features.FeatureFactory;
import nl.hauntedmc.proxyfeatures.localization.LocalizationHandler;

import java.util.*;

public class FeatureLoadManager {

    private final ProxyFeatures plugin;
    private final MainConfigHandler mainConfigHandler;
    private final FeatureRegistry featureRegistry;
    private final FeatureDependencyManager dependencyManager;
    private final LocalizationHandler localizationHandler;

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
                try {
                    Class<?> clazz = Class.forName(classInfo.getName());
                    if (VelocityBaseFeature.class.isAssignableFrom(clazz)) {
                        @SuppressWarnings("unchecked")
                        Class<? extends VelocityBaseFeature<?>> featureClass = (Class<? extends VelocityBaseFeature<?>>) clazz;
                        featureRegistry.registerAvailableFeature(classInfo.getSimpleName(), featureClass);
                    }
                } catch (ClassNotFoundException e) {
                    plugin.getLogger().error("Failed to load feature class: {}", classInfo.getName(), e);
                }
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

        stack.add(featureName);
        visited.add(featureName);

        VelocityBaseFeature<?> feature = FeatureFactory.createFeature(featureRegistry.getAvailableFeatures().get(featureName), plugin);
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
     * Enables and loads a feature dynamically.
     */
    public boolean enableFeature(String featureName) {
        if (!featureRegistry.getAvailableFeatures().containsKey(featureName)) {
            plugin.getLogger().warn("Feature not found: {}", featureName);
            return false;
        }
        mainConfigHandler.setFeatureEnabled(featureName, true);
        return loadFeature(featureName);
    }


    /**
     * Loads and initializes a feature.
     */
    public boolean loadFeature(String featureName) {
        if (featureRegistry.isFeatureLoaded(featureName)) {
            plugin.getLogger().warn("Feature already loaded: {}", featureName);
            return false;
        }

        VelocityBaseFeature<?> feature = FeatureFactory.createFeature(featureRegistry.getAvailableFeatures().get(featureName), plugin);
        if (feature == null) return false;

        mainConfigHandler.registerFeature(featureName);
        mainConfigHandler.injectFeatureDefaults(featureName, feature.getDefaultConfig());
        localizationHandler.registerDefaultMessages(feature.getDefaultMessages());

        if (mainConfigHandler.isFeatureEnabled(featureName)) {
            if (!dependencyManager.areDependenciesMet(feature)) {
                plugin.getLogger().warn("Feature {} is missing dependencies and cannot be enabled.", featureName);
                return false;
            }

            feature.initialize();
            featureRegistry.registerLoadedFeature(featureName, feature);
            plugin.getLogger().info("Feature loaded: {}", featureName);
            FeatureEventManager.triggerEvent(new FeatureLoadedEvent(featureName));
            return true;
        }

        return false;
    }

    /**
     * Disables and unloads a feature dynamically.
     */
    public boolean disableFeature(String featureName) {
        VelocityBaseFeature<?> feature = featureRegistry.getLoadedFeature(featureName);
        if (feature == null) {
            plugin.getLogger().warn("Feature not currently loaded: {}", featureName);
            return false;
        }
        feature.cleanup();
        mainConfigHandler.setFeatureEnabled(featureName, false);
        plugin.getLogger().info("Feature disabled: {}", featureName);
        featureRegistry.deregisterLoadedFeature(featureName);
        FeatureEventManager.triggerEvent(new FeatureDisabledEvent(featureName));

        // Disable dependent features
        for (String dependent : dependencyManager.getDependentFeatures(featureName)) {
            disableFeature(dependent);
        }

        return true;
    }

    public boolean softReloadFeature(String featureName) {
        if (!featureRegistry.isFeatureLoaded(featureName)) {
            plugin.getLogger().warn("Feature not currently loaded: {}", featureName);
            return false;
        }
        VelocityBaseFeature<?> feature = featureRegistry.getLoadedFeature(featureName);
        feature.getConfigHandler().reloadConfig();
        feature.getLocalizationHandler().reloadLocalization();
        plugin.getLogger().info("Feature " + featureName + " soft reloaded.");
        return true;
    }

    /**
     * Reloads a feature dynamically, ensuring dependent features reload afterward.
     */
    public boolean reloadFeature(String featureName) {
        if (!featureRegistry.isFeatureLoaded(featureName)) {
            plugin.getLogger().warn("Feature not currently loaded: {}", featureName);
            return false;
        }

        mainConfigHandler.reloadConfig();
        localizationHandler.reloadLocalization();
        VelocityBaseFeature<?> feature = featureRegistry.getLoadedFeature(featureName);
        feature.cleanup();
        featureRegistry.deregisterLoadedFeature(featureName);

        boolean hasReloaded = loadFeature(featureName);

        if (hasReloaded) {
            plugin.getLogger().info("Feature {} reloaded.", featureName);

            // Reload dependent features automatically
            for (String dependent : dependencyManager.getDependentFeatures(featureName)) {
                plugin.getLogger().info("Reloading dependent feature: {}", dependent);
                reloadFeature(dependent);
            }
        }

        return hasReloaded;
    }

    /**
     * Returns the feature registry for tracking features.
     */
    public FeatureRegistry getFeatureRegistry() {
        return featureRegistry;
    }

    /**
     * Unload all currently loaded features.
     */
    public void unloadAllFeatures() {
        plugin.getLogger().info("Unloading all loaded features...");

        List<VelocityBaseFeature<?>> loadedFeatures = featureRegistry.getLoadedFeatures();

        for (VelocityBaseFeature<?> feature : loadedFeatures) {
            feature.cleanup();
        }

        plugin.getLogger().info("All features have been unloaded.");
    }
}
