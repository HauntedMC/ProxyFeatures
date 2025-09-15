package nl.hauntedmc.proxyfeatures.features.friends.meta;


import nl.hauntedmc.commonlib.featureapi.feature.meta.BaseMeta;

import java.util.List;

public class Meta implements BaseMeta {

    @Override
    public String getFeatureName() {
        return "Friends";
    }

    @Override
    public String getFeatureVersion() {
        return "1.2.0";
    }

    @Override
    public List<String> getDependencies() {
        return List.of("Vanish");
    }
}
