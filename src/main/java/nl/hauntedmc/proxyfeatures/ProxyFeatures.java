package nl.hauntedmc.proxyfeatures;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyReloadEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;

import org.slf4j.Logger;

import java.nio.file.Path;

@Plugin(id = "proxyfeatures",
        name = "ProxyFeatures",
        version = "1.0.0",
        url = "https://www.hauntedmc.nl",
        description = "ProxyFeatures",
        authors = {"HauntedMC"})
public class ProxyFeatures {
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
    }


    /**
     * This event is fired when the proxy is
     * reloaded by the user using /velocity reload.
     */
    @Subscribe
    public void onProxyReload(final ProxyReloadEvent event) {
        logger.info("ProxyFeatures is reloaded.");
    }

    /**
     * SYNC
     * <p>
     * This event is fired by the proxy after the proxy has stopped
     * accepting connections but before the proxy process exits
     */
    @Subscribe
    public void onProxyShutdown(final ProxyShutdownEvent event) {
        logger.info("ProxyFeatures is stopped.");
    }

}
