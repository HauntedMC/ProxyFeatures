package nl.hauntedmc.proxyfeatures.features.versioncheck.meta;

import nl.hauntedmc.proxyfeatures.features.BaseMeta;

public class Meta implements BaseMeta {

    @Override
    public String getFeatureName() {
        return "VersionCheck";
    }

    @Override
    public String getFeatureVersion() {
        return "1.0.0";
    }
}
