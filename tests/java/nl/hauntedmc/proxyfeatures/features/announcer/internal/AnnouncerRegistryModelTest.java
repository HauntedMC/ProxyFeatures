package nl.hauntedmc.proxyfeatures.features.announcer.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AnnouncerRegistryModelTest {

    @Test
    void announcementRecordStoresValues() {
        AnnouncerRegistry.Announcement a = new AnnouncerRegistry.Announcement("announcer.text1", 3);
        AnnouncerRegistry.Announcement b = new AnnouncerRegistry.Announcement("announcer.text1", 3);
        AnnouncerRegistry.Announcement c = new AnnouncerRegistry.Announcement("announcer.text2", 1);

        assertEquals("announcer.text1", a.key());
        assertEquals(3, a.weight());
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }
}
