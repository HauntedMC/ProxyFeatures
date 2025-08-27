package nl.hauntedmc.proxyfeatures.features.sanctions;

import nl.hauntedmc.commonlib.config.ConfigMap;
import nl.hauntedmc.commonlib.localization.MessageMap;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.features.VelocityBaseFeature;
import nl.hauntedmc.proxyfeatures.features.sanctions.command.*;
import nl.hauntedmc.proxyfeatures.features.sanctions.entity.SanctionEntity;
import nl.hauntedmc.proxyfeatures.features.sanctions.listener.ConnectListener;
import nl.hauntedmc.proxyfeatures.features.sanctions.meta.Meta;
import nl.hauntedmc.proxyfeatures.features.sanctions.service.DiscordService;
import nl.hauntedmc.proxyfeatures.features.sanctions.service.SanctionsService;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.proxyfeatures.features.sanctions.service.ServiceLookup;

public class Sanctions extends VelocityBaseFeature<Meta> {

    private ORMContext orm;
    private SanctionsService service;
    private ServiceLookup serviceLookup;
    private DiscordService discordService;

    public Sanctions(ProxyFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap cfg = new ConfigMap();
        cfg.put("enabled", true);
        cfg.put("expirySweepSeconds", 30);
        cfg.put("discordWebhookURL", "");
        cfg.put("appealURL", "https://hauntedmc.nl/appeal");
        return cfg;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap m = new MessageMap();

        // Usage / errors
        m.add("sanctions.player_only", "&8&l[&c&lSanctions&8&l]&r &cAlleen spelers kunnen dit commando gebruiken.");
        m.add("sanctions.not_found", "&8&l[&c&lSanctions&8&l]&r &cSpeler niet gevonden.");
        m.add("sanctions.ip_invalid", "&8&l[&c&lSanctions&8&l]&r &cOngeldig IP-adres.");
        m.add("sanctions.invalid_length", "&8&l[&c&lSanctions&8&l]&r &cOngeldige lengte. Gebruik &fP&c (permanent) of combinaties zoals &f7d&c, &f12h&c, &f1d6h&c, &f1w&c, &f1mo&c, &f1y&c.");
        m.add("sanctions.self", "&8&l[&c&lSanctions&8&l]&r &cJe kunt dit niet op jezelf uitvoeren.");
        m.add("sanctions.already_banned", "&8&l[&c&lSanctions&8&l]&r &cDeze speler/IP is al verbannen.");
        m.add("sanctions.already_muted", "&8&l[&c&lSanctions&8&l]&r &cDeze speler is al gemute.");
        m.add("sanctions.perm_block", "&8&l[&c&lSanctions&8&l]&r &cAlleen hogere staffleden mogen permanente sancties (ban/mute) uitvoeren.");
        m.add("sanctions.exempt_target", "&8&l[&c&lSanctions&8&l]&r &cDit doelwit is beschermd en kan niet worden aangepakt.");
        m.add("sanctions.reason_required", "&8&l[&c&lSanctions&8&l]&r &cJe moet een reden opgeven.");
        m.add("sanctions.internal_error", "&8&l[&c&lSanctions&8&l]&r &cEr ging iets mis. Probeer het later opnieuw.");

        // Command usage
        m.add("sanctions.usage.ban", "&8&l[&c&lSanctions&8&l]&r &eGebruik: &f/ban <naam> <lengte> <reden>");
        m.add("sanctions.usage.banip", "&8&l[&c&lSanctions&8&l]&r &eGebruik: &f/banip <ip> <lengte> <reden>");
        m.add("sanctions.usage.mute", "&8&l[&c&lSanctions&8&l]&r &eGebruik: &f/mute <naam> <lengte> <reden>");
        m.add("sanctions.usage.warn", "&8&l[&c&lSanctions&8&l]&r &eGebruik: &f/warn <naam> <reden>");
        m.add("sanctions.usage.kick", "&8&l[&c&lSanctions&8&l]&r &eGebruik: &f/kick <naam> <reden>");
        m.add("sanctions.usage.unban", "&8&l[&c&lSanctions&8&l]&r &eGebruik: &f/unban <naam>");
        m.add("sanctions.usage.unmute", "&8&l[&c&lSanctions&8&l]&r &eGebruik: &f/unmute <naam>");
        m.add("sanctions.usage.unbanip", "&8&l[&c&lSanctions&8&l]&r &eGebruik: &f/unbanip <ip>");
        m.add("sanctions.usage.sanctionlist", "&8&l[&c&lSanctions&8&l]&r &eGebruik: &f/sanctionlist <naam> <all|active>");

        // Not currently sanctioned
        m.add("sanctions.not_banned_player", "&8&l[&c&lSanctions&8&l]&r &cDeze speler is momenteel niet verbannen.");
        m.add("sanctions.not_banned_ip", "&8&l[&c&lSanctions&8&l]&r &cDit IP-adres is momenteel niet verbannen.");
        m.add("sanctions.not_muted", "&8&l[&c&lSanctions&8&l]&r &cDeze speler is momenteel niet gemute.");

        // Success feedback to executor
        m.add("sanctions.unbanned", "&8&l[&c&lSanctions&8&l]&r &aBan opgeheven voor &f{target}&a.");
        m.add("sanctions.unbanned_ip", "&8&l[&c&lSanctions&8&l]&r &aBan opgeheven voor &f{ip}&a.");
        m.add("sanctions.unmuted", "&8&l[&c&lSanctions&8&l]&r &aMute opgeheven voor &f{target}&a.");

        // Player facing
        m.add("sanctions.notify.unmuted", "&aJe mute is opgeheven.");
        m.add("sanctions.notify.muted.temp", "&eJe bent gemute {duration}. &7Reden: &f{reason}");
        m.add("sanctions.notify.muted.perm", "&eJe bent &lpermanent &r&egemute. &7Reden: &f{reason}");
        m.add("sanctions.notify.warn", "&eJe bent gewaarschuwd. &7Reden: &f{reason}");
        m.add("sanctions.notify.kick", "&eJe bent gekickt. &7Reden: &f{reason}");

        // Disconnect messages (show appeal URL from config)
        m.add("sanctions.disconnect.banned.temp",
                "&cJe bent verbannen {duration}.\n&7Reden: &f{reason}\n&7Appeal: &f{appeal}");
        m.add("sanctions.disconnect.banned.perm",
                "&cJe bent &lpermanent &r&cverbannen.\n&7Reden: &f{reason}\n&7Appeal: &f{appeal}");

        // Staff announcements
        m.add("sanctions.announce.unban", "&8&l[&c&lSanctions&8&l]&r &f{actor} &7heeft de ban van &a{target} &7opgeheven.");
        m.add("sanctions.announce.unmute", "&8&l[&c&lSanctions&8&l]&r &f{actor} &7heeft de mute van &a{target} &7opgeheven.");
        m.add("sanctions.announce.unbanip", "&8&l[&c&lSanctions&8&l]&r &f{actor} &7heeft de IP-ban van &a{ip} &7opgeheven.");
        m.add("sanctions.announce.ban.temp",
                "&8&l[&c&lSanctions&8&l]&r &f{actor} &7heeft &c{target} &7verbannen {duration}. &7Reden: &f{reason}");
        m.add("sanctions.announce.ban.perm",
                "&8&l[&c&lSanctions&8&l]&r &f{actor} &7heeft &c{target} &7permanent verbannen. &7Reden: &f{reason}");
        m.add("sanctions.announce.banip.temp",
                "&8&l[&c&lSanctions&8&l]&r &f{actor} &7heeft IP &c{ip} &7verbannen {duration}. &7Reden: &f{reason}");
        m.add("sanctions.announce.banip.perm",
                "&8&l[&c&lSanctions&8&l]&r &f{actor} &7heeft IP &c{ip} &7permanent verbannen. &7Reden: &f{reason}");
        m.add("sanctions.announce.mute.temp",
                "&8&l[&c&lSanctions&8&l]&r &f{actor} &7heeft &e{target} &7gemute {duration}. &7Reden: &f{reason}");
        m.add("sanctions.announce.mute.perm",
                "&8&l[&c&lSanctions&8&l]&r &f{actor} &7heeft &e{target} &7permanent gemute. &7Reden: &f{reason}");
        m.add("sanctions.announce.warn",
                "&8&l[&c&lSanctions&8&l]&r &f{actor} &7heeft &6{target} &7gewaarschuwd. &7Reden: &f{reason}");
        m.add("sanctions.announce.kick",
                "&8&l[&c&lSanctions&8&l]&r &f{actor} &7heeft &c{target} &7gekickt. &7Reden: &f{reason}");

        // Chat blocked
        m.add("sanctions.chat_blocked", "&cJe bent gemute en kunt niet chatten. &7Resterende mute tijd: &f{remaining}");

        // Sanction list
        m.add("sanctions.list.header", "&8&l[&c&lSanctions&8&l]&r &eSancties voor &f{player}&e — &f{mode} &7(&f{count}&7)");
        m.add("sanctions.list.empty", "&8&l[&c&lSanctions&8&l]&r &7Geen sancties gevonden voor &f{player}&7 (&f{mode}&7).");
        m.add("sanctions.list.entry.line1", "&8• &7Type: &f{type} &8| &7Status: {status} &8| &7Door: &f{actor}");
        m.add("sanctions.list.entry.line2", "&8  &7Op: &f{created} &8| &7Duur: &f{duration}");
        m.add("sanctions.list.entry.line2b", "&8  &7Verloopt: &f{expires}");
        m.add("sanctions.list.entry.line3", "&8  &7Reden: &f{reason}");
        m.add("sanctions.list.entry.separator", "");
        return m;
    }

