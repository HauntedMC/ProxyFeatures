package nl.hauntedmc.proxyfeatures.internal.action.disable;

import java.util.Set;

public record FeatureDisableResponse(
        FeatureDisableResult result,
        String feature,
        Set<String> alsoDisabledDependents
) {
    public boolean success() {
        return result == FeatureDisableResult.SUCCESS;
    }
}
