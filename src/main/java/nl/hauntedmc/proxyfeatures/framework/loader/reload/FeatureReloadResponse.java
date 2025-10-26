package nl.hauntedmc.proxyfeatures.framework.loader.reload;

import java.util.Set;

public record FeatureReloadResponse(
        FeatureReloadResult result,
        String feature,
        Set<String> reloadedDependents
) {
    public boolean success() {
        return result == FeatureReloadResult.SUCCESS;
    }
}
