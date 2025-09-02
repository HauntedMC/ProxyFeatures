package nl.hauntedmc.proxyfeatures.features.playerlanguage;

import nl.hauntedmc.commonlib.config.ConfigMap;
import nl.hauntedmc.commonlib.localization.MessageMap;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.common.util.APIRegistry;
import nl.hauntedmc.proxyfeatures.features.VelocityBaseFeature;
import nl.hauntedmc.proxyfeatures.features.playerlanguage.api.LanguageAPI;
import nl.hauntedmc.proxyfeatures.features.playerlanguage.meta.Meta;
import nl.hauntedmc.proxyfeatures.features.playerlanguage.command.LanguageCommand;
import nl.hauntedmc.proxyfeatures.features.playerlanguage.listener.LanguageListener;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerLanguageEntity;
import nl.hauntedmc.proxyfeatures.features.playerlanguage.service.LanguageService;

public class PlayerLanguage extends VelocityBaseFeature<Meta> {

    private LanguageService service;

    public PlayerLanguage(ProxyFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap cfg = new ConfigMap();
        cfg.put("enabled", true);
        return cfg;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap m = new MessageMap();
        m.add("language.player_only", "&8&l[&b&lLanguage&8&l]&r &cAlleen spelers kunnen dit commando gebruiken.");
        m.add("language.usage", "&8&l[&b&lLanguage&8&l]&r &7Gebruik: &f/language &b<LANG>&7 &8• &7Voorbeelden: &fNL, EN");
        m.add("language.invalid", "&8&l[&b&lLanguage&8&l]&r &cOnbekende taal &f{input}&c.");
        m.add("language.current", "&8&l[&b&lLanguage&8&l]&r &7Jouw huidige taal: &f{lang}");
        m.add("language.set", "&8&l[&b&lLanguage&8&l]&r &aTaal ingesteld op &f{lang}. Log opnieuw in om de taalweergave te vernieuwen.");
        return m;
    }

    @Override
    public void initialize() {
        // Data connection
        getLifecycleManager().getDataManager().initDataProvider(getFeatureName());
        getLifecycleManager().getDataManager().registerConnection(
                "orm", DatabaseType.MYSQL, "player_data_rw");

        ORMContext orm = getLifecycleManager().getDataManager()
                .createORMContext("orm",
                        PlayerLanguageEntity.class,
                        PlayerEntity.class)
                .orElseThrow();

        service = new LanguageService(this, orm);

        // Command + listener
        getLifecycleManager().getCommandManager().registerFeatureCommand(new LanguageCommand(this));
        getLifecycleManager().getListenerManager().registerListener(new LanguageListener(this));

        // API (through proxy APIRegistry)
        APIRegistry.register(LanguageAPI.class, service);
    }

    @Override
    public void disable() {
        APIRegistry.unregister(LanguageAPI.class);
    }

    public LanguageService getService() { return service; }
}
