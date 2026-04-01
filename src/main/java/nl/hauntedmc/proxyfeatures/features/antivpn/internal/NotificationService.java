package nl.hauntedmc.proxyfeatures.features.antivpn.internal;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.proxyfeatures.features.antivpn.AntiVPN;

import java.time.Duration;
import java.util.Objects;

/**
 * Fix #8:
 * - Rate-limits staff notifications to prevent spam
 * - Localizes per receiver via forAudience(player)
 */
public final class NotificationService {

    private final AntiVPN feature;
    private final ProxyServer proxy;
    private final String permission;
    private final Cache<String, Long> cooldown; // key -> lastSendMillis

    public NotificationService(AntiVPN feature) {
        this.feature = Objects.requireNonNull(feature);
        this.proxy = feature.getPlugin().getProxy();
        this.permission = "proxyfeatures.feature.antivpn.notify";

        long cdSec = feature.getConfigHandler().node("notify").get("cooldown_seconds").as(Long.class, 30L);
        Duration dur = Duration.ofSeconds(Math.max(1, cdSec));

        this.cooldown = Caffeine.newBuilder()
                .expireAfterWrite(dur)
                .maximumSize(20_000)
                .build();
    }

    public void notifyRegionBlocked(String playerName, String countryCode) {
        String key = "region:" + playerName + ":" + (countryCode == null ? "" : countryCode);
        if (!tryMark(key)) return;

        for (Player p : proxy.getAllPlayers()) {
            if (!p.hasPermission(permission)) continue;

            Component msg = feature.getLocalizationHandler()
                    .getMessage("antivpn.notify_region")
                    .with("player", playerName)
                    .with("country", countryCode == null ? "" : countryCode)
                    .forAudience(p)
                    .build();

            p.sendMessage(msg);
        }
    }

    public void notifyRegionUnknownBlocked(String playerName) {
        String key = "region_unknown:" + playerName;
        if (!tryMark(key)) return;

        for (Player p : proxy.getAllPlayers()) {
            if (!p.hasPermission(permission)) continue;

            Component msg = feature.getLocalizationHandler()
                    .getMessage("antivpn.notify_region_unknown")
                    .with("player", playerName)
                    .forAudience(p)
                    .build();

            p.sendMessage(msg);
        }
    }

    public void notifyVpnBlocked(String playerName) {
        String key = "vpn:" + playerName;
        if (!tryMark(key)) return;

        for (Player p : proxy.getAllPlayers()) {
            if (!p.hasPermission(permission)) continue;

            Component msg = feature.getLocalizationHandler()
                    .getMessage("antivpn.notify_vpn")
                    .with("player", playerName)
                    .forAudience(p)
                    .build();

            p.sendMessage(msg);
        }
    }

    private boolean tryMark(String key) {
        Long existing = cooldown.getIfPresent(key);
        if (existing != null) return false;
        cooldown.put(key, System.currentTimeMillis());
        return true;
    }
}
