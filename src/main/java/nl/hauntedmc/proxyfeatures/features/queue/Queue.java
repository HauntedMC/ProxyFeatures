package nl.hauntedmc.proxyfeatures.features.queue;

import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.proxyfeatures.api.io.localization.MessageMap;
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
        cfg.put("servers-whitelist", List.of("survival", "skyblock", "kitpvp", "creative", "minigames"));
        cfg.put("poll-interval-seconds", 2);
        cfg.put("update-interval-seconds", 15);
        cfg.put("grace-seconds", 60);
        return cfg;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap m = new MessageMap();
        m.add("queue.join.denied.full", "&eDe &e{server} server is vol. Je staat op plek &f#{position}&e in de wachtrij.");
        m.add("queue.join.bypass", "&aBypass actief: je gaat direct door naar &f{server}&a.");
        m.add("queue.join.already_in_queue", "&eJe staat al in de wachtrij voor &f{server}&e (positie &f#{position}&e).");
        m.add("queue.join.moved_between_queues", "&eJe bent verplaatst naar de wachtrij voor &f{server}&e (positie &f#{position}&e).");
        m.add("queue.join.connection_failure", "&cJe kon helaas niet worden verbonden met &7{server}&c: &f{reason}");
        m.add("queue.status.header", "&eWachtrij status voor &f{server}&e:");
        m.add("queue.status.line", "  &7Positie: &f#{position}");
        m.add("queue.status.none", "&eJe staat momenteel niet in een wachtrij.");
        m.add("queue.status.not_enabled", "&cWachtrij is niet ingeschakeld voor &f{server}&c.");
        m.add("queue.actionbar.status", "&7Je staat in de wachtrij voor &6{server} &f(&7Plek &f#{position}&f)");
        m.add("queue.actionbar.leave", "&7Om de wachtrij te verlaten typ &f/queue leave");
        m.add("queue.actionbar.rank", "&7Koop een rank op &astore.hauntedmc.nl &7om prioriteit te krijgen");
        m.add("queue.advance.now_connecting", "&aEr is plek vrijgekomen! Je wordt nu verbonden met &f{server}&a...");
        m.add("queue.grace.active", "&eJe plek is &f{seconds}s&e gereserveerd als je verbinding verbreekt.");
        m.add("queue.cmd.usage", "&eGebruik: /queue | /queue leave | /queue info <server>");
        m.add("queue.cmd.info.header", "&aInfo over de wachtrij voor &f{server}&a:");
        m.add("queue.cmd.info.entry", "  &7#{idx}: &f{name} &8(perm {priority})");
        m.add("queue.cmd.info.empty", "&7Geen spelers in de wachtrij.");
        m.add("queue.cmd.no_permission", "&cJe hebt geen toestemming hiervoor.");
        m.add("queue.cmd.target_not_enabled", "&cWachtrij voor &f{server}&c is uitgeschakeld.");
        m.add("queue.cmd.leave.done", "&aJe hebt de wachtrij voor &f{server}&a verlaten.");
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

        int pollSec = Math.max(1, getConfigHandler().get("poll-interval-seconds", Integer.class, 2));
        int updateSec = Math.max(1, getConfigHandler().get("update-interval-seconds", Integer.class, 15));

        manager.startSchedulers(Duration.ofSeconds(pollSec), Duration.ofSeconds(updateSec));
    }

    @Override
    public void disable() {
        if (manager != null) {
            manager.shutdown();
        }
    }
}
