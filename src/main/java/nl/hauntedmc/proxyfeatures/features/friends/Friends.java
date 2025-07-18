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

        m.add("friend.usage",            "&eGebruik: /friends <sub‑command>");
        m.add("friend.add.sent",         "&aVriendverzoek verzonden naar {player}.");
        m.add("friend.add.received",     "&e{player} wil je als vriend toevoegen. Gebruik &a/friend accept {player} &eom te accepteren.");
        m.add("friend.accepted",         "&aJij en {player} zijn nu vrienden!");
        m.add("friend.denied",           "&cJe hebt het verzoek van {player} geweigerd.");
        m.add("friend.removed",          "&e{player} is verwijderd uit je vriendenlijst.");
        m.add("friend.blocked",          "&eJe hebt {player} geblokkeerd.");
        m.add("friend.unblocked",        "&a{player} is gedeblokkeerd.");
        m.add("friend.already_blocked",  "&cDeze speler is al geblokkeerd.");
        m.add("friend.not_blocked",      "&cDeze speler is niet geblokkeerd.");
        m.add("friend.mode_disabled",    "&cVriendensysteem staat uit. Gebruik /friend enable om het in te schakelen.");
        m.add("friend.mode_enabled",     "&aVriendensysteem ingeschakeld.");
        m.add("friend.mode_now_disabled","&eVriendensysteem uitgeschakeld; anderen kunnen geen verzoeken sturen.");
        m.add("friend.already_friends",  "&cJullie zijn al vrienden.");
        m.add("friend.request_exists",   "&cEr is al een verzoek in behandeling.");
        m.add("friend.not_found",        "&cSpeler niet gevonden.");
        m.add("friend.no_requests",      "&cJe hebt geen openstaande verzoeken.");
        m.add("friend.list.header",      "&eVriendenlijst &7({online}/{total})");
        m.add("friend.list.entry",       "&7{status}{player}");
        m.add("friend.info.header",      "&eInfo over {player}:");
        m.add("friend.info.online",      "&7Status: &aOnline &7(&f{server}&7)");
        m.add("friend.info.offline",     "&7Status: &cOffline &7(Laatst gezien: &f{last}&7)");
        m.add("friend.info.none",        "&cGeen informatie beschikbaar.");
        m.add("friend.connected",        "&eVerbinden met server van &a{player}&e...");
        m.add("friend.not_online",       "&cDeze speler is niet online.");
        m.add("friend.not_friends",      "&cJullie zijn geen vrienden.");
        return m;
    }

    @Override
    public void initialize() {
        // ORM
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

        // Command
        getLifecycleManager().getCommandManager()
                .registerFeatureCommand(new FriendCommand(this));
    }

    @Override public void disable() {}

    public ORMContext     getOrm()     { return orm; }
    public FriendsService getService() { return service; }
}
