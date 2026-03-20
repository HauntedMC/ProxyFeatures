package nl.hauntedmc.proxyfeatures.framework.loader;

import nl.hauntedmc.proxyfeatures.framework.loader.disable.FeatureDisableResponse;
import nl.hauntedmc.proxyfeatures.framework.loader.disable.FeatureDisableResult;
import nl.hauntedmc.proxyfeatures.framework.loader.enable.FeatureEnableResponse;
import nl.hauntedmc.proxyfeatures.framework.loader.enable.FeatureEnableResult;
import nl.hauntedmc.proxyfeatures.framework.loader.reload.FeatureReloadResponse;
import nl.hauntedmc.proxyfeatures.framework.loader.reload.FeatureReloadResult;
import nl.hauntedmc.proxyfeatures.framework.loader.softreload.FeatureSoftReloadResponse;
import nl.hauntedmc.proxyfeatures.framework.loader.softreload.FeatureSoftReloadResult;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FeatureResponsesTest {

    @Test
    void enableResponseSuccessAndFailureFlagsWork() {
        FeatureEnableResponse success = new FeatureEnableResponse(FeatureEnableResult.SUCCESS, Set.of(), Set.of());
        FeatureEnableResponse fail = new FeatureEnableResponse(FeatureEnableResult.FAILED, Set.of("plugin"), Set.of("feature"));

        assertTrue(success.success());
        assertFalse(fail.success());
    }

    @Test
    void disableResponseSuccessAndFailureFlagsWork() {
        FeatureDisableResponse success = new FeatureDisableResponse(FeatureDisableResult.SUCCESS, "Queue", Set.of("Friends"));
        FeatureDisableResponse fail = new FeatureDisableResponse(FeatureDisableResult.FAILED, "Queue", Set.of());

        assertTrue(success.success());
        assertFalse(fail.success());
        assertEquals("Queue", success.feature());
        assertEquals(Set.of("Friends"), success.alsoDisabledDependents());
    }

    @Test
    void reloadResponseSuccessAndFailureFlagsWork() {
        FeatureReloadResponse success = new FeatureReloadResponse(FeatureReloadResult.SUCCESS, "Queue", Set.of("Friends"));
        FeatureReloadResponse fail = new FeatureReloadResponse(FeatureReloadResult.FAILED, "Queue", Set.of());

        assertTrue(success.success());
        assertFalse(fail.success());
    }

    @Test
    void softReloadResponseSuccessAndFailureFlagsWork() {
        FeatureSoftReloadResponse success = new FeatureSoftReloadResponse(FeatureSoftReloadResult.SUCCESS, "Queue");
        FeatureSoftReloadResponse fail = new FeatureSoftReloadResponse(FeatureSoftReloadResult.FAILED, "Queue");

        assertTrue(success.success());
        assertFalse(fail.success());
    }
}
