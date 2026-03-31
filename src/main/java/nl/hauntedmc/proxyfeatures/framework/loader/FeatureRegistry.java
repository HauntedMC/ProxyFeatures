package nl.hauntedmc.proxyfeatures.framework.loader;


import nl.hauntedmc.proxyfeatures.features.VelocityBaseFeature;

import java.util.*;

public class FeatureRegistry {
    private final Map<String, VelocityBaseFeature<?>> loadedFeatures = new LinkedHashMap<>();
    private final Map<String, FeatureDescriptor> availableFeatures = new LinkedHashMap<>();

    public void registerAvailableFeature(FeatureDescriptor descriptor) {
        if (descriptor == null || descriptor.registryName() == null || descriptor.registryName().isBlank()) {
            return;
        }
        availableFeatures.put(descriptor.registryName(), descriptor);
    }

    public void deregisterAvailableFeature(String featureName) {
        availableFeatures.remove(featureName);
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
        return Collections.unmodifiableSet(new LinkedHashSet<>(loadedFeatures.keySet()));
    }

    public boolean isFeatureLoaded(String featureName) {
        return loadedFeatures.containsKey(featureName);
    }

    public Map<String, FeatureDescriptor> getAvailableFeatures() {
        return Collections.unmodifiableMap(availableFeatures);
    }

    public FeatureDescriptor getAvailableFeature(String featureName) {
        return availableFeatures.get(featureName);
    }

    public List<VelocityBaseFeature<?>> getLoadedFeatures() {
        return new ArrayList<>(loadedFeatures.values());
    }

}
