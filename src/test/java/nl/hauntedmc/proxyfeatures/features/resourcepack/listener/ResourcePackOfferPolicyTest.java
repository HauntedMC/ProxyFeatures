package nl.hauntedmc.proxyfeatures.features.resourcepack.listener;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResourcePackOfferPolicyTest {

    @Test
    void resolveReturnsResumeWhenPackDoesNotExist() {
        assertEquals(ResourcePackOfferPolicy.Action.RESUME, ResourcePackOfferPolicy.resolve(false, false));
    }

    @Test
    void resolveReturnsResumeWhenPackIsAlreadyApplied() {
        assertEquals(ResourcePackOfferPolicy.Action.RESUME, ResourcePackOfferPolicy.resolve(true, true));
    }

    @Test
    void resolveReturnsSendOfferWhenPackExistsAndNotApplied() {
        assertEquals(ResourcePackOfferPolicy.Action.SEND_OFFER, ResourcePackOfferPolicy.resolve(true, false));
    }
}
