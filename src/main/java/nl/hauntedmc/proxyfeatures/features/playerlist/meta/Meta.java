package nl.hauntedmc.proxyfeatures.features.playerlist.meta;


import nl.hauntedmc.commonlib.featureapi.meta.BaseMeta;

public class Meta implements BaseMeta {

    @Override
    public String getFeatureName() {
        return "PlayerList";
    }

    @Override
    public String getFeatureVersion() {
        return "1.0.0";
    }
}
