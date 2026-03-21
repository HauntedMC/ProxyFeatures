package nl.hauntedmc.proxyfeatures.features.connectioninfo.command;

import java.util.List;
import java.util.Locale;

final class PingCommandPolicy {

    private PingCommandPolicy() {
    }

    static String colorCodeForPing(int ping, int greenThreshold, int yellowThreshold) {
        if (ping <= greenThreshold) {
            return "&a";
        }
        if (ping <= yellowThreshold) {
            return "&e";
        }
        return "&c";
    }

    static List<String> suggestions(String[] args, List<String> onlinePlayers) {
        if (args.length == 0 || args[0].isEmpty()) {
            return onlinePlayers;
        }

        String partial = args[0].toLowerCase(Locale.ROOT);
        return onlinePlayers.stream()
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(partial))
                .toList();
    }
}
