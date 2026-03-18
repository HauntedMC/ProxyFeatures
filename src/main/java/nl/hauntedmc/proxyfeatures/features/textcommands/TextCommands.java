package nl.hauntedmc.proxyfeatures.features.textcommands;

import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.proxyfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.proxyfeatures.features.VelocityBaseFeature;
import nl.hauntedmc.proxyfeatures.features.textcommands.command.TextCommand;
import nl.hauntedmc.proxyfeatures.features.textcommands.meta.Meta;

import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Map;

public class TextCommands extends VelocityBaseFeature<Meta> {

    /**
     * name -> command definition (message key + placeholders)
     */
    private Map<String, CommandDef> commands;

    public TextCommands(ProxyFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);

        defaultCommands().forEach((name, def) -> {
            defaults.put("commands." + name + ".message-key", def.messageKey);
            def.placeholders.forEach((placeholder, value) ->
                    defaults.put("commands." + name + ".placeholders." + placeholder, value)
            );
        });

        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap m = new MessageMap();

        // NOTE: placeholders use {url} and are used both for visible text and the click action.
        m.add("text.store", "<aqua>Ga naar <gray><click:open_url:'{url}'>{url}</click> <aqua>voor onze winkel.");
        m.add("text.regels", "<aqua>De regels kun je vinden op: <gray><click:open_url:'{url}'>{url}</click>");
        m.add("text.website", "<aqua>Onze website is: <gray><click:open_url:'{url}'>{url}</click>");
        m.add("text.ranks", "<aqua>Bekijk alle ranks en de bijbehorende functies en commands op <gray><click:open_url:'{url}'>{url}</click>");
        m.add("text.discord", "<aqua>Ga naar <gray><click:open_url:'{url}'>{url}</click> <aqua>om discord te joinen.");
        m.add("text.linkbedrock", "<aqua>Ga naar <gray><click:open_url:'{url}'>{url}</click> <aqua>om je bedrock account aan je java account te koppelen. Hiermee kun je op je telefoon, console of op Windows 10 Edition verder spelen op je java account.");
        m.add("text.bedrock", "<aqua>Je kunt met Minecraft Bedrock Editie op HauntedMC spelen. Meer informatie hierover vind je op <gray><click:open_url:'{url}'>{url}</click>");
        m.add("text.support", "<aqua>Voor de ticket support ga je naar: <gray><click:open_url:'{url}'>{url}</click>");
        m.add("text.help", "<aqua>Voor extra hulp, informatie en veelgestelde vragen over HauntedMC ga naar: <gray><click:open_url:'{url}'>{url}</click>");
        m.add("text.vacatures", "<aqua>Voor het solliciteren voor een van onze staff ranks, lees deze informatie goed door: <gray><click:open_url:'{url}'>{url}</click>");
        m.add("text.shoptutorial", "<aqua>Ga naar <gray><click:open_url:'{url}'>{url}</click> <aqua>voor de shop tutorial.");
        m.add("text.flaghelp", "<aqua>Ga naar <gray><click:open_url:'{url}'>{url}</click> <aqua>voor meer details over de flags.");
        m.add("text.maps", "<aqua>Ga naar <gray><click:open_url:'{url}'>{url}</click> <aqua>om alle interactieve 3D kaarten van de gamemodes te bekijken.");
        m.add("text.limits", "<aqua>Ga naar <gray><click:open_url:'{url}'>{url}</click> <aqua>om de (tile) entity limits te bekijken.");
        m.add("text.leaderboard", "<aqua>Ga naar <gray><click:open_url:'{url}'>{url}</click> <aqua>om de rankings van alle gamemodes te bekijken.");
        m.add("text.kleurcodes", "<aqua>Ga naar <gray><click:open_url:'{url}'>{url}</click> <aqua>om uitleg te krijgen over het gebruik van kleurcodes.");
        m.add("text.claimtutorial", "<aqua>Ga naar <gray><click:open_url:'{url}'>{url}</click> <aqua>voor de uitleg video over claimen op survival.");
        m.add("text.hex", "<aqua>Ga naar <gray><click:open_url:'{url}'>{url}</click> <aqua>om een tekst met hex kleurcodes te maken.");
        m.add("text.report", "<yellow>Je kunt een speler reporten op de website: <green><click:open_url:'{url}'>{url}</click>. <aqua>Om de chat van iemand op te slaan gebruik <gold>/chatreport <naam> <aqua>en kopier deze link in een report ticket.");

