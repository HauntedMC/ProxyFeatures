package nl.hauntedmc.proxyfeatures.features.restart;

import nl.hauntedmc.commonlib.config.ConfigMap;
import nl.hauntedmc.commonlib.localization.MessageMap;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.features.VelocityBaseFeature;
import nl.hauntedmc.proxyfeatures.features.restart.command.ProxyRestartCommand;
import nl.hauntedmc.proxyfeatures.features.restart.internal.RestartHandler;
import nl.hauntedmc.proxyfeatures.features.restart.meta.Meta;

import java.util.ArrayList;
import java.util.List;

public class Restart extends VelocityBaseFeature<Meta> {

    private RestartHandler handler;

    public Restart(ProxyFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);
        defaults.put("countdown_seconds", 60);
        List<Integer> warnTimes = new ArrayList<>(List.of(60, 30, 15, 10, 5, 4, 3, 2, 1));
        defaults.put("warn_times", warnTimes);
        defaults.put("final_delay_seconds", 5);
        defaults.put("broadcast_use_chat", true);
        defaults.put("broadcast_use_titles", true);
        defaults.put("title_fade_in_ms", 500);
        defaults.put("title_stay_ms", 2000);
        defaults.put("title_fade_out_ms", 500);
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap m = new MessageMap();
        m.add("restart.cmd_usage", "&eGebruik: /proxyrestart &7[force]");
        m.add("restart.cmd_usage_force", "&eGebruik: /proxyrestart force");
        m.add("restart.already_running", "&eEr is al een proxy restart bezig.");
        m.add("restart.started", "&aProxy restart sequentie gestart.");
        m.add("restart.forced", "&f&l[PROXY RESTART] &cProxyrestart geforceerd. &7Direct herstarten...");
        m.add("restart.warn_chat", "&f&l[PROXY RESTART] &cDe proxy wordt herstart over &f{seconds}s&c.");
        m.add("restart.warn_title", "<red>PROXY RESTART</red>");
        m.add("restart.warn_subtitle", "<gray>Restart over </gray><white>{seconds}s</white>");
        m.add("restart.final_chat", "&f&l[PROXY RESTART] &cDe proxy gaat nu herstarten! &7Iedereen wordt gekickt.");
        m.add("restart.final_title", "<red><bold>PROXY RESTART</bold></red>");
        m.add("restart.final_subtitle", "<gray>Je wordt nu gekickt.</gray>");
        m.add("restart.kick", "<red>De proxy wordt herstart. Je kunt zo weer joinen.</red>");
        return m;
    }

    @Override
    public void initialize() {
        this.handler = new RestartHandler(this);
        getLifecycleManager().getCommandManager().registerFeatureCommand(new ProxyRestartCommand(this));
    }

    @Override
    public void disable() {
    }

    public RestartHandler getHandler() {
        return handler;
    }
}
