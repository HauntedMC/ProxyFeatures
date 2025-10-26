package nl.hauntedmc.proxyfeatures.features.playerlist.meta;


import nl.hauntedmc.proxyfeatures.api.feature.meta.BaseMeta;

import java.util.List;

public class Meta implements BaseMeta {

    @Override
    public String getFeatureName() {
        return "PlayerList";
    }

    @Override
    public String getFeatureVersion() {
        return "1.0.0";
    }

    @Override
    public List<String> getDependencies() {
        return List.of("Vanish");
    }
}
