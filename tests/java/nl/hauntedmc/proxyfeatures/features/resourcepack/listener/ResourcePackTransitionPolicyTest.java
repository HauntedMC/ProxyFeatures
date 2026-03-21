package nl.hauntedmc.proxyfeatures.features.resourcepack.listener;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourcePackTransitionPolicyTest {

    @Test
    void firstConnectAlwaysOffersGlobalPack() {
        ResourcePackTransitionPolicy.TransitionPlan plan = ResourcePackTransitionPolicy.plan(
                false,
                "survival",
                false,
                false
        );

        assertFalse(plan.removePreviousPack());
        assertEquals("global", plan.offerPackIdentifier());
        assertFalse(plan.resumeImmediately());
    }

    @Test
    void switchWithServerPackRemovesPreviousAndOffersCurrent() {
        ResourcePackTransitionPolicy.TransitionPlan plan = ResourcePackTransitionPolicy.plan(
                true,
                "skyblock",
                true,
                true
        );

        assertTrue(plan.removePreviousPack());
        assertEquals("skyblock", plan.offerPackIdentifier());
        assertFalse(plan.resumeImmediately());
    }

    @Test
    void switchWithoutCurrentPackResumesConfiguration() {
        ResourcePackTransitionPolicy.TransitionPlan plan = ResourcePackTransitionPolicy.plan(
                true,
                "skyblock",
                true,
                false
        );

        assertTrue(plan.removePreviousPack());
        assertNull(plan.offerPackIdentifier());
        assertTrue(plan.resumeImmediately());
    }
}
