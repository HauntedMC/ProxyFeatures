package nl.hauntedmc.proxyfeatures.features.hlink.command;

import nl.hauntedmc.proxyfeatures.features.hlink.internal.HLinkHandler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HLinkResultPolicyTest {

    @Test
    void evaluateReturnsErrorForFailureCases() {
        assertEquals(HLinkResultPolicy.Outcome.ERROR, HLinkResultPolicy.evaluate(null, null).outcome());
        assertEquals(HLinkResultPolicy.Outcome.ERROR, HLinkResultPolicy.evaluate(
                new HLinkHandler.LinkResult(HLinkHandler.LinkResultType.ERROR, null),
                null
        ).outcome());
        assertEquals(HLinkResultPolicy.Outcome.ERROR, HLinkResultPolicy.evaluate(
                new HLinkHandler.LinkResult(HLinkHandler.LinkResultType.SUCCESS, "token"),
                new RuntimeException("boom")
        ).outcome());
    }

    @Test
    void evaluateReturnsAlreadyRegisteredWhenAlreadyLinked() {
        HLinkResultPolicy.Decision decision = HLinkResultPolicy.evaluate(
                new HLinkHandler.LinkResult(HLinkHandler.LinkResultType.ALREADY_REGISTERED, null),
                null
        );
        assertEquals(HLinkResultPolicy.Outcome.ALREADY_REGISTERED, decision.outcome());
        assertNull(decision.token());
    }

    @Test
    void evaluateReturnsSuccessOnlyWithNonBlankToken() {
        HLinkResultPolicy.Decision success = HLinkResultPolicy.evaluate(
                new HLinkHandler.LinkResult(HLinkHandler.LinkResultType.SUCCESS, "abc123"),
                null
        );
        assertEquals(HLinkResultPolicy.Outcome.SUCCESS, success.outcome());
        assertEquals("abc123", success.token());

        HLinkResultPolicy.Decision blank = HLinkResultPolicy.evaluate(
                new HLinkHandler.LinkResult(HLinkHandler.LinkResultType.SUCCESS, " "),
                null
        );
        assertEquals(HLinkResultPolicy.Outcome.ERROR, blank.outcome());
    }
}
