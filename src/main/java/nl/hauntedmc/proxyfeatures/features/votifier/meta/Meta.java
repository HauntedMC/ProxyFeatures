package nl.hauntedmc.proxyfeatures.features.votifier.meta;


import nl.hauntedmc.proxyfeatures.api.feature.meta.BaseMeta;

import java.util.List;

public class Meta implements BaseMeta {

    @Override
    public String getFeatureName() {
        return "Votifier";
    }

    @Override
    public String getFeatureVersion() {
        return "1.4.0";
    }

    @Override
    public List<String> getPluginDependencies() {
        return List.of(DATA_PROVIDER, DATA_REGISTRY);
    }
}
