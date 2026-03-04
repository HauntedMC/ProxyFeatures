package nl.hauntedmc.proxyfeatures.features.playerlanguage.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.api.command.FeatureCommand;
import nl.hauntedmc.proxyfeatures.api.io.localization.Language;
import nl.hauntedmc.proxyfeatures.features.playerlanguage.PlayerLanguage;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class LanguageCommand implements FeatureCommand {

    private static final String PERM_SELF = "proxyfeatures.feature.language.command";
    private static final String PERM_OTHERS = "proxyfeatures.feature.language.others";

    private final PlayerLanguage feature;

    public LanguageCommand(PlayerLanguage feature) {
        this.feature = feature;
    }


    public void execute(Invocation inv) {
        CommandSource src = inv.source();
        String[] args = inv.arguments();

        // Self usage requires a player (consistent with your current behavior)
        if (!(src instanceof Player p)) {
            src.sendMessage(feature.getLocalizationHandler().getMessage("language.player_only").forAudience(src).build());
            return;
        }

        if (!src.hasPermission(PERM_SELF)) {
            // Optional: send a "no permission" for self if you add a message key.
            return;
        }

        // /language
        if (args.length == 0) {
            Language current = feature.getService().get(p.getUniqueId());
            src.sendMessage(feature.getLocalizationHandler().getMessage("language.current")
                    .with("lang", current.name())
                    .forAudience(src).build());
            src.sendMessage(feature.getLocalizationHandler().getMessage("language.usage").forAudience(src).build());
            return;
        }

        // Staff QoL: /language <player>  -> show that player's current language (if arg is not a language token)
        if (args.length == 1 && src.hasPermission(PERM_OTHERS) && !isLanguageToken(args[0])) {
            String targetName = args[0];
            Optional<UUID> targetUuid = feature.getService().resolveUuidByName(targetName);
            if (targetUuid.isEmpty()) {
                src.sendMessage(feature.getLocalizationHandler().getMessage("language.not_found")
                        .with("target", targetName)
                        .forAudience(src).build());
                return;
            }
            Language lang = feature.getService().get(targetUuid.get());
            src.sendMessage(feature.getLocalizationHandler().getMessage("language.current_other")
                    .with("target", targetName)
                    .with("lang", lang.name())
                    .forAudience(src).build());
            return;
        }

        // /language <LANG>   -> set own
        if (args.length == 1) {
            String token = args[0].trim().toUpperCase(Locale.ROOT);
            Language chosen;
            try {
                chosen = Language.valueOf(token);
            } catch (IllegalArgumentException ex) {
                src.sendMessage(feature.getLocalizationHandler().getMessage("language.invalid")
                        .with("input", token)
                        .forAudience(src).build());
                return;
            }

            feature.getService().set(p.getUniqueId(), chosen);
            src.sendMessage(feature.getLocalizationHandler().getMessage("language.set")
                    .with("lang", chosen.name())
                    .forAudience(src).build());
            return;
        }

        // /language <player> <LANG>  (staff)
        if (!src.hasPermission(PERM_OTHERS)) {
            src.sendMessage(feature.getLocalizationHandler().getMessage("language.no_permission_others").forAudience(src).build());
            return;
        }

        String targetName = args[0];
        String token = args[1].trim().toUpperCase(Locale.ROOT);

        Optional<UUID> targetUuid = feature.getService().resolveUuidByName(targetName);
        if (targetUuid.isEmpty()) {
            src.sendMessage(feature.getLocalizationHandler().getMessage("language.not_found")
                    .with("target", targetName)
                    .forAudience(src).build());
            return;
        }

        Language chosen;
        try {
            chosen = Language.valueOf(token);
        } catch (IllegalArgumentException ex) {
            src.sendMessage(feature.getLocalizationHandler().getMessage("language.invalid")
                    .with("input", token)
                    .forAudience(src).build());
            return;
        }

        feature.getService().set(targetUuid.get(), chosen);
        src.sendMessage(feature.getLocalizationHandler().getMessage("language.set_other")
                .with("target", targetName)
                .with("lang", chosen.name())
                .forAudience(src).build());
    }


    public boolean hasPermission(Invocation inv) {
        // Let everyone pass here; we check exact perms in execute to allow nuanced control.
        return inv.source().hasPermission(PERM_SELF) || inv.source().hasPermission(PERM_OTHERS);
    }

    public String getName() {
        return "language";
    }

    public String[] getAliases() {
        return new String[]{"lang", "taal", "sprache"};
    }


    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        CommandSource src = invocation.source();
        boolean canOthers = src.hasPermission(PERM_OTHERS);
        String[] a = invocation.arguments();

        List<String> languages = Arrays.stream(Language.values()).map(Enum::name).sorted().toList();
        List<String> players = canOthers
                ? ProxyFeatures.getProxyInstance().getAllPlayers().stream()
                .map(Player::getUsername)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList()
                : List.of();

        if (a.length == 0) {
            // Suggest both languages and (if staff) online player names
            List<String> out = new ArrayList<>(languages);
            out.addAll(players);
            return CompletableFuture.completedFuture(out);
        }

        if (a.length == 1) {
            String partial = a[0].toUpperCase(Locale.ROOT);
            List<String> langMatches = languages.stream()
                    .filter(s -> s.startsWith(partial))
                    .collect(Collectors.toList());

            if (!canOthers) {
                return CompletableFuture.completedFuture(langMatches);
            }

            String partialRaw = a[0];
            List<String> playerMatches = players.stream()
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(partialRaw.toLowerCase(Locale.ROOT)))
                    .toList();

            // Combine: languages first, then players
            List<String> out = new ArrayList<>(langMatches);
            out.addAll(playerMatches);
            return CompletableFuture.completedFuture(out);
        }

        if (a.length == 2 && canOthers) {
            String partial = a[1].toUpperCase(Locale.ROOT);
            List<String> langMatches = languages.stream()
                    .filter(s -> s.startsWith(partial))
                    .collect(Collectors.toList());
            return CompletableFuture.completedFuture(langMatches);
        }

        return CompletableFuture.completedFuture(List.of());
    }

    private boolean isLanguageToken(String s) {
        try {
            Language.valueOf(s.trim().toUpperCase(Locale.ROOT));
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
