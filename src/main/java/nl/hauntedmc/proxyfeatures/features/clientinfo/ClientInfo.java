package nl.hauntedmc.proxyfeatures.features.clientinfo;

import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.proxyfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.proxyfeatures.features.VelocityBaseFeature;
import nl.hauntedmc.proxyfeatures.features.clientinfo.command.ClientInfoBrigadierCommand;
import nl.hauntedmc.proxyfeatures.features.clientinfo.entity.PlayerClientInfoSettingsEntity;
import nl.hauntedmc.proxyfeatures.features.clientinfo.internal.ClientInfoAdvisor;
import nl.hauntedmc.proxyfeatures.features.clientinfo.internal.ClientInfoSettingsService;
import nl.hauntedmc.proxyfeatures.features.clientinfo.listener.PlayerListener;
import nl.hauntedmc.proxyfeatures.features.clientinfo.meta.Meta;

import java.util.Map;

public class ClientInfo extends VelocityBaseFeature<Meta> {

    private ClientInfoAdvisor advisor;

    public ClientInfo(ProxyFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);

        // ---- Notify pipeline (push recommendations) ----
        defaults.put("notify.enabled", true);
        defaults.put("notify.debounce_millis", 10000L);
        defaults.put("notify.cooldown_millis", 5L * 60L * 1000L);
        defaults.put("notify.only_send_if_changed", true);
        defaults.put("notify.only_once_per_session", false);

        // ---- Checks (global) ----
        defaults.put("checks.view_distance.enabled", true);
        defaults.put("checks.chat_mode.enabled", true);
        defaults.put("checks.particles.enabled", true);

        // ---- Recommendations (global) ----
        defaults.put("recommend.view_distance_min", 5);
        defaults.put("recommend.chat_mode", "SHOWN");      // SHOWN | COMMANDS_ONLY | HIDDEN
        defaults.put("recommend.particles", "ALL");        // ALL | DECREASED | MINIMAL

        // ---- Profiles (optional per-server overrides) ----
        // profiles:
        //   survival:
        //     servers: [ "survival", "survival2" ]
        //     checks:
        //       view_distance: true
        //     recommend:
        //       view_distance_min: 8
        defaults.put("profiles", Map.of());

        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap m = new MessageMap();

        // Push recommendations
        m.add("clientinfo.header", "  &fEr zijn aanbevelingen voor je client instellingen:");
        m.add("clientinfo.recommendation", "    • &f{setting_name}: &c{setting_found} &7-> &a{setting_recommended}{help}");
        m.add("clientinfo.no_recommendations", "  &aJe client instellingen zien er goed uit!");
        m.add("clientinfo.footer_help_hint", "  &7Tip: gebruik &f/clientinfo help &7voor uitleg hoe je dit aanpast.");

        // Setting labels (localized)
        m.add("clientinfo.setting.view_distance", "Render Distance");
        m.add("clientinfo.setting.chat_mode", "Chat Mode");
        m.add("clientinfo.setting.particles", "Particles");

        // Command text
        m.add("clientinfo.cmd_usage", "&eGebruik: &f/clientinfo &7[speler] &8| &f/clientinfo recommend &7[speler] &8| &f/clientinfo toggle &8| &f/clientinfo help");
        m.add("clientinfo.cmd_header", "&6&lClient instellingen van &f{player}&6&l:");
        m.add("clientinfo.cmd_section.settings", "  &7&lInstellingen:");
        m.add("clientinfo.cmd_section.recommendations", "  &7&lAanbevelingen:");
        m.add("clientinfo.cmd_entry", "    • &f{setting}: &7{value}");
        m.add("clientinfo.cmd_playerNotFound", "&cSpeler &f{player} &cniet gevonden.");

        // Toggle
        m.add("clientinfo.toggle.enabled", "&7Client aanbevelingen staan nu &aaan&7.");
        m.add("clientinfo.toggle.disabled", "&7Client aanbevelingen staan nu &cuit&7.");

        // Help
        m.add("clientinfo.help.header", "&eClient instellingen aanpassen:");
        m.add("clientinfo.help.render_distance", "  • &fRender Distance&7: &aOpties &8-> &aVideo Settings &8-> &aRender Distance");
        m.add("clientinfo.help.chat_mode", "  • &fChat Mode&7: &aOpties &8-> &aChat Settings &8-> &aChat");
        m.add("clientinfo.help.particles", "  • &fParticles&7: &aOpties &8-> &aVideo Settings &8-> &aParticles");
        m.add("clientinfo.help.footer", "&7Je kunt de meldingen uitzetten met &6/clientinfo toggle");

        return m;
    }

    @Override
    public void initialize() {
        // DB for per-player notification toggle
        getLifecycleManager().getDataManager().initDataProvider(getFeatureName());
        getLifecycleManager().getDataManager().registerConnection("ormConnection", DatabaseType.MYSQL, "player_data_rw");
        ORMContext ormContext = getLifecycleManager().getDataManager()
                .createORMContext("ormConnection", PlayerClientInfoSettingsEntity.class, PlayerEntity.class)
                .orElseThrow();

        ClientInfoSettingsService settingsService = new ClientInfoSettingsService(ormContext);
        this.advisor = new ClientInfoAdvisor(this, settingsService);

        getLifecycleManager().getListenerManager().registerListener(new PlayerListener(this, advisor));
        getLifecycleManager().getCommandManager().registerBrigadierCommand(new ClientInfoBrigadierCommand(this, advisor));
    }

    @Override
    public void disable() {
        ClientInfoAdvisor a = this.advisor;
        this.advisor = null;
        if (a != null) a.shutdown();
    }
}
