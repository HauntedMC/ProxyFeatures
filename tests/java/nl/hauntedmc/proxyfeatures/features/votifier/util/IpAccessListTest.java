package nl.hauntedmc.proxyfeatures.features.votifier.util;

import nl.hauntedmc.proxyfeatures.framework.log.FeatureLogger;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IpAccessListTest {

    @Test
    void denyEntriesOverrideAllowEntriesAndEmptyAllowlistDefaultsToAllow() throws Exception {
        FeatureLogger logger = mock(FeatureLogger.class);
        IpAccessList list = IpAccessList.fromCsv("", "10.0.0.1", logger);

        assertFalse(list.allows(InetAddress.getByName("10.0.0.1")));
        assertTrue(list.allows(InetAddress.getByName("8.8.8.8")));
        assertFalse(list.allows(null));
    }

    @Test
    void allowlistSupportsIpPortAndCidrEntriesIncludingPartialMasks() throws Exception {
        FeatureLogger logger = mock(FeatureLogger.class);
        IpAccessList list = IpAccessList.fromCsv("1.2.3.4:25565,192.168.0.0/16,10.0.128.0/17", "", logger);

        assertTrue(list.allows(InetAddress.getByName("1.2.3.4")));
        assertTrue(list.allows(InetAddress.getByName("192.168.5.9")));
        assertTrue(list.allows(InetAddress.getByName("10.0.200.1")));
        assertFalse(list.allows(InetAddress.getByName("10.0.10.1")));
        assertFalse(list.allows(InetAddress.getByName("8.8.8.8")));
    }

    @Test
    void invalidEntriesAreIgnoredAndLogged() {
        FeatureLogger logger = mock(FeatureLogger.class);
        IpAccessList list = IpAccessList.fromCsv("bad,10.0.0.0/not-a-number,999.999.999.999", "not-ip", logger);

        assertTrue(list.allows(localhost()));
        verify(logger, atLeast(2)).warn(contains("Invalid"));
    }

    @Test
    void invalidEntriesWithoutLoggerDoNotThrow() {
        IpAccessList list = IpAccessList.fromCsv("bad,10.0.0.0/not-a-number", "", null);
        assertTrue(list.allows(localhost()));
    }

    private static InetAddress localhost() {
        try {
            return InetAddress.getByName("127.0.0.1");
        } catch (Exception e) {
            fail(e);
            return null;
        }
    }
}
