package nl.hauntedmc.proxyfeatures.features.playerinfo;

import nl.hauntedmc.commonlib.config.ConfigMap;
import nl.hauntedmc.commonlib.localization.MessageMap;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerConnectionInfoEntity;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.features.VelocityBaseFeature;
import nl.hauntedmc.proxyfeatures.features.playerinfo.command.PlayerInfoCommand;
import nl.hauntedmc.proxyfeatures.features.playerinfo.service.PlayerInfoService;
import nl.hauntedmc.proxyfeatures.features.playerinfo.meta.Meta;
import nl.hauntedmc.proxyfeatures.features.sanctions.entity.SanctionEntity;

public class PlayerInfo extends VelocityBaseFeature<Meta> {

    private ORMContext ormContext;
    private PlayerInfoService service;

    public PlayerInfo(ProxyFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);
        defaults.put("datetimeFormat", "dd-MM-yyyy HH:mm:ss");
        defaults.put("timezone", "");
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messages = new MessageMap();

        messages.add("playerinfo.cmd_usage", "&eGebruik: /playerinfo <speler>");
        messages.add("playerinfo.cmd_playerNotFound", "&cSpeler {player} niet gevonden.");
        messages.add("playerinfo.cmd_header", "&eInformatie over {player}:");

        messages.add("playerinfo.entry", "  &f{field}: &7{value}");
        messages.add("playerinfo.online_yes", "&aJa &7(op &f{server}&7)");
        messages.add("playerinfo.online_no", "&cNee");

        messages.add("playerinfo.field.name", "Naam");
        messages.add("playerinfo.field.uuid", "UUID");
        messages.add("playerinfo.field.online", "Online");
        messages.add("playerinfo.field.first_login", "Eerste login");
        messages.add("playerinfo.field.last_login", "Laatste login");
        messages.add("playerinfo.field.last_disconnect", "Laatste disconnect");
        messages.add("playerinfo.field.alts", "Mogelijke alts");

        messages.add("playerinfo.alts_none", "Geen");
        messages.add("playerinfo.permanent", "permanent");

        messages.add("playerinfo.punishments_header", "  &fActieve straffen:");
        messages.add("playerinfo.punishments_none", "    - &7Geen");
        messages.add("playerinfo.punishment_item",
                "    &f{type}&7: {reason} &8| &7tot: &f{expires} &8| &7sinds: &f{created}");

        return messages;
    }

    @Override
    public void initialize() {
        // ORM setup
        getLifecycleManager().getDataManager().initDataProvider(getFeatureName());
        getLifecycleManager().getDataManager()
                .registerConnection("ormConnection", DatabaseType.MYSQL, "player_data_rw");

        ormContext = getLifecycleManager().getDataManager().createORMContext(
                "ormConnection",
                PlayerEntity.class,
                PlayerConnectionInfoEntity.class,
                SanctionEntity.class
        ).orElseThrow();

        service = new PlayerInfoService(this);

        // Command
        getLifecycleManager().getCommandManager().registerFeatureCommand(new PlayerInfoCommand(this));
    }

    @Override
    public void disable() {
    }

    public ORMContext getOrmContext() {
        return ormContext;
    }

    public PlayerInfoService getService() {
        return service;
    }
}
