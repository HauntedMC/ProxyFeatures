package nl.hauntedmc.proxyfeatures.features.versioncheck.meta;


import nl.hauntedmc.proxyfeatures.api.feature.meta.BaseMeta;

public class Meta implements BaseMeta {

    @Override
    public String getFeatureName() {
        return "VersionCheck";
    }

    @Override
    public String getFeatureVersion() {
        return "1.1.0";
    }
}