    @Override
    public void initialize() {
        getLifecycleManager().getDataManager().initDataProvider(getFeatureName());
        getLifecycleManager().getDataManager().registerConnection(
                "orm", DatabaseType.MYSQL, "player_data_rw");

        // IMPORTANT: include PlayerEntity.class in ORM context
        orm = getLifecycleManager().getDataManager()
                .createORMContext("orm",
                        SanctionEntity.class,
                        PlayerEntity.class)
                .orElseThrow();

        service = new SanctionsService(this);
        serviceLookup = new ServiceLookup(this);
        discordService = new DiscordService(this);

        // Commands
        getLifecycleManager().getCommandManager().registerFeatureCommand(new BanCommand(this));
        getLifecycleManager().getCommandManager().registerFeatureCommand(new BanIpCommand(this));
        getLifecycleManager().getCommandManager().registerFeatureCommand(new MuteCommand(this));
        getLifecycleManager().getCommandManager().registerFeatureCommand(new WarnCommand(this));
        getLifecycleManager().getCommandManager().registerFeatureCommand(new KickCommand(this));
        getLifecycleManager().getCommandManager().registerFeatureCommand(new UnbanCommand(this));
        getLifecycleManager().getCommandManager().registerFeatureCommand(new UnmuteCommand(this));
        getLifecycleManager().getCommandManager().registerFeatureCommand(new UnbanIpCommand(this));
        getLifecycleManager().getCommandManager().registerFeatureCommand(new SanctionListCommand(this));

        // Listeners
        getLifecycleManager().getListenerManager().registerListener(new ConnectListener(this));

        // Expiry sweeper
        int sweep = (int) getConfigHandler().getSetting("expirySweepSeconds");
        getLifecycleManager().getTaskManager().scheduleRepeatingTask(() -> {
            service.sweepExpiries();
        }, sweep * 1000L);
    }

    @Override
    public void disable() {
    }

    public ORMContext getOrm() {
        return orm;
    }

    public SanctionsService getService() {
        return service;
    }

    public ServiceLookup getServiceLookup() {
        return serviceLookup;
    }

    public DiscordService getDiscordService() {
        return discordService;
    }

}
