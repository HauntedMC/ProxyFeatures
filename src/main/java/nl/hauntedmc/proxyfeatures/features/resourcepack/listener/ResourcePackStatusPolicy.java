package nl.hauntedmc.proxyfeatures.features.resourcepack.listener;

import com.velocitypowered.api.event.player.PlayerResourcePackStatusEvent;

final class ResourcePackStatusPolicy {

    enum Action {
        NONE,
        DISCONNECT,
        MESSAGE
    }

    record Resolution(boolean unblockConfiguration, Action action, String localizationKey) {
    }

    private ResourcePackStatusPolicy() {
    }

    static Resolution resolve(PlayerResourcePackStatusEvent.Status status) {
        if (status == null) {
            return new Resolution(false, Action.NONE, null);
        }

        return switch (status) {
            case ACCEPTED, DOWNLOADED -> new Resolution(false, Action.NONE, null);
            case SUCCESSFUL -> new Resolution(true, Action.NONE, null);
            case DECLINED, DISCARDED -> new Resolution(true, Action.DISCONNECT, "resourcepack.kick_declined");
            case FAILED_DOWNLOAD -> new Resolution(true, Action.DISCONNECT, "resourcepack.kick_failed");
            case FAILED_RELOAD -> new Resolution(true, Action.MESSAGE, "resourcepack.reload_failed");
            case INVALID_URL -> new Resolution(true, Action.MESSAGE, "resourcepack.url_invalid");
        };
    }
}
