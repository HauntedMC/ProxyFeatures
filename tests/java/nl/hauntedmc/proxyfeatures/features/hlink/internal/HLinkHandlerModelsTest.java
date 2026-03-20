package nl.hauntedmc.proxyfeatures.features.hlink.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HLinkHandlerModelsTest {

    @Test
    void linkResultAndTypeAreReachable() {
        HLinkHandler.LinkResult result = new HLinkHandler.LinkResult(HLinkHandler.LinkResultType.SUCCESS, "token");

        assertEquals(HLinkHandler.LinkResultType.SUCCESS, result.type());
        assertEquals("token", result.token());
        assertEquals(HLinkHandler.LinkResultType.ERROR, HLinkHandler.LinkResultType.valueOf("ERROR"));
    }
}