        return m;
    }

    @Override
    public void initialize() {
        this.commands = new HashMap<>();
        initializeTextCommands();
        registerTextCommands();
    }

    private void initializeTextCommands() {
        var commandNodes = getConfigHandler().node("commands").children();
        for (Map.Entry<String, nl.hauntedmc.proxyfeatures.api.io.config.ConfigNode> entry : commandNodes.entrySet()) {
            String name = entry.getKey();
            if (name == null || name.isBlank()) {
                continue;
            }

            String trimmedName = name.trim();
            var node = entry.getValue();
            String messageKey = node.get("message-key").as(String.class, null);
            if (messageKey == null || messageKey.isBlank()) {
                getLogger().warn("Skipping text command '" + trimmedName + "' because 'message-key' is missing.");
                continue;
            }

            Map<String, String> placeholders = new HashMap<>(node.get("placeholders").mapValues(String.class));
            commands.put(trimmedName, new CommandDef(messageKey.trim(), placeholders));
        }

        if (commands.isEmpty()) {
            getLogger().warn("No valid text commands configured under 'features." + getFeatureName() + ".commands'.");
        }
    }

    private void registerTextCommands() {
        for (Map.Entry<String, CommandDef> entry : commands.entrySet()) {
            getLifecycleManager().getCommandManager().registerFeatureCommand(
                    new TextCommand(this, entry.getKey(), entry.getValue().messageKey, entry.getValue().placeholders)
            );
        }
    }

    @Override
    public void disable() {
    }

    /**
     * Simple holder for a message key and its placeholder map.
     */
    private record CommandDef(String messageKey, Map<String, String> placeholders) {
    }

    private static Map<String, CommandDef> defaultCommands() {
        Map<String, CommandDef> defaults = new LinkedHashMap<>();
        defaults.put("store", new CommandDef("text.store", Map.of("url", "https://store.hauntedmc.nl")));
        defaults.put("regels", new CommandDef("text.regels", Map.of("url", "https://hauntedmc.nl/regels")));
        defaults.put("website", new CommandDef("text.website", Map.of("url", "https://hauntedmc.nl/")));
        defaults.put("ranks", new CommandDef("text.ranks", Map.of("url", "https://hauntedmc.nl/ranks")));
        defaults.put("discord", new CommandDef("text.discord", Map.of("url", "https://hauntedmc.nl/discord")));
        defaults.put("linkbedrock", new CommandDef("text.linkbedrock", Map.of("url", "https://link.geysermc.org/method/online")));
        defaults.put("bedrock", new CommandDef("text.bedrock", Map.of("url", "https://hauntedmc.nl/help/bedrock")));
        defaults.put("support", new CommandDef("text.support", Map.of("url", "https://hauntedmc.nl/support")));
        defaults.put("help", new CommandDef("text.help", Map.of("url", "https://hauntedmc.nl/help")));
        defaults.put("vacatures", new CommandDef("text.vacatures", Map.of("url", "https://hauntedmc.nl/vacatures")));
        defaults.put("shoptutorial", new CommandDef("text.shoptutorial", Map.of("url", "https://hauntedmc.nl/shoptutorial")));
        defaults.put("flaghelp", new CommandDef("text.flaghelp", Map.of("url", "https://github.com/ShaneBeee/GriefPreventionFlags/wiki/Flags")));
        defaults.put("maps", new CommandDef("text.maps", Map.of("url", "https://hauntedmc.nl/help/dynmap")));
        defaults.put("limits", new CommandDef("text.limits", Map.of("url", "https://hauntedmc.nl/limits")));
        defaults.put("leaderboard", new CommandDef("text.leaderboard", Map.of("url", "https://hauntedmc.nl/leaderboard")));
        defaults.put("kleurcodes", new CommandDef("text.kleurcodes", Map.of("url", "https://www.hauntedmc.nl/kleurcodes/")));
        defaults.put("claimtutorial", new CommandDef("text.claimtutorial", Map.of("url", "https://www.youtube.com/watch?v=VScvidtaWM8")));
        defaults.put("hex", new CommandDef("text.hex", Map.of("url", "https://rgb.birdflop.com/")));
        defaults.put("report", new CommandDef("text.report", Map.of("url", "https://hauntedmc.nl/support")));
        return defaults;
    }
}
