package nl.hauntedmc.proxyfeatures.features.friends;

import nl.hauntedmc.commonlib.config.ConfigMap;
import nl.hauntedmc.commonlib.localization.MessageMap;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.features.VelocityBaseFeature;
import nl.hauntedmc.proxyfeatures.features.friends.command.FriendCommand;
import nl.hauntedmc.proxyfeatures.features.friends.entity.FriendRelationEntity;
import nl.hauntedmc.proxyfeatures.features.friends.entity.FriendSettingsEntity;
import nl.hauntedmc.proxyfeatures.features.friends.entity.FriendsService;
import nl.hauntedmc.proxyfeatures.features.friends.meta.Meta;

public class Friends extends VelocityBaseFeature<Meta> {

    private ORMContext orm;
    private FriendsService service;

    public Friends(ProxyFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap cfg = new ConfigMap();
        cfg.put("enabled", false);
        return cfg;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap m = new MessageMap();
        final String P = "&8&l[&b&lFriends&8&l]&r ";

        // General / usage
        m.add("friend.player_only", P + "&cAlleen spelers kunnen dit commando gebruiken.");
        m.add("friend.usage",       P + "&eGebruik: &f/friend &7<subcommand> &8• &7Bijv.: &f/friend add &oNaam");

        // Overview / online
        m.add("friend.no_friends_online", P + "&7Er zijn momenteel geen vrienden online.");
        m.add("friend.online_header",     P + "&eVrienden online:");
        m.add("friend.online_entry",      "  &8» &f{player} &8(&7{server}&8)");

        // Requests
        m.add("friend.requests_header", P + "&eOpenstaande verzoeken:");
        m.add("friend.pending_requests",
                P + "&eJe hebt &f{count} &eopenstaand(e) verzoek(en). &7Gebruik &f/friend requests");

        // Add / Accept / Deny / Cancel
        m.add("friend.add.sent",      P + "&aVriendverzoek verzonden naar &f{player}&a.");
        m.add("friend.add.received",  P + "&e{player} &ewil je als vriend toevoegen. Gebruik &a/friend accept {player}&e.");
        m.add("friend.accepted",      P + "&aJij en &f{player} &azijn nu vrienden!");
        m.add("friend.accepted_many", P + "&aJe hebt &f{count} &avoorstel(len) geaccepteerd.");
        m.add("friend.denied",        P + "&cJe hebt het verzoek van &f{player} &cgeweigerd.");
        m.add("friend.denied_many",   P + "&eJe hebt &f{count} &everzoek(en) geweigerd.");
        m.add("friend.cancelled",     P + "&eJe hebt je vriendschapsverzoek naar &f{player} &egeannuleerd.");

        // Remove / Block / Unblock
        m.add("friend.removed",         P + "&e&f{player} &eis verwijderd uit je vriendenlijst.");
        m.add("friend.blocked",         P + "&eJe hebt &f{player} &egeblokkeerd &7(vriendschap verbroken).");
        m.add("friend.unblocked",       P + "&a{player} &ais gedeblokkeerd.");
        m.add("friend.already_blocked", P + "&cDeze speler is al geblokkeerd.");
        m.add("friend.not_blocked",     P + "&cDeze speler is niet geblokkeerd.");

        // Mode on/off
        m.add("friend.mode_disabled",     P + "&cHet vriendensysteem staat uit. Gebruik &f/friend enable&c.");
        m.add("friend.mode_enabled",      P + "&aVriendensysteem ingeschakeld.");
        m.add("friend.mode_now_disabled", P + "&eVriendensysteem uitgeschakeld; anderen kunnen geen verzoeken sturen.");

        // Validation / errors
        m.add("friend.already_friends",  P + "&cJullie zijn al vrienden.");
        m.add("friend.request_exists",   P + "&cEr is al een verzoek in behandeling.");
        m.add("friend.not_found",        P + "&cSpeler niet gevonden.");
        m.add("friend.no_requests",      P + "&cJe hebt geen openstaande verzoeken.");
        m.add("friend.not_friends",      P + "&cJullie zijn geen vrienden.");
        m.add("friend.not_online",       P + "&cDeze speler is niet online.");
        m.add("friend.self",             P + "&cJe kunt jezelf niet targeten.");
        m.add("friend.target_disabled",  P + "&cDeze speler accepteert geen vriendschapsverzoeken.");
        m.add("friend.you_blocked_target",
                P + "&cJe hebt &f{player} &cgeblokkeerd. Deblokkeer eerst met &f/friend unblock {player}&c.");
        m.add("friend.blocked_by_target",
                P + "&cJe kunt &f{player} &cniet toevoegen: je bent door deze speler geblokkeerd.");

        // List
        m.add("friend.list.header",  P + "&eVriendenlijst &7(&a{online}&7/&f{total}&7)");
        m.add("friend.list.entry",   "  &8» {status}&f{player}");

        // Info
        m.add("friend.info.header",  P + "&eInformatie over &f{player}&e:");
        m.add("friend.info.online",  "&7Status: &aOnline &7(&f{server}&7)");
        m.add("friend.info.offline", "&7Status: &cOffline &7(Laatst gezien: &f{last}&7)");

        // Connect
        m.add("friend.connected",    P + "&eVerbinden met de server van &a{player}&e...");
        m.add("friend.requests.incoming_entry", "  &8» &f{player} &8(&7wacht op jouw reactie&8)");
        m.add("friend.requests.outgoing_entry",   "&8» &f{player} &8(&7wacht op acceptatie&8)");
        m.add("friend.cannot_deny_outgoing",
                P + "&cJe kunt geen uitgaand verzoek weigeren. Gebruik &f/friend cancel {player} &com het verzoek te annuleren.");

        return m;
    }

    @Override
    public void initialize() {
        getLifecycleManager().getDataManager().initDataProvider(getFeatureName());
        getLifecycleManager().getDataManager().registerConnection(
                "orm", DatabaseType.MYSQL, "player_data_rw");

        orm = getLifecycleManager().getDataManager()
                .createORMContext("orm",
                        FriendRelationEntity.class,
                        FriendSettingsEntity.class,
                        PlayerEntity.class)
                .orElseThrow();

        service = new FriendsService(this);

        getLifecycleManager().getCommandManager()
                .registerFeatureCommand(new FriendCommand(this));
    }

    @Override
    public void disable() {}

    public ORMContext     getOrm()     { return orm; }
    public FriendsService getService() { return service; }
}
