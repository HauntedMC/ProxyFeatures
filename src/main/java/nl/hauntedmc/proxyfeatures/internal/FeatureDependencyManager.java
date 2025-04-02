package nl.hauntedmc.proxyfeatures.internal;

import com.velocitypowered.api.plugin.PluginContainer;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.features.BaseFeature;
import nl.hauntedmc.proxyfeatures.features.FeatureFactory;

import java.io.Serializable;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class FeatureDependencyManager implements Serializable {

    private final FeatureLoadManager featureLoadManager;
    private final ProxyFeatures plugin;

    public FeatureDependencyManager(FeatureLoadManager featureLoadManager, ProxyFeatures plugin) {
        this.featureLoadManager = featureLoadManager;
        this.plugin = plugin;
    }

    /**
     * Ensures that all dependencies of a feature are enabled before loading.
     */
    public boolean areDependenciesMet(BaseFeature<?> feature) {
        return checkDependencies(feature, new HashSet<>()) && arePluginDependenciesMet(feature);
    }

    /**
     * Recursively checks dependencies and ensures they are enabled.
     */
    private boolean checkDependencies(BaseFeature<?> feature, Set<String> visited) {
        String featureName = feature.getFeatureName();

        // 🔹 Prevent circular dependencies
        if (visited.contains(featureName)) {
            plugin.getLogger().warn("Circular dependency detected: {}", featureName);
            return false;
        }

        visited.add(featureName);
        List<String> dependencies = feature.getDependencies();

        for (String dependency : dependencies) {
            if (!featureLoadManager.getFeatureRegistry().isFeatureLoaded(dependency)) {
                plugin.getLogger().info("Enabling dependency {} for {}", dependency, featureName);

                BaseFeature<?> dependencyFeature = FeatureFactory.createFeature(
                        featureLoadManager.getFeatureRegistry().getAvailableFeatures().get(dependency),
                        this.plugin
                );

                if (dependencyFeature == null) {
                    plugin.getLogger().warn("Failed to instantiate dependency {} for {}", dependency, featureName);
                    return false;
                }

                // Recursively check dependencies
                if (!checkDependencies(dependencyFeature, visited)) {
                    return false;
                }

                if (!featureLoadManager.loadFeature(dependency)) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Checks if all required external plugins are loaded.
     */
    public boolean arePluginDependenciesMet(BaseFeature<?> feature) {
        List<String> requiredPlugins = feature.getPluginDependencies();

        for (String pluginName : requiredPlugins) {
            if (!isPluginLoaded(pluginName)) {
                plugin.getLogger().warn("Cannot enable {} because required plugin {} is missing.", feature.getFeatureName(), pluginName);
                return false;
            }
        }
        return true;
    }

    /**
     * Checks if a specific plugin is loaded.
     */
    public boolean isPluginLoaded(String pluginName) {
        Optional<PluginContainer> foundPlugin = plugin.getPluginManager().getPlugin(pluginName);
        return foundPlugin.isPresent();
    }

    /**
     * Finds features that depend on a given feature.
     */
    public List<String> getDependentFeatures(String featureName) {
        return featureLoadManager.getFeatureRegistry().getLoadedFeatureNames().stream()
                .filter(name -> featureLoadManager.getFeatureRegistry().getLoadedFeature(name)
                        .getDependencies().contains(featureName))
                .toList();
    }
}
