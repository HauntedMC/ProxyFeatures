package nl.hauntedmc.proxyfeatures.features.queue;

import nl.hauntedmc.commonlib.config.ConfigMap;
import nl.hauntedmc.commonlib.localization.MessageMap;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.features.VelocityBaseFeature;
import nl.hauntedmc.proxyfeatures.features.queue.command.QueueCommand;
import nl.hauntedmc.proxyfeatures.features.queue.listener.ConnectionListener;
import nl.hauntedmc.proxyfeatures.features.queue.listener.PreConnectListener;
import nl.hauntedmc.proxyfeatures.features.queue.meta.Meta;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.List;

public class Queue extends VelocityBaseFeature<Meta> {

    private QueueManager manager;

    public Queue(ProxyFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap cfg = new ConfigMap();
        cfg.put("enabled", false);
        cfg.put("auto-activate", true);
        cfg.put("servers-whitelist", List.of("survival", "skyblock", "kitpvp", "creative", "minigames"));
        cfg.put("poll-interval-seconds", 2);
        cfg.put("update-interval-seconds", 15);
        cfg.put("grace-seconds", 120);
        return cfg;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap m = new MessageMap();
        m.add("queue.join.denied.full", "&e{server} is vol. Je bent in de wachtrij geplaatst.");
        m.add("queue.join.denied.full_withpos", "&e{server} is vol. Je staat op &f#{position}&e in de wachtrij.");
        m.add("queue.join.bypass", "&aBypass actief: je gaat direct door naar &f{server}&a.");
        m.add("queue.join.already_in_queue", "&eJe staat al in de wachtrij voor &f{server}&e (positie &f#{position}&e).");
        m.add("queue.join.moved_between_queues", "&eJe bent verplaatst naar de wachtrij voor &f{server}&e (positie &f#{position}&e).");
        m.add("queue.status.header", "&eWachtrij status voor &f{server}&e:");
        m.add("queue.status.line", "  &7Positie: &f#{position}");
        m.add("queue.status.none", "&eJe staat momenteel niet in een wachtrij.");
        m.add("queue.status.not_enabled", "&cWachtrij is niet ingeschakeld voor &f{server}&c.");
        m.add("queue.advance.now_connecting", "&aEr is plek vrijgekomen! Je wordt nu verbonden met &f{server}&a...");
        m.add("queue.grace.active", "&eJe plek is &f{seconds}s&e gereserveerd als je verbinding verbreekt.");
        m.add("queue.grace.lost", "&cJe reservatie voor &f{server}&c is verlopen.");
        m.add("queue.cmd.usage", "&eGebruik: /queue | /queue top <server> | /queue skip <player>");
        m.add("queue.cmd.top.header", "&aTop van de wachtrij voor &f{server}&a:");
        m.add("queue.cmd.top.entry", "  &7#{idx}: &f{name} &8(perm {priority})");
        m.add("queue.cmd.top.empty", "&7Geen spelers in de wachtrij.");
        m.add("queue.cmd.player_not_found", "&cSpeler {player} niet gevonden.");
        m.add("queue.cmd.no_permission", "&cJe hebt geen toestemming hiervoor.");
        m.add("queue.cmd.target_not_enabled", "&cWachtrij voor &f{server}&c is uitgeschakeld.");
        m.add("queue.cmd.skip.done", "&a{player} verplaatst naar de kop van de wachtrij voor &f{server}&a.");
        m.add("queue.cmd_notPlayer", "&cAlleen spelers kunnen dit commando uitvoeren.");
        m.add("queue.generic.server", "Server");
        return m;
    }

    @Override
    public void initialize() {
        Logger logger = getPlugin().getLogger();
        this.manager = new QueueManager(this, logger);

        getLifecycleManager().getListenerManager().registerListener(new PreConnectListener(this, manager));
        getLifecycleManager().getListenerManager().registerListener(new ConnectionListener(manager));

        getLifecycleManager().getCommandManager().registerFeatureCommand(new QueueCommand(this, manager));

        int pollSec = ((Number) getConfigHandler().getSetting("poll-interval-seconds")).intValue();
        int updateSec = ((Number) getConfigHandler().getSetting("update-interval-seconds")).intValue();

        manager.startSchedulers(Duration.ofSeconds(pollSec), Duration.ofSeconds(updateSec));
    }

    @Override
    public void disable() {
        if (manager != null) {
            manager.shutdown();
        }
    }
}
