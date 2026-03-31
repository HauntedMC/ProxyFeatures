package nl.hauntedmc.proxyfeatures.api.feature.meta;

import java.util.List;

public interface BaseMeta {
    String DATA_PROVIDER = "dataprovider";
    String DATA_REGISTRY = "dataregistry";

    String getFeatureName();

    String getFeatureVersion();

    default List<String> getDependencies() {
        return List.of();
    }

    default List<String> getPluginDependencies() {
        return List.of();
    }
}
