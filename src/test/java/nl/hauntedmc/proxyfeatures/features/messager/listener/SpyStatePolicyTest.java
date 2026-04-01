package nl.hauntedmc.proxyfeatures.features.messager.listener;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpyStatePolicyTest {

    @Test
    void reconcileReturnsEnableWhenPermittedAndCurrentlyDisabled() {
        assertEquals(SpyStatePolicy.Action.ENABLE, SpyStatePolicy.reconcile(true, false));
    }

    @Test
    void reconcileReturnsDisableWhenNotPermittedAndCurrentlyEnabled() {
        assertEquals(SpyStatePolicy.Action.DISABLE, SpyStatePolicy.reconcile(false, true));
    }

    @Test
    void reconcileReturnsNoneWhenStateAlreadyMatchesPermission() {
        assertEquals(SpyStatePolicy.Action.NONE, SpyStatePolicy.reconcile(true, true));
        assertEquals(SpyStatePolicy.Action.NONE, SpyStatePolicy.reconcile(false, false));
    }
}
