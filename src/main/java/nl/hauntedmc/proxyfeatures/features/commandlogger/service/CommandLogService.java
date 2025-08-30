package nl.hauntedmc.proxyfeatures.features.commandlogger.service;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.proxyfeatures.features.commandlogger.CommandLogger;
import nl.hauntedmc.proxyfeatures.features.commandlogger.entity.CommandExecutionEntity;

import java.util.Locale;

public class CommandLogService {

    private static final String PROXY_SERVER_NAME = "proxy";

    private final CommandLogger feature;

    public CommandLogService(CommandLogger feature) {
        this.feature = feature;
    }

    /**
     * Slaat een geverifieerde command-executie op in de database.
     * "server" wordt altijd "proxy" voor Velocity.
     *
     * @param source CommandSource (speler of console, etc.)
     * @param fullCommand command zonder leading slash (zoals ontvangen van het event)
     */
    public void logProxyCommand(CommandSource source, String fullCommand) {
        final long timestamp = System.currentTimeMillis();

        feature.getOrmContext().runInTransaction(session -> {
            PlayerEntity playerEntity = null;

            if (source instanceof Player p) {
                // Resolve or create PlayerEntity
                playerEntity = session.createQuery(
                                "SELECT p FROM PlayerEntity p WHERE p.uuid = :uuid", PlayerEntity.class)
                        .setParameter("uuid", p.getUniqueId().toString())
                        .uniqueResult();

                if (playerEntity == null) {
                    playerEntity = new PlayerEntity();
                    playerEntity.setUuid(p.getUniqueId().toString());
                    playerEntity.setUsername(p.getUsername());
                    session.persist(playerEntity);
                } else if (!p.getUsername().equals(playerEntity.getUsername())) {
                    // Update username if it changed
                    playerEntity.setUsername(p.getUsername());
                    session.merge(playerEntity);
                }
            }

            String sourceLabel = source.getClass().getSimpleName().toLowerCase(Locale.ROOT);

            CommandExecutionEntity entry = new CommandExecutionEntity();
            entry.setServer(PROXY_SERVER_NAME);        // always "proxy" for Velocity
            entry.setPlayer(playerEntity);             // nullable for non-player sources
            entry.setSource(sourceLabel);              // class name of the source, lowercased
            entry.setCommand(fullCommand);             // full command as entered
            entry.setTimestamp(timestamp);             // epoch millis

            session.persist(entry);
            return null;
        });
    }
}
