package nl.hauntedmc.proxyfeatures.internal.action.softreload;

public record FeatureSoftReloadResponse(
        FeatureSoftReloadResult result,
        String feature
) {
    public boolean success() {
        return result == FeatureSoftReloadResult.SUCCESS;
    }
}
