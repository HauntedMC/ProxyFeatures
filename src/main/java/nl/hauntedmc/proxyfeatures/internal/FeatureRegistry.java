package nl.hauntedmc.proxyfeatures.internal;

import nl.hauntedmc.proxyfeatures.features.VelocityBaseFeature;

import java.util.*;

public class FeatureRegistry {
    private final Map<String, VelocityBaseFeature<?>> loadedFeatures = new HashMap<>();
    private final Map<String, Class<? extends VelocityBaseFeature<?>>> availableFeatures = new HashMap<>();

    public void registerAvailableFeature(String featureName, Class<? extends VelocityBaseFeature<?>> featureClass) {
        availableFeatures.put(featureName, featureClass);
    }

    public void registerLoadedFeature(String featureName, VelocityBaseFeature<?> feature) {
        loadedFeatures.put(featureName, feature);
    }

    public void deregisterLoadedFeature(String featureName) {
        loadedFeatures.remove(featureName);
    }

    public VelocityBaseFeature<?> getLoadedFeature(String featureName) {
        return loadedFeatures.get(featureName);
    }

    public Set<String> getLoadedFeatureNames() {
        return loadedFeatures.keySet();
    }

    public boolean isFeatureLoaded(String featureName) {
        return loadedFeatures.containsKey(featureName);
    }

    public Map<String, Class<? extends VelocityBaseFeature<?>>> getAvailableFeatures() {
        return availableFeatures;
    }

    public List<VelocityBaseFeature<?>> getLoadedFeatures() {
        return new ArrayList<>(loadedFeatures.values());
    }

}
