package nl.hauntedmc.proxyfeatures.features.restart;

import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.proxyfeatures.api.io.localization.MessageMap;
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
        defaults.put("schedule_time_zone", "system");
        defaults.put("schedule_check_interval_seconds", 5);
        defaults.put("schedule_announce_hours_before", 5);

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
        m.add("restart.schedule.cmd_usage", "&eGebruik: /proxyrestart schedule &7<datum of dag> <tijd> &8of &7<datumTtijd>");
        m.add("restart.schedule.invalid_datetime", "&cOngeldige datum of tijd. Voorbeelden: &f2026-01-28 18:00&c, &f28-01-2026 18:00&c, &fmonday 18:00&c, &f2026-01-28T18:00");
        m.add("restart.schedule.time_must_be_future", "&cDe opgegeven tijd moet in de toekomst liggen.");
        m.add("restart.schedule.already_scheduled", "&cEr staat al een restart ingepland op &f{datetime}&c. Gebruik &f/proxyrestart cancel&c om dit te annuleren.");
        m.add("restart.schedule.blocked_by_running", "&cEr is al een restart bezig. Inplannen kan nu niet.");
        m.add("restart.schedule.set", "&aProxy restart ingepland voor &f{datetime}&a.");
        m.add("restart.schedule.announce_chat", "&f&l[PROXY RESTART] &cEr staat een proxy restart ingepland op &f{datetime}&c.");
        m.add("restart.cancel.cmd_usage", "&eGebruik: /proxyrestart cancel");
        m.add("restart.cancel.none", "&eEr staat geen restart ingepland.");
        m.add("restart.cancel.ok", "&aDe ingeplande restart is geannuleerd.");
        m.add("restart.status.cmd_usage", "&eGebruik: /proxyrestart status");
        m.add("restart.status.none", "&eEr staat geen restart ingepland.");
        m.add("restart.status.scheduled", "&aIngepland: &f{datetime}&a.");

        return m;
    }

    @Override
    public void initialize() {
        this.handler = new RestartHandler(this);
        getLifecycleManager().getCommandManager().registerFeatureCommand(new ProxyRestartCommand(this));
    }

    @Override
    public void disable() {
        if (this.handler != null) {
            this.handler.shutdown();
        }
    }

    public RestartHandler getHandler() {
        return handler;
    }
}
