package nl.hauntedmc.proxyfeatures.features.motd.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.proxy.server.ServerPing;
import nl.hauntedmc.proxyfeatures.features.motd.Motd;

public class PingListener {

    private final Motd feature;

    public PingListener(Motd feature) {
        this.feature = feature;
    }

    @Subscribe(priority = 10)
    public void onProxyPing(ProxyPingEvent event) {
        ServerPing serverPing = feature.getMotdHandler().modifyServerPing(event.getPing());
        event.setPing(serverPing);
    }

}
