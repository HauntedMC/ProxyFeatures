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
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;

import com.velocitypowered.api.scheduler.Scheduler;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import nl.hauntedmc.commonlib.featureapi.FeaturePlugin;
import nl.hauntedmc.proxyfeatures.commands.ProxyFeaturesCommand;
import nl.hauntedmc.proxyfeatures.config.MainConfigHandler;
import nl.hauntedmc.proxyfeatures.internal.FeatureLoadManager;
import nl.hauntedmc.proxyfeatures.localization.LocalizationHandler;
import org.spongepowered.configurate.CommentedConfigurationNode;

import java.nio.file.Path;
import java.util.Collection;

@Plugin(id = "proxyfeatures",
        name = "ProxyFeatures",
        version = "1.7.0",
        url = "https://www.hauntedmc.nl",
        description = "ProxyFeatures",
        authors = {"HauntedMC"},
        dependencies = {
                @Dependency(id = "dataregistry"),
                @Dependency(id = "dataprovider")
        })
public class ProxyFeatures implements FeaturePlugin {

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

        CommandMeta proxyfeaturesCommandMeta = commandManager.metaBuilder("proxyfeatures")
                .plugin(this)
                .build();

        commandManager.register(proxyfeaturesCommandMeta, new ProxyFeaturesCommand(this));
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

    public CommentedConfigurationNode getConfig() {
        return mainConfigHandler.getConfig();
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

    /**
     * Hack to get all players when we dont have an instance of getPlugin
     * TODO: should be refactored in the final design
     * @return All players on this proxy
     */
    public static Collection<Player> getAllPlayers() {
        return proxy.getAllPlayers();
    }
}
