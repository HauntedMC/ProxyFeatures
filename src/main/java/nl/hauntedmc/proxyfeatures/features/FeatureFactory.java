package nl.hauntedmc.proxyfeatures.features;

import nl.hauntedmc.proxyfeatures.ProxyFeatures;

public class FeatureFactory {

    public static VelocityBaseFeature<?> createFeature(Class<? extends VelocityBaseFeature<?>> featureClass, ProxyFeatures plugin) {
        try {
            return featureClass.getDeclaredConstructor(ProxyFeatures.class).newInstance(plugin);
        } catch (Exception e) {
            plugin.getLogger().error("Failed to instantiate feature: {} - {}", featureClass.getSimpleName(), e.getMessage());
            return null;
        }
    }
}
