package nl.hauntedmc.proxyfeatures.features.textcommands;

import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.features.BaseFeature;
import nl.hauntedmc.proxyfeatures.features.textcommands.command.TextCommand;
import nl.hauntedmc.proxyfeatures.features.textcommands.meta.Meta;
import nl.hauntedmc.proxyfeatures.localization.MessageMap;

import java.util.HashMap;
import java.util.Map;

public class TextCommands extends BaseFeature<Meta> {
    private Map<String, String> commands;

    public TextCommands(ProxyFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public Map<String, Object> getDefaultConfig() {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("enabled", false);
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        return new MessageMap();
    }

    @Override
    public void initialize() {
        this.commands = new HashMap<>();
        initializeTextCommands();
        registerTextCommands();
    }

    private void initializeTextCommands() {
        commands.put("store", "<aqua>Ga naar <gray><click:open_url:'https://store.hauntedmc.nl'>https://store.hauntedmc.nl</click> <aqua>voor onze winkel.");
        commands.put("regels", "<aqua>De regels kun je vinden op: <gray><click:open_url:'https://hauntedmc.nl/regels'>https://hauntedmc.nl/regels</click>");
        commands.put("website", "<aqua>Onze website is: <gray><click:open_url:'https://hauntedmc.nl/'>https://hauntedmc.nl/</click>");
        commands.put("ranks", "<aqua>Bekijk alle ranks en de bijbehorende functies en commands op <gray><click:open_url:'https://hauntedmc.nl/ranks'>https://hauntedmc.nl/ranks</click>");
        commands.put("discord", "<aqua>Ga naar <gray><click:open_url:'https://hauntedmc.nl/discord'>https://hauntedmc.nl/discord</click> <aqua>om discord te joinen.");
        commands.put("linkbedrock", "<aqua>Ga naar <gray><click:open_url:'https://link.geysermc.org/method/online'>https://link.geysermc.org/method/online</click> <aqua>om je bedrock account aan je java account te koppelen. Hiermee kun je op je telefoon, console of op Windows 10 Edition verder spelen op je java account.");
        commands.put("bedrock", "<aqua>Je kunt met Minecraft Bedrock Editie op HauntedMC spelen. Meer informatie hierover vind je op <gray><click:open_url:'https://hauntedmc.nl/help/bedrock'>https://hauntedmc.nl/help/bedrock</click>");
        commands.put("support", "<aqua>Voor de ticket support ga je naar: <gray><click:open_url:'https://hauntedmc.nl/support'>https://hauntedmc.nl/support</click>");
        commands.put("help", "<aqua>Voor extra hulp, informatie en veelgestelde vragen over HauntedMC ga naar: <gray><click:open_url:'https://hauntedmc.nl/help'>https://hauntedmc.nl/help</click>");
        commands.put("vacatures", "<aqua>Voor het solliciteren voor een van onze staff ranks, lees deze informatie goed door: <gray><click:open_url:'https://hauntedmc.nl/vacatures'>https://hauntedmc.nl/vacatures</click>");
        commands.put("vote", "<aqua>Ga naar <gray><click:open_url:'https://hauntedmc.nl/vote'>https://hauntedmc.nl/vote</click> <aqua>voor alle vote informatie.");
        commands.put("shoptutorial", "<aqua>Ga naar <gray><click:open_url:'https://hauntedmc.nl/shoptutorial'>https://hauntedmc.nl/shoptutorial</click> <aqua>voor de shop tutorial.");
        commands.put("flaghelp", "<aqua>Ga naar <gray><click:open_url:'https://github.com/ShaneBeee/GriefPreventionFlags/wiki/Flags'>https://github.com/ShaneBeee/GriefPreventionFlags/wiki/Flags</click> <aqua>voor meer details over de flags.");
        commands.put("maps", "<aqua>Ga naar <gray><click:open_url:'https://hauntedmc.nl/help/dynmap'>https://hauntedmc.nl/help/dynmap</click> <aqua>om alle interactieve 3D kaarten van de gamemodes te bekijken.");
        commands.put("limits", "<aqua>Ga naar <gray><click:open_url:'https://hauntedmc.nl/limits'>https://hauntedmc.nl/limits</click> <aqua>om de (tile) entity limits te bekijken.");
        commands.put("leaderboard", "<aqua>Ga naar <gray><click:open_url:'https://hauntedmc.nl/leaderboard'>https://hauntedmc.nl/leaderboard</click> <aqua>om de rankings van alle gamemodes te bekijken.");
        commands.put("kleurcodes", "<aqua>Ga naar <gray><click:open_url:'https://www.hauntedmc.nl/kleurcodes/'>https://www.hauntedmc.nl/kleurcodes/</click> <aqua>om uitleg te krijgen over het gebruik van kleurcodes.");
        commands.put("claimtutorial", "<aqua>Ga naar <gray><click:open_url:'https://www.youtube.com/watch?v=VScvidtaWM8'>https://www.youtube.com/watch?v=VScvidtaWM8</click> <aqua>voor de uitleg video over claimen op survival.");
        commands.put("hex", "<aqua>Ga naar <gray><click:open_url:'https://rgb.birdflop.com/'>https://rgb.birdflop.com/</click> <aqua>om een tekst met hex kleurcodes te maken.");
        commands.put("report", "<yellow>Je kunt een speler reporten op de website: <green><click:open_url:'https://hauntedmc.nl/support'>https://hauntedmc.nl/support</click>. <aqua>Om de chat van iemand op te slaan gebruik <gold>/chatreport <naam> <aqua>en kopier deze link in een report ticket.");
    }

    private void registerTextCommands() {
        for (Map.Entry<String, String> entry : commands.entrySet()) {
            getLifecycleManager().getCommandManager().registerFeatureCommand(new TextCommand(this, entry.getKey(), entry.getValue()));
        }
    }

    @Override
    public void disable() {
    }
}
