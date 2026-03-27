package nl.hauntedmc.proxyfeatures.features.friends.meta;


import nl.hauntedmc.proxyfeatures.api.feature.meta.BaseMeta;

import java.util.List;

public class Meta implements BaseMeta {

    @Override
    public String getFeatureName() {
        return "Friends";
    }

    @Override
    public String getFeatureVersion() {
        return "1.3.0";
    }

    @Override
    public List<String> getDependencies() {
        return List.of();
    }
}
