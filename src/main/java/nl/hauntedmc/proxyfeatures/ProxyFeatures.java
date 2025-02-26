package nl.hauntedmc.proxyfeatures;

import com.github.retrooper.packetevents.PacketEvents;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyReloadEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;

import nl.hauntedmc.proxyfeatures.commands.ProxyFeaturesCommand;
import nl.hauntedmc.proxyfeatures.config.ConfigHandler;
import nl.hauntedmc.proxyfeatures.lifecycle.FeatureLoadManager;
import nl.hauntedmc.proxyfeatures.localization.LocalizationHandler;
import org.slf4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;

import java.nio.file.Path;
import java.util.Objects;

@Plugin(id = "proxyfeatures",
        name = "ProxyFeatures",
        version = "1.0.0",
        url = "https://www.hauntedmc.nl",
        description = "ProxyFeatures",
        authors = {"HauntedMC"})
public class ProxyFeatures {

    private ConfigHandler configHandler;
    private FeatureLoadManager featureLoadManager;
    private LocalizationHandler localizationHandler;

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    @Inject
    private Injector injector;

    @Inject
    public ProxyFeatures(ProxyServer proxy,
                     Logger logger,
                     @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        logger.info("ProxyFeatures is loading...");
    }

    /**
     * SYNC
     * <p>
     * This event is fired by the proxy after plugins have been
     * loaded but before the proxy starts accepting connections.
     */
    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        PacketEvents.getAPI().init();

        // General plugin initialization
        configHandler = new ConfigHandler(this);
        localizationHandler = new LocalizationHandler(this);
        featureLoadManager = new FeatureLoadManager(this);
        registerBaseCommand();
        registerCommonListeners();

        // Feature specific initialization
        featureLoadManager.initializeFeatures();
    }


    /**
     * This event is fired when the proxy is
     * reloaded by the user using /velocity reload.
     */
    @Subscribe
    public void onProxyReload(final ProxyReloadEvent event) {
    }

    /**
     * SYNC
     * <p>
     * This event is fired by the proxy after the proxy has stopped
     * accepting connections but before the proxy process exits
     */
    @Subscribe
    public void onProxyShutdown(final ProxyShutdownEvent event) {
        featureLoadManager.unloadAllFeatures();
        getLogger().info("proxyfeatures is shutting down...");
    }


    public Logger getLogger() {
        return logger;
    }

    private void registerBaseCommand() {
        CommandManager commandManager = proxy.getCommandManager();

        CommandMeta proxyfeaturesCommandMeta = commandManager.metaBuilder("proxyfeatures")
                .plugin(this)
                .build();

        commandManager.register(proxyfeaturesCommandMeta, new ProxyFeaturesCommand(this));
    }

    private void registerCommonListeners() {
        EventManager eventManager = proxy.getEventManager();
        //eventManager.register(this, new SomeListener(this));
    }

    public FeatureLoadManager getFeatureLoadManager() {
        return featureLoadManager;
    }

    public ConfigHandler getConfigHandler() {
        return configHandler;
    }

    public LocalizationHandler getLocalizationHandler() {
        return localizationHandler;
    }

    public Path getDataDirectory() {
        return dataDirectory;
    }

    public CommentedConfigurationNode getConfig() {
        return configHandler.getConfig();
    }
}
