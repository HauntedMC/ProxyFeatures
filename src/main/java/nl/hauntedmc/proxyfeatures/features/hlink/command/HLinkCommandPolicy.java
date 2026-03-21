package nl.hauntedmc.proxyfeatures.features.hlink.command;

import java.util.List;
import java.util.Locale;

final class HLinkCommandPolicy {

    private HLinkCommandPolicy() {
    }

    static boolean isValidSyncUsage(List<String> args) {
        return args.size() == 2 && "sync".equalsIgnoreCase(args.get(0));
    }

    static List<String> suggestions(List<String> args, List<String> onlinePlayers) {
        if (args.size() == 1) {
            return List.of("sync");
        }

        if (args.size() == 2 && "sync".equalsIgnoreCase(args.get(0))) {
            String partial = args.get(1).toLowerCase(Locale.ROOT);
            return onlinePlayers.stream()
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(partial))
                    .toList();
        }

        return List.of();
    }
}
