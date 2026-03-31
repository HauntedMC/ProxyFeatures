package nl.hauntedmc.proxyfeatures;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyReloadEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.PluginManager;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.Scheduler;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import nl.hauntedmc.proxyfeatures.framework.command.ProxyFeaturesCommand;
import nl.hauntedmc.proxyfeatures.framework.config.MainConfigHandler;
import nl.hauntedmc.proxyfeatures.framework.loader.FeatureLoadManager;
import nl.hauntedmc.proxyfeatures.framework.localization.LocalizationHandler;

import java.nio.file.Path;

@Plugin(id = "proxyfeatures",
        name = "ProxyFeatures",
        version = "2.3.0",
        url = "https://www.hauntedmc.nl",
        description = "ProxyFeatures",
        authors = {"HauntedMC"},
        dependencies = {
                @Dependency(id = "dataregistry", optional = true),
                @Dependency(id = "dataprovider", optional = true)
        })
public class ProxyFeatures {

    private MainConfigHandler mainConfigHandler;
    private FeatureLoadManager featureLoadManager;
    private LocalizationHandler localizationHandler;

    private static ProxyServer proxy = null;
    private final ComponentLogger logger;
    private final Path dataDirectory;
    @Inject
    private Injector injector;

    @Inject
    public ProxyFeatures(ProxyServer proxy,
                         ComponentLogger logger,
                         @DataDirectory Path dataDirectory) {
        ProxyFeatures.proxy = proxy;
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
        // General plugin initialization
        mainConfigHandler = new MainConfigHandler(this);
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

    public ComponentLogger getLogger() {
        return logger;
    }

    private void registerBaseCommand() {
        CommandManager commandManager = proxy.getCommandManager();

        // Build Brigadier tree and register via Velocity's BrigadierCommand wrapper
        ProxyFeaturesCommand root = new ProxyFeaturesCommand(this);
        com.velocitypowered.api.command.BrigadierCommand brigadier =
                new com.velocitypowered.api.command.BrigadierCommand(root.buildTree());

        CommandMeta meta = commandManager.metaBuilder(brigadier)
                .plugin(this)
                .build();

        commandManager.register(meta, brigadier);
    }

    private void registerCommonListeners() {
    }

    public FeatureLoadManager getFeatureLoadManager() {
        return featureLoadManager;
    }

    public MainConfigHandler getConfigHandler() {
        return mainConfigHandler;
    }

    public LocalizationHandler getLocalizationHandler() {
        return localizationHandler;
    }

    public Path getDataDirectory() {
        return dataDirectory;
    }

    public PluginManager getPluginManager() {
        return proxy.getPluginManager();
    }

    public EventManager getEventManager() {
        return proxy.getEventManager();
    }

    public CommandManager getCommandManager() {
        return proxy.getCommandManager();
    }

    public Scheduler getScheduler() {
        return proxy.getScheduler();
    }

    public ProxyServer getProxy() {
        return proxy;
    }

    public static ProxyServer getProxyInstance() {
        return proxy;
    }


}
