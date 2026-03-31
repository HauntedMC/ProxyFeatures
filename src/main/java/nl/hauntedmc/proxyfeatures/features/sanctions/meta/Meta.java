package nl.hauntedmc.proxyfeatures.features.sanctions.meta;


import nl.hauntedmc.proxyfeatures.api.feature.meta.BaseMeta;

import java.util.List;

public class Meta implements BaseMeta {

    @Override
    public String getFeatureName() {
        return "Sanctions";
    }

    @Override
    public String getFeatureVersion() {
        return "1.1.1";
    }

    @Override
    public List<String> getPluginDependencies() {
        return List.of(DATA_PROVIDER, DATA_REGISTRY);
    }
}
