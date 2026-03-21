package nl.hauntedmc.proxyfeatures.features.commandlogger.listener;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;

import java.util.Locale;
import java.util.Optional;

final class CommandExecutionPolicy {

    private CommandExecutionPolicy() {
    }

    static Optional<String> extractAlias(String fullCommand) {
        if (fullCommand == null) {
            return Optional.empty();
        }

        String trimmed = fullCommand.stripLeading();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }

        for (int i = 0; i < trimmed.length(); i++) {
            if (Character.isWhitespace(trimmed.charAt(i))) {
                return Optional.of(trimmed.substring(0, i));
            }
        }
        return Optional.of(trimmed);
    }

    static String describeSource(CommandSource source) {
        if (source instanceof Player player) {
            return player.getUsername() + " (" + player.getUniqueId() + ")";
        }
        return source.getClass().getSimpleName().toLowerCase(Locale.ROOT);
    }
}
