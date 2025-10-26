package nl.hauntedmc.proxyfeatures.features.proxyinfo.meta;


import nl.hauntedmc.proxyfeatures.api.feature.meta.BaseMeta;

public class Meta implements BaseMeta {

    @Override
    public String getFeatureName() {
        return "ProxyInfo";
    }

    @Override
    public String getFeatureVersion() {
        return "1.0.0";
    }
}
