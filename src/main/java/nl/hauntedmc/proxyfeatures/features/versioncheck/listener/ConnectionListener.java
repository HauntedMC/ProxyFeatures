package nl.hauntedmc.proxyfeatures.features.versioncheck.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import nl.hauntedmc.proxyfeatures.features.versioncheck.VersionCheck;

public class ConnectionListener {

    private final VersionCheck feature;

    public ConnectionListener(VersionCheck feature) {
        this.feature = feature;
    }

    @Subscribe(priority = 10, async = true)
    public void onLogin(LoginEvent event) {
        feature.checkVersion(event);
    }

}
