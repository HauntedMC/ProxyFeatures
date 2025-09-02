package nl.hauntedmc.proxyfeatures.features.textcommands;

import nl.hauntedmc.commonlib.config.ConfigMap;
import nl.hauntedmc.commonlib.localization.MessageMap;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.features.VelocityBaseFeature;
import nl.hauntedmc.proxyfeatures.features.textcommands.command.TextCommand;
import nl.hauntedmc.proxyfeatures.features.textcommands.meta.Meta;

import java.util.HashMap;
import java.util.Map;

public class TextCommands extends VelocityBaseFeature<Meta> {

    /** name -> command definition (message key + placeholders) */
    private Map<String, CommandDef> commands;

    public TextCommands(ProxyFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap m = new MessageMap();

        // NOTE: placeholders use {url} and are used both for visible text and the click action.
        m.add("command.store",        "<aqua>Ga naar <gray><click:open_url:'{url}'>{url}</click> <aqua>voor onze winkel.");
        m.add("command.regels",       "<aqua>De regels kun je vinden op: <gray><click:open_url:'{url}'>{url}</click>");
        m.add("command.website",      "<aqua>Onze website is: <gray><click:open_url:'{url}'>{url}</click>");
        m.add("command.ranks",        "<aqua>Bekijk alle ranks en de bijbehorende functies en commands op <gray><click:open_url:'{url}'>{url}</click>");
        m.add("command.discord",      "<aqua>Ga naar <gray><click:open_url:'{url}'>{url}</click> <aqua>om discord te joinen.");
        m.add("command.linkbedrock",  "<aqua>Ga naar <gray><click:open_url:'{url}'>{url}</click> <aqua>om je bedrock account aan je java account te koppelen. Hiermee kun je op je telefoon, console of op Windows 10 Edition verder spelen op je java account.");
        m.add("command.bedrock",      "<aqua>Je kunt met Minecraft Bedrock Editie op HauntedMC spelen. Meer informatie hierover vind je op <gray><click:open_url:'{url}'>{url}</click>");
        m.add("command.support",      "<aqua>Voor de ticket support ga je naar: <gray><click:open_url:'{url}'>{url}</click>");
        m.add("command.help",         "<aqua>Voor extra hulp, informatie en veelgestelde vragen over HauntedMC ga naar: <gray><click:open_url:'{url}'>{url}</click>");
        m.add("command.vacatures",    "<aqua>Voor het solliciteren voor een van onze staff ranks, lees deze informatie goed door: <gray><click:open_url:'{url}'>{url}</click>");
        m.add("command.vote",         "<aqua>Ga naar <gray><click:open_url:'{url}'>{url}</click> <aqua>voor alle vote informatie.");
        m.add("command.shoptutorial", "<aqua>Ga naar <gray><click:open_url:'{url}'>{url}</click> <aqua>voor de shop tutorial.");
        m.add("command.flaghelp",     "<aqua>Ga naar <gray><click:open_url:'{url}'>{url}</click> <aqua>voor meer details over de flags.");
        m.add("command.maps",         "<aqua>Ga naar <gray><click:open_url:'{url}'>{url}</click> <aqua>om alle interactieve 3D kaarten van de gamemodes te bekijken.");
        m.add("command.limits",       "<aqua>Ga naar <gray><click:open_url:'{url}'>{url}</click> <aqua>om de (tile) entity limits te bekijken.");
        m.add("command.leaderboard",  "<aqua>Ga naar <gray><click:open_url:'{url}'>{url}</click> <aqua>om de rankings van alle gamemodes te bekijken.");
        m.add("command.kleurcodes",   "<aqua>Ga naar <gray><click:open_url:'{url}'>{url}</click> <aqua>om uitleg te krijgen over het gebruik van kleurcodes.");
        m.add("command.claimtutorial","<aqua>Ga naar <gray><click:open_url:'{url}'>{url}</click> <aqua>voor de uitleg video over claimen op survival.");
        m.add("command.hex",          "<aqua>Ga naar <gray><click:open_url:'{url}'>{url}</click> <aqua>om een tekst met hex kleurcodes te maken.");
        m.add("command.report",       "<yellow>Je kunt een speler reporten op de website: <green><click:open_url:'{url}'>{url}</click>. <aqua>Om de chat van iemand op te slaan gebruik <gold>/chatreport <naam> <aqua>en kopier deze link in een report ticket.");

        return m;
    }

