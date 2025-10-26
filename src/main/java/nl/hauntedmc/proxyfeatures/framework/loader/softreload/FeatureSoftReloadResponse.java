package nl.hauntedmc.proxyfeatures.framework.loader.softreload;

public record FeatureSoftReloadResponse(
        FeatureSoftReloadResult result,
        String feature
) {
    public boolean success() {
        return result == FeatureSoftReloadResult.SUCCESS;
    }
}
