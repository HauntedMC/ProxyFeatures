package nl.hauntedmc.proxyfeatures.features.playerlanguage;

import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerLanguageEntity;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.api.APIRegistry;
import nl.hauntedmc.proxyfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.proxyfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.proxyfeatures.features.VelocityBaseFeature;
import nl.hauntedmc.proxyfeatures.features.playerlanguage.api.LanguageAPI;
import nl.hauntedmc.proxyfeatures.features.playerlanguage.command.LanguageCommand;
import nl.hauntedmc.proxyfeatures.features.playerlanguage.listener.LanguageListener;
import nl.hauntedmc.proxyfeatures.features.playerlanguage.meta.Meta;
import nl.hauntedmc.proxyfeatures.features.playerlanguage.service.LanguageService;

public class PlayerLanguage extends VelocityBaseFeature<Meta> {

    private LanguageService service;

    public PlayerLanguage(ProxyFeatures plugin) {
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
        m.add("language.player_only", "&8&l[&b&lLanguage&8&l]&r &cAlleen spelers kunnen dit commando gebruiken.");
        m.add("language.usage", "&8&l[&b&lLanguage&8&l]&r &7Gebruik: &f/language &b<LANG>&7 &8• &7Voorbeelden: &fNL, EN");
        m.add("language.invalid", "&8&l[&b&lLanguage&8&l]&r &cOnbekende taal &f{input}&c.");
        m.add("language.current", "&8&l[&b&lLanguage&8&l]&r &7Jouw huidige taal: &f{lang}");
        m.add("language.set", "&8&l[&b&lLanguage&8&l]&r &aTaal ingesteld op &f{lang}. Log opnieuw in om de taalweergave te vernieuwen.");
        m.add("language.default_auto",
                "&8&l[&b&lLanguage&8&l]&r &7Op basis van je locatie is je standaardtaal ingesteld op &f{language}&7. " +
                        "Wil je dit wijzigen? Gebruik &f/language &b<LANG>&7.");
        // Staff / others
        m.add("language.no_permission_others", "&8&l[&b&lLanguage&8&l]&r &cJe hebt geen permissie om de taal van anderen aan te passen.");
        m.add("language.not_found", "&8&l[&b&lLanguage&8&l]&r &cSpeler &f{target}&c niet gevonden.");
        m.add("language.set_other", "&8&l[&b&lLanguage&8&l]&r &aTaal van &f{target}&a ingesteld op &f{lang}");
        m.add("language.current_other", "&8&l[&b&lLanguage&8&l]&r &7Taal van &f{target}&7: &f{lang}");
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

    public LanguageService getService() {
        return service;
    }
}
