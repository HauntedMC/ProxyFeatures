package nl.hauntedmc.proxyfeatures.features.playerinfo.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerConnectionInfoEntity;
import nl.hauntedmc.proxyfeatures.commands.FeatureCommand;
import nl.hauntedmc.proxyfeatures.features.playerinfo.PlayerInfo;
import nl.hauntedmc.proxyfeatures.features.playerinfo.service.PlayerInfoService;
import nl.hauntedmc.proxyfeatures.features.sanctions.entity.SanctionEntity;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class PlayerInfoCommand extends FeatureCommand {
    private final PlayerInfo feature;

    public PlayerInfoCommand(PlayerInfo feature) {
        this.feature = feature;
    }

    @Override
    public String getName() {
        return "playerinfo";
    }

    @Override
    public String[] getAliases() {
        return new String[]{""};
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("proxyfeatures.feature.playerinfo.command");
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (args.length != 1) {
            source.sendMessage(feature.getLocalizationHandler()
                    .getMessage("playerinfo.cmd_usage")
                    .forAudience(source)
                    .build());
            return;
        }

        String query = args[0];

        // Resolve via online player (for latest name/uuid), then DB fallback
        Optional<Player> online = feature.getPlugin().getProxy().getPlayer(query);
        Optional<PlayerEntity> playerEntityOpt;

        if (online.isPresent()) {
            String uuid = online.get().getUniqueId().toString();
            playerEntityOpt = feature.getService().findPlayerEntityByUuid(uuid);
            if (playerEntityOpt.isEmpty()) {
                // Fall back to name lookup; don't mutate DB here.
                playerEntityOpt = feature.getService().findPlayerEntityByName(online.get().getUsername());
            }
        } else {
            // Try DB by exact (case-insensitive) name first, otherwise interpret as UUID
            playerEntityOpt = feature.getService().findPlayerEntityByName(query);
            if (playerEntityOpt.isEmpty()) {
                try {
                    UUID uuid = UUID.fromString(query);
                    playerEntityOpt = feature.getService().findPlayerEntityByUuid(uuid.toString());
                } catch (Exception ignored) {
                }
            }
        }

        if (playerEntityOpt.isEmpty()) {
            source.sendMessage(feature.getLocalizationHandler()
                    .getMessage("playerinfo.cmd_playerNotFound")
                    .withPlaceholders(Map.of("player", query))
                    .forAudience(source)
                    .build());
            return;
        }

        PlayerEntity playerEntity = playerEntityOpt.get();
        PlayerInfoService svc = feature.getService();

        // Online status
        PlayerInfoService.OnlineStatus onlineStatus = svc.getOnlineStatus(playerEntity.getUsername());

        // Connection info
        Optional<PlayerConnectionInfoEntity> connOpt = svc.getConnectionInfo(playerEntity);

        // Active sanctions
        List<SanctionEntity> activeSanctions = svc.getActiveSanctions(playerEntity);

        // Header
        source.sendMessage(feature.getLocalizationHandler()
                .getMessage("playerinfo.cmd_header")
                .withPlaceholders(Map.of("player", playerEntity.getUsername()))
                .forAudience(source)
                .build());

        // Field labels (resolved via messages)
        String lblName = raw(source, "playerinfo.field.name");
        String lblUuid = raw(source, "playerinfo.field.uuid");
        String lblOnline = raw(source, "playerinfo.field.online");
        String lblFirstLogin = raw(source, "playerinfo.field.first_login");
        String lblLastLogin = raw(source, "playerinfo.field.last_login");
        String lblLastDisconnect = raw(source, "playerinfo.field.last_disconnect");
        String lblAlts = raw(source, "playerinfo.field.alts");

        // Entries
        sendEntry(source, lblName, playerEntity.getUsername());
        sendEntry(source, lblUuid, playerEntity.getUuid());

        if (onlineStatus.online()) {
            Component onlineYes = feature.getLocalizationHandler()
                    .getMessage("playerinfo.online_yes")
                    .withPlaceholders(Map.of("server", onlineStatus.serverName()))
                    .forAudience(source)
                    .build();
            sendEntry(source, lblOnline, onlineYes);
        } else {
            Component onlineNo = feature.getLocalizationHandler()
                    .getMessage("playerinfo.online_no")
                    .forAudience(source)
                    .build();
            sendEntry(source, lblOnline, onlineNo);
        }

        String firstLogin = connOpt.map(c -> svc.fmt(c.getFirstConnectionAt())).orElse("—");
        String lastLogin = connOpt.map(c -> svc.fmt(c.getLastConnectionAt())).orElse("—");
        String lastDisconnect = connOpt.map(c -> svc.fmt(c.getLastDisconnectAt())).orElse("—");

        sendEntry(source, lblFirstLogin, firstLogin);
        sendEntry(source, lblLastLogin, lastLogin);
        sendEntry(source, lblLastDisconnect, lastDisconnect);

        // Possible alts by last known IP (exclude self, A–Z)
        String lastIp = connOpt.map(PlayerConnectionInfoEntity::getIpAddress).orElse(null);
        List<String> altNames = svc.findUsernamesByLastIp(lastIp, playerEntity.getId());
        String altsValue = altNames.isEmpty()
                ? raw(source, "playerinfo.alts_none")
                : String.join(", ", altNames);
        sendEntry(source, lblAlts, altsValue);

        // Active punishments
        source.sendMessage(feature.getLocalizationHandler()
                .getMessage("playerinfo.punishments_header")
                .forAudience(source)
                .build());

        if (activeSanctions.isEmpty()) {
            source.sendMessage(feature.getLocalizationHandler()
                    .getMessage("playerinfo.punishments_none")
                    .forAudience(source)
                    .build());
        } else {
            String permanentLabel = raw(source, "playerinfo.permanent");
            for (SanctionEntity s : activeSanctions) {
                String expires = (s.getExpiresAt() == null) ? permanentLabel : svc.fmt(s.getExpiresAt());
                String created = svc.fmt(s.getCreatedAt());

                source.sendMessage(feature.getLocalizationHandler()
                        .getMessage("playerinfo.punishment_item")
                        .withPlaceholders(Map.of(
                                "type", s.getType().name(),
                                "reason", s.getReason(),
                                "expires", expires,
                                "created", created
                        ))
                        .forAudience(source)
                        .build());
            }
        }
    }

    private void sendEntry(CommandSource audience, String field, String value) {
        audience.sendMessage(feature.getLocalizationHandler()
                .getMessage("playerinfo.entry")
                .withPlaceholders(Map.of("field", field, "value", value))
                .forAudience(audience)
                .build());
    }

    private void sendEntry(CommandSource audience, String field, Component valueComponent) {
        String rawMessage = LegacyComponentSerializer.legacyAmpersand().serialize(valueComponent);
        audience.sendMessage(feature.getLocalizationHandler()
                .getMessage("playerinfo.entry")
                .withPlaceholders(Map.of("field", field, "value", rawMessage))
                .forAudience(audience)
                .build());
    }

    /**
     * Resolve a message key to a legacy-serialized string for placeholders.
     */
    private String raw(CommandSource audience, String key) {
        return LegacyComponentSerializer.legacyAmpersand().serialize(
                feature.getLocalizationHandler().getMessage(key).forAudience(audience).build()
        );
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        String[] args = invocation.arguments();

        // No arg yet -> all online players
        if (args.length == 0 || args[0].isEmpty()) {
            List<String> all = feature.getPlugin().getProxy().getAllPlayers()
                    .stream().map(Player::getUsername).collect(Collectors.toList());
            return CompletableFuture.completedFuture(all);
        }

        String partial = args[0].toLowerCase(Locale.ROOT);
        List<String> matching = feature.getPlugin().getProxy().getAllPlayers()
                .stream()
                .map(Player::getUsername)
                .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(partial))
                .collect(Collectors.toList());
        return CompletableFuture.completedFuture(matching);
    }
}
