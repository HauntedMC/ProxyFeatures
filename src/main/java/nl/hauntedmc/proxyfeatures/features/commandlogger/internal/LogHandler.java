package nl.hauntedmc.proxyfeatures.features.commandlogger.internal;

import nl.hauntedmc.proxyfeatures.features.commandlogger.CommandLogger;

public class LogHandler {

    private final CommandLogger feature;

    public LogHandler(CommandLogger feature) {
        this.feature = feature;
    }

    /**
     * Logt een proxy-commando (dus geverifieerd via CommandManager.hasCommand).
     *
     * @param who  beschrijving van de bron (bijv. "Notch (uuid)" of "console")
     * @param full volledige commandline zonder leading slash, bv. "velocity info"
     */
    public void logProxyCommand(String who, String full) {
        feature.getLogger().info(who + " executed command: " + "/" + full);
    }
}
