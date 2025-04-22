package nl.hauntedmc.proxyfeatures.features.motd.meta;


import nl.hauntedmc.commonlib.featureapi.meta.BaseMeta;

public class Meta implements BaseMeta {

    @Override
    public String getFeatureName() {
        return "Motd";
    }

    @Override
    public String getFeatureVersion() {
        return "1.0.0";
    }
}
