package nl.hauntedmc.proxyfeatures.features.hlink.meta;


import nl.hauntedmc.proxyfeatures.api.feature.meta.BaseMeta;

import java.util.List;

public class Meta implements BaseMeta {

    @Override
    public String getFeatureName() {
        return "HLink";
    }

    @Override
    public String getFeatureVersion() {
        return "1.1.0";
    }

    @Override
    public List<String> getPluginDependencies() {
        return List.of("luckperms");
    }
}
