package nl.hauntedmc.proxyfeatures.framework.loader.dependency;

import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.framework.loader.FeatureDescriptor;
import nl.hauntedmc.proxyfeatures.framework.loader.FeatureLoadManager;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FeatureDependencyManager {

    private final FeatureLoadManager featureLoadManager;
    private final ProxyFeatures plugin;

    public FeatureDependencyManager(FeatureLoadManager featureLoadManager, ProxyFeatures plugin) {
        this.featureLoadManager = featureLoadManager;
        this.plugin = plugin;
    }

    /**
     * Ensures that all dependencies of a feature are enabled before loading.
     */
    public boolean areDependenciesMet(String featureName) {
        String featureKey = featureLoadManager.resolveFeatureKey(featureName);
        if (featureKey == null) {
            plugin.getLogger().warn("Feature not found in registry: {}", featureName);
            return false;
        }

        return checkDependencies(featureKey, new HashSet<>(), new HashSet<>())
                && arePluginDependenciesMet(featureKey);
    }

    /**
     * Recursively checks dependencies and ensures they are enabled.
     */
    private boolean checkDependencies(String featureName, Set<String> activePath, Set<String> resolved) {
        if (featureLoadManager.getFeatureRegistry().isFeatureLoaded(featureName)) {
            resolved.add(featureName);
            return true;
        }
        if (resolved.contains(featureName)) {
            return true;
        }

        if (!activePath.add(featureName)) {
            plugin.getLogger().warn("Circular dependency detected: {}", featureName);
            return false;
        }

        try {
            FeatureDescriptor descriptor = featureLoadManager.getFeatureRegistry().getAvailableFeature(featureName);
            if (descriptor == null) {
                plugin.getLogger().warn("Feature not found in registry: {}", featureName);
                return false;
            }

            for (String dependency : descriptor.featureDependencies()) {
                String dependencyKey = featureLoadManager.resolveFeatureKey(dependency);
                if (dependencyKey == null) {
                    plugin.getLogger().warn("Missing dependency '{}' for feature {}", dependency, featureName);
                    return false;
                }

                if (!checkDependencies(dependencyKey, activePath, resolved)) {
                    return false;
                }

                if (!featureLoadManager.getFeatureRegistry().isFeatureLoaded(dependencyKey)) {
                    plugin.getLogger().info("Enabling dependency {} for {}", dependencyKey, featureName);
                    if (!featureLoadManager.loadFeature(dependencyKey)) {
                        return false;
                    }
                }
            }

            resolved.add(featureName);
            return true;
        } finally {
            activePath.remove(featureName);
        }
    }

    /**
     * Checks if all required external plugins are loaded.
     */
    public boolean arePluginDependenciesMet(String featureName) {
        Set<String> missingPlugins = featureLoadManager.getMissingPluginDependencies(featureName);
        if (!missingPlugins.isEmpty()) {
            plugin.getLogger().warn(
                    "Cannot enable {} because required plugin(s) {} are missing.",
                    featureName,
                    String.join(", ", missingPlugins)
            );
            return false;
        }
        return true;
    }

    /**
     * Finds features that depend on a given feature.
     */
    public List<String> getDependentFeatures(String featureName) {
        String targetKey = featureLoadManager.resolveFeatureKey(featureName);
        if (targetKey == null) {
            return List.of();
        }

        return featureLoadManager.getFeatureRegistry().getLoadedFeatureNames().stream()
                .filter(name -> {
                    FeatureDescriptor descriptor = featureLoadManager.getFeatureRegistry().getAvailableFeature(name);
                    if (descriptor == null) {
                        return false;
                    }

                    for (String dependency : descriptor.featureDependencies()) {
                        String dependencyKey = featureLoadManager.resolveFeatureKey(dependency);
                        if (targetKey.equals(dependencyKey)) {
                            return true;
                        }
                    }
                    return false;
                })
                .toList();
    }
}
