package nl.hauntedmc.proxyfeatures.features.vanish.internal.messaging;

import nl.hauntedmc.dataprovider.database.messaging.api.AbstractEventMessage;

/**
 * Mirrors the Bukkit-side message (type "vanish_update").
 */
public final class VanishStateMessage extends AbstractEventMessage {

    private final String playerUuid;
    private final String playerName;
    private final boolean vanished;
    private final String server; // origin server name (optional)

    // No-arg ctor for Gson
    @SuppressWarnings("unused")
    private VanishStateMessage() {
        super("vanish_update");
        this.playerUuid = null;
        this.playerName = null;
        this.vanished = false;
        this.server = null;
    }

    public VanishStateMessage(String type,
                              String playerUuid,
                              String playerName,
                              boolean vanished,
                              String server) {
        super(type);
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.vanished = vanished;
        this.server = server;
    }

    public String getPlayerUuid() {
        return playerUuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public boolean isVanished() {
        return vanished;
    }

    public String getServer() {
        return server;
    }
}