    @Override
    public void initialize() {
        this.commands = new HashMap<>();
        initializeTextCommands();
        registerTextCommands();
    }

    private void initializeTextCommands() {
        // name -> (messageKey, placeholders)
        commands.put("store",        new CommandDef("command.store",        Map.of("url", "https://store.hauntedmc.nl")));
        commands.put("regels",       new CommandDef("command.regels",       Map.of("url", "https://hauntedmc.nl/regels")));
        commands.put("website",      new CommandDef("command.website",      Map.of("url", "https://hauntedmc.nl/")));
        commands.put("ranks",        new CommandDef("command.ranks",        Map.of("url", "https://hauntedmc.nl/ranks")));
        commands.put("discord",      new CommandDef("command.discord",      Map.of("url", "https://hauntedmc.nl/discord")));
        commands.put("linkbedrock",  new CommandDef("command.linkbedrock",  Map.of("url", "https://link.geysermc.org/method/online")));
        commands.put("bedrock",      new CommandDef("command.bedrock",      Map.of("url", "https://hauntedmc.nl/help/bedrock")));
        commands.put("support",      new CommandDef("command.support",      Map.of("url", "https://hauntedmc.nl/support")));
        commands.put("help",         new CommandDef("command.help",         Map.of("url", "https://hauntedmc.nl/help")));
        commands.put("vacatures",    new CommandDef("command.vacatures",    Map.of("url", "https://hauntedmc.nl/vacatures")));
        commands.put("vote",         new CommandDef("command.vote",         Map.of("url", "https://hauntedmc.nl/vote")));
        commands.put("shoptutorial", new CommandDef("command.shoptutorial", Map.of("url", "https://hauntedmc.nl/shoptutorial")));
        commands.put("flaghelp",     new CommandDef("command.flaghelp",     Map.of("url", "https://github.com/ShaneBeee/GriefPreventionFlags/wiki/Flags")));
        commands.put("maps",         new CommandDef("command.maps",         Map.of("url", "https://hauntedmc.nl/help/dynmap")));
        commands.put("limits",       new CommandDef("command.limits",       Map.of("url", "https://hauntedmc.nl/limits")));
        commands.put("leaderboard",  new CommandDef("command.leaderboard",  Map.of("url", "https://hauntedmc.nl/leaderboard")));
        commands.put("kleurcodes",   new CommandDef("command.kleurcodes",   Map.of("url", "https://www.hauntedmc.nl/kleurcodes/")));
        commands.put("claimtutorial",new CommandDef("command.claimtutorial",Map.of("url", "https://www.youtube.com/watch?v=VScvidtaWM8")));
        commands.put("hex",          new CommandDef("command.hex",          Map.of("url", "https://rgb.birdflop.com/")));
        commands.put("report",       new CommandDef("command.report",       Map.of("url", "https://hauntedmc.nl/support")));
    }

    private void registerTextCommands() {
        for (Map.Entry<String, CommandDef> entry : commands.entrySet()) {
            getLifecycleManager().getCommandManager().registerFeatureCommand(
                    new TextCommand(this, entry.getKey(), entry.getValue().messageKey, entry.getValue().placeholders)
            );
        }
    }

    @Override
    public void disable() { }

    /** Simple holder for a message key and its placeholder map. */
    private static class CommandDef {
        final String messageKey;
        final Map<String, String> placeholders;

        private CommandDef(String messageKey, Map<String, String> placeholders) {
            this.messageKey = messageKey;
            this.placeholders = placeholders;
        }
    }
}
