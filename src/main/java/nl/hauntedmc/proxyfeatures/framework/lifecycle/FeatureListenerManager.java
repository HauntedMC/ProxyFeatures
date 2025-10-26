package nl.hauntedmc.proxyfeatures.framework.lifecycle;

import nl.hauntedmc.proxyfeatures.ProxyFeatures;

import java.util.ArrayList;
import java.util.List;

public class FeatureListenerManager {

    private final ProxyFeatures plugin;
    private final List<Object> registeredListeners = new ArrayList<>();

    public FeatureListenerManager(ProxyFeatures plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers an event listener and tracks it for later removal.
     */
    public void registerListener(Object listener) {
        plugin.getEventManager().register(plugin, listener);
        registeredListeners.add(listener);
    }

    /**
     * Unregisters all event listeners that have been registered.
     */
    public void unregisterAllListeners() {
        for (Object listener : registeredListeners) {
            plugin.getEventManager().unregisterListener(plugin, listener);
        }
        registeredListeners.clear();
    }

    /**
     * Returns the number of registered listeners.
     */
    public int getRegisteredListenerCount() {
        return registeredListeners.size();
    }
}
