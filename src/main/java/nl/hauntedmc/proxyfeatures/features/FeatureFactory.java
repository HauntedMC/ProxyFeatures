package nl.hauntedmc.proxyfeatures.features;

import nl.hauntedmc.proxyfeatures.ProxyFeatures;

public class FeatureFactory {

    public static VelocityBaseFeature<?> createFeature(String featureClassName, ProxyFeatures plugin) {
        if (featureClassName == null || featureClassName.isBlank()) {
            plugin.getLogger().error("Failed to instantiate feature: missing feature class name.");
            return null;
        }

        try {
            Class<?> rawClass = Class.forName(featureClassName, true, plugin.getClass().getClassLoader());
            if (!VelocityBaseFeature.class.isAssignableFrom(rawClass)) {
                plugin.getLogger().error("Feature class does not extend VelocityBaseFeature: {}", featureClassName);
                return null;
            }

            @SuppressWarnings("unchecked")
            Class<? extends VelocityBaseFeature<?>> featureClass = (Class<? extends VelocityBaseFeature<?>>) rawClass;
            return featureClass.getDeclaredConstructor(ProxyFeatures.class).newInstance(plugin);
        } catch (ReflectiveOperationException | LinkageError t) {
            plugin.getLogger().error("Failed to instantiate feature class: {}", featureClassName, t);
            return null;
        }
    }
}
