package nl.hauntedmc.proxyfeatures.features.playerinfo.meta;


import nl.hauntedmc.commonlib.featureapi.feature.meta.BaseMeta;

public class Meta implements BaseMeta {

    @Override
    public String getFeatureName() {
        return "PlayerInfo";
    }

    @Override
    public String getFeatureVersion() {
        return "1.0.0";
    }
}
