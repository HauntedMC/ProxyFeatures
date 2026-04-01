package nl.hauntedmc.proxyfeatures.features.resourcepack.listener;

import com.velocitypowered.api.event.player.PlayerResourcePackStatusEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourcePackStatusPolicyTest {

    @Test
    void acceptedAndDownloadedDoNothing() {
        ResourcePackStatusPolicy.Resolution accepted = ResourcePackStatusPolicy.resolve(PlayerResourcePackStatusEvent.Status.ACCEPTED);
        ResourcePackStatusPolicy.Resolution downloaded = ResourcePackStatusPolicy.resolve(PlayerResourcePackStatusEvent.Status.DOWNLOADED);

        assertFalse(accepted.unblockConfiguration());
        assertEquals(ResourcePackStatusPolicy.Action.NONE, accepted.action());
        assertNull(accepted.localizationKey());

        assertFalse(downloaded.unblockConfiguration());
        assertEquals(ResourcePackStatusPolicy.Action.NONE, downloaded.action());
        assertNull(downloaded.localizationKey());
    }

    @Test
    void successfulOnlyUnblocksConfiguration() {
        ResourcePackStatusPolicy.Resolution resolution = ResourcePackStatusPolicy.resolve(
                PlayerResourcePackStatusEvent.Status.SUCCESSFUL
        );

        assertTrue(resolution.unblockConfiguration());
        assertEquals(ResourcePackStatusPolicy.Action.NONE, resolution.action());
        assertNull(resolution.localizationKey());
    }

    @Test
    void declinedAndDiscardedDisconnectWithDeclinedMessage() {
        ResourcePackStatusPolicy.Resolution declined = ResourcePackStatusPolicy.resolve(
                PlayerResourcePackStatusEvent.Status.DECLINED
        );
        ResourcePackStatusPolicy.Resolution discarded = ResourcePackStatusPolicy.resolve(
                PlayerResourcePackStatusEvent.Status.DISCARDED
        );

        assertTrue(declined.unblockConfiguration());
        assertEquals(ResourcePackStatusPolicy.Action.DISCONNECT, declined.action());
        assertEquals("resourcepack.kick_declined", declined.localizationKey());

        assertTrue(discarded.unblockConfiguration());
        assertEquals(ResourcePackStatusPolicy.Action.DISCONNECT, discarded.action());
        assertEquals("resourcepack.kick_declined", discarded.localizationKey());
    }

    @Test
    void failedDownloadDisconnectsWithFailedMessage() {
        ResourcePackStatusPolicy.Resolution resolution = ResourcePackStatusPolicy.resolve(
                PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD
        );

        assertTrue(resolution.unblockConfiguration());
        assertEquals(ResourcePackStatusPolicy.Action.DISCONNECT, resolution.action());
        assertEquals("resourcepack.kick_failed", resolution.localizationKey());
    }

    @Test
    void failedReloadAndInvalidUrlSendMessageWithoutDisconnecting() {
        ResourcePackStatusPolicy.Resolution failedReload = ResourcePackStatusPolicy.resolve(
                PlayerResourcePackStatusEvent.Status.FAILED_RELOAD
        );
        ResourcePackStatusPolicy.Resolution invalidUrl = ResourcePackStatusPolicy.resolve(
                PlayerResourcePackStatusEvent.Status.INVALID_URL
        );

        assertTrue(failedReload.unblockConfiguration());
        assertEquals(ResourcePackStatusPolicy.Action.MESSAGE, failedReload.action());
        assertEquals("resourcepack.reload_failed", failedReload.localizationKey());

        assertTrue(invalidUrl.unblockConfiguration());
        assertEquals(ResourcePackStatusPolicy.Action.MESSAGE, invalidUrl.action());
        assertEquals("resourcepack.url_invalid", invalidUrl.localizationKey());
    }
}
