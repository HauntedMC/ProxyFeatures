package nl.hauntedmc.proxyfeatures.features.slashserver.command;

import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.proxyfeatures.api.command.FeatureCommand;
import nl.hauntedmc.proxyfeatures.features.slashserver.SlashServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class SlashServerAdminCommand implements FeatureCommand {

    private static final String PERM_BASE = "proxyfeatures.feature.slashserver.command";
    private static final String PERM_LIST = PERM_BASE + ".list";
    private static final String PERM_STATUS = PERM_BASE + ".status";
    private static final String PERM_ENABLE = PERM_BASE + ".enable";
    private static final String PERM_DISABLE = PERM_BASE + ".disable";

    private final SlashServer feature;

    public SlashServerAdminCommand(SlashServer feature) {
        this.feature = feature;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (args.length == 0) {
            sendUsage(source);
            return;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> handleList(source);
            case "status" -> handleStatus(source, args);
            case "enable" -> handleSetEnabled(source, args, true);
            case "disable" -> handleSetEnabled(source, args, false);
            default -> sendUsage(source);
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission(PERM_BASE);
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length == 0) {
            return CompletableFuture.completedFuture(List.of("list", "status", "enable", "disable"));
        }

        if (args.length == 1) {
            String input = args[0].toLowerCase(Locale.ROOT);
            List<String> suggestions = new ArrayList<>();
            for (String option : List.of("list", "status", "enable", "disable")) {
                if (option.startsWith(input)) {
                    suggestions.add(option);
                }
            }
            return CompletableFuture.completedFuture(suggestions);
        }

        if (args.length == 2 && List.of("status", "enable", "disable").contains(args[0].toLowerCase(Locale.ROOT))) {
            String input = args[1].toLowerCase(Locale.ROOT);
            List<String> suggestions = new ArrayList<>();
            for (String serverName : feature.getConfiguredServerNames()) {
                if (serverName.startsWith(input)) {
                    suggestions.add(serverName);
                }
            }
            return CompletableFuture.completedFuture(suggestions);
        }

        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public String getName() {
        return "slashserver";
    }

    @Override
    public String[] getAliases() {
        return new String[]{"ss"};
    }

    private void handleList(CommandSource source) {
        if (!source.hasPermission(PERM_LIST)) {
            sendNoPermission(source);
            return;
        }

        List<String> serverNames = feature.getConfiguredServerNames();
        source.sendMessage(feature.getLocalizationHandler()
                .getMessage("slash.admin.list.header")
                .with("count", serverNames.size())
                .forAudience(source)
                .build());

        for (String serverName : serverNames) {
            source.sendMessage(feature.getLocalizationHandler()
                    .getMessage("slash.admin.list.entry")
                    .with("server", serverName)
                    .with("status", statusComponent(source, serverName))
                    .forAudience(source)
                    .build());
        }
    }

    private void handleStatus(CommandSource source, String[] args) {
        if (!source.hasPermission(PERM_STATUS)) {
            sendNoPermission(source);
            return;
        }

        if (args.length < 2) {
            sendUsage(source);
            return;
        }

        String serverName = normalizeAndValidateServer(source, args[1]);
        if (serverName == null) {
            return;
        }

        source.sendMessage(feature.getLocalizationHandler()
                .getMessage("slash.admin.status")
                .with("server", serverName)
                .with("status", statusComponent(source, serverName))
                .forAudience(source)
                .build());
    }

    private void handleSetEnabled(CommandSource source, String[] args, boolean enabled) {
        String permission = enabled ? PERM_ENABLE : PERM_DISABLE;
        if (!source.hasPermission(permission)) {
            sendNoPermission(source);
            return;
        }

        if (args.length < 2) {
            sendUsage(source);
            return;
        }

        String serverName = normalizeAndValidateServer(source, args[1]);
        if (serverName == null) {
            return;
        }

        boolean current = feature.isServerEnabled(serverName);
        if (current == enabled) {
            source.sendMessage(feature.getLocalizationHandler()
                    .getMessage(enabled ? "slash.admin.already_enabled" : "slash.admin.already_disabled")
                    .with("server", serverName)
                    .forAudience(source)
                    .build());
            return;
        }

        feature.setServerEnabled(serverName, enabled);
        source.sendMessage(feature.getLocalizationHandler()
                .getMessage(enabled ? "slash.admin.set_enabled" : "slash.admin.set_disabled")
                .with("server", serverName)
                .forAudience(source)
                .build());
    }

    private String normalizeAndValidateServer(CommandSource source, String input) {
        String serverName = feature.normalizeServerName(input);
        if (serverName.isBlank() || !feature.hasConfiguredServer(serverName) || feature.findServer(serverName).isEmpty()) {
            source.sendMessage(feature.getLocalizationHandler()
                    .getMessage("slash.admin.unknown_server")
                    .with("server", input)
                    .forAudience(source)
                    .build());
            return null;
        }
        return serverName;
    }

    private Component statusComponent(CommandSource source, String serverName) {
        return feature.getLocalizationHandler()
                .getMessage(feature.isServerEnabled(serverName) ? "slash.admin.enabled" : "slash.admin.disabled")
                .forAudience(source)
                .build();
    }

    private void sendUsage(CommandSource source) {
        source.sendMessage(feature.getLocalizationHandler()
                .getMessage("slash.admin.usage")
                .forAudience(source)
                .build());
    }

    private void sendNoPermission(CommandSource source) {
        source.sendMessage(feature.getLocalizationHandler()
                .getMessage("slash.admin.no_permission")
                .forAudience(source)
                .build());
    }
}
