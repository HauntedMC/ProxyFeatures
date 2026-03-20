package nl.hauntedmc.proxyfeatures.features.antivpn.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AntiVPNServiceModelsTest {

    @Test
    void nestedPolicyAndEvaluationModelsAreReachable() {
        AntiVPNService.LookupDebug debug = new AntiVPNService.LookupDebug(
                IPCheckResult.of("NL", true, "provider"),
                "mem"
        );
        assertEquals("NL", debug.result().countryCode());
        assertEquals("mem", debug.cacheSource());

        AntiVPNService.Evaluation eval = new AntiVPNService.Evaluation(
                false,
                "antivpn.denied.vpn",
                "NL",
                true,
                "provider",
                "disk",
                "none"
        );
        assertFalse(eval.allowed());
        assertEquals("antivpn.denied.vpn", eval.denyMessageKey());
        assertEquals("NL", eval.countryUpper());
        assertEquals(Boolean.TRUE, eval.vpn());
        assertEquals("provider", eval.providerId());
        assertEquals("disk", eval.cacheSource());
        assertEquals("none", eval.error());

        assertEquals(AntiVPNService.PolicyDecision.ALLOW, AntiVPNService.PolicyDecision.valueOf("ALLOW"));
        assertEquals(AntiVPNService.PolicyDecision.DENY, AntiVPNService.PolicyDecision.valueOf("DENY"));
    }
}
