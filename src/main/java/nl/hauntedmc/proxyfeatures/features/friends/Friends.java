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
        cfg.put("enabled", true);
        cfg.put("maxFriends", 100);
        return cfg;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap m = new MessageMap();
        m.add("friend.player_only", "&8&l[&b&lFriends&8&l]&r &cAlleen spelers kunnen dit commando gebruiken.");
        m.add("friend.usage",       "&8&l[&b&lFriends&8&l]&r &eGebruik: /friends <sub‑command>");
        m.add("friend.no_friends_online", "&8&l[&b&lFriends&8&l]&r &7Geen vrienden online.");
        m.add("friend.online_header",     "&8&l[&b&lFriends&8&l]&r &eVrienden online:");
        m.add("friend.requests_header",   "&8&l[&b&lFriends&8&l]&r &eOpenstaande verzoeken:");
        m.add("friend.online_entry",      "&7- {player} &7(&f{server}&7)");
        m.add("friend.pending_requests",
                "&8&l[&b&lFriends&8&l]&r &eJe hebt &f{count} &eopenstaand(e) verzoek(en). &7/friend requests");
        m.add("friend.add.sent",      "&8&l[&b&lFriends&8&l]&r &aVriendverzoek verzonden naar {player}.");
        m.add("friend.add.received",  "&8&l[&b&lFriends&8&l]&r &e{player} wil je als vriend toevoegen. Gebruik &a/friend accept {player}&e.");
        m.add("friend.accepted",      "&8&l[&b&lFriends&8&l]&r &aJij en {player} zijn nu vrienden!");
        m.add("friend.denied",        "&8&l[&b&lFriends&8&l]&r &cJe hebt het verzoek van {player} geweigerd.");
        m.add("friend.removed",       "&8&l[&b&lFriends&8&l]&r &e{player} is verwijderd uit je vriendenlijst.");
        m.add("friend.blocked",       "&8&l[&b&lFriends&8&l]&r &eJe hebt {player} geblokkeerd.");
        m.add("friend.unblocked",     "&8&l[&b&lFriends&8&l]&r &a{player} is gedeblokkeerd.");
        m.add("friend.already_blocked","&8&l[&b&lFriends&8&l]&r &cDeze speler is al geblokkeerd.");
        m.add("friend.not_blocked",   "&8&l[&b&lFriends&8&l]&r &cDeze speler is niet geblokkeerd.");
        m.add("friend.mode_disabled", "&8&l[&b&lFriends&8&l]&r &cVriendensysteem staat uit. Gebruik /friend enable.");
        m.add("friend.mode_enabled",  "&8&l[&b&lFriends&8&l]&r &aVriendensysteem ingeschakeld.");
        m.add("friend.mode_now_disabled","&8&l[&b&lFriends&8&l]&r &eVriendensysteem uitgeschakeld; anderen kunnen geen verzoeken sturen.");
        m.add("friend.already_friends","&8&l[&b&lFriends&8&l]&r &cJullie zijn al vrienden.");
        m.add("friend.request_exists","&8&l[&b&lFriends&8&l]&r &cEr is al een verzoek in behandeling.");
        m.add("friend.not_found",      "&8&l[&b&lFriends&8&l]&r &cSpeler niet gevonden.");
        m.add("friend.no_requests",    "&8&l[&b&lFriends&8&l]&r &cJe hebt geen openstaande verzoeken.");
        m.add("friend.not_friends",    "&8&l[&b&lFriends&8&l]&r &cJullie zijn geen vrienden.");
        m.add("friend.not_online",     "&8&l[&b&lFriends&8&l]&r &cDeze speler is niet online.");
        m.add("friend.list.header",    "&8&l[&b&lFriends&8&l]&r &eVriendenlijst &7({online}/{total})");
        m.add("friend.list.entry",     "&7{status}{player}");
        m.add("friend.info.header",    "&8&l[&b&lFriends&8&l]&r &eInfo over {player}:");
        m.add("friend.info.online",    "&7Status: &aOnline &7(&f{server}&7)");
        m.add("friend.info.offline",   "&7Status: &cOffline &7(Laatst gezien: &f{last}&7)");
        m.add("friend.connected",      "&8&l[&b&lFriends&8&l]&r &eVerbinden met server van &a{player}&e...");
        m.add("friend.self", "&8&l[&b&lFriends&8&l]&r &cJe kunt jezelf niet targeten.");
        m.add("friend.requests.outgoing_entry", "&7- {player} &7(&ewacht op acceptatie&7)");
        m.add("friend.cancelled", "&8&l[&b&lFriends&8&l]&r &eJe hebt je vriendschapsverzoek naar {player} geannuleerd.");
        m.add("friend.cannot_deny_outgoing","&8&l[&b&lFriends&8&l]&r &cJe kunt geen uitgaand verzoek weigeren. Gebruik &e/friend cancel {player} &com het verzoek te annuleren.");

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