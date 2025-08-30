package nl.hauntedmc.proxyfeatures.features.motd.internal;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.velocitypowered.api.proxy.server.ServerPing;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.commonlib.util.CastUtils;
import nl.hauntedmc.commonlib.util.ComponentUtils;
import nl.hauntedmc.proxyfeatures.common.util.APIRegistry;
import nl.hauntedmc.proxyfeatures.features.motd.Motd;
import nl.hauntedmc.proxyfeatures.features.vanish.internal.VanishAPI;
import nl.hauntedmc.proxyfeatures.features.versioncheck.VersionCheck;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class MotdHandler {
    private final Motd feature;
    private final Cache<String, Component> motdCache;

    public MotdHandler(Motd feature) {
        this.feature = feature;
        this.motdCache = Caffeine.newBuilder()
                .expireAfterWrite(2, TimeUnit.SECONDS)
                .build();
    }

    public ServerPing modifyServerPing(ServerPing unmodifiedPing) {
        return createNewServerPing(unmodifiedPing);
    }

    private ServerPing createNewServerPing(ServerPing unmodifiedPing) {
        Component motd = getMOTD();

        ServerPing.Players players = getAdjustedPlayers(unmodifiedPing);

        ServerPing.Version version = unmodifiedPing.getVersion();

        if (feature.getPlugin().getFeatureLoadManager().getFeatureRegistry().isFeatureLoaded("VersionCheck")) {
            VersionCheck versionCheck = (VersionCheck) feature.getPlugin().getFeatureLoadManager().getFeatureRegistry().getLoadedFeature("VersionCheck");
            if (versionCheck.getVersionHandler().isAllowedVersion(unmodifiedPing.getVersion().getProtocol())) {
                int minProtocol = versionCheck.getVersionHandler().getMinimumProtcolVersion();
                String friendlyName = versionCheck.getVersionHandler().getFriendlyProtocolName() + "+";
                version = new ServerPing.Version(minProtocol, friendlyName);
            }
        }

        return new ServerPing(version,
                players,
                motd,
                unmodifiedPing.getFavicon().orElse(null));
    }

    private ServerPing.@NotNull Players getAdjustedPlayers(ServerPing unmodifiedPing) {
        double playerCountAdjustment = (double) feature.getConfigHandler().getSetting("playerCountMultiplier");

        if (playerCountAdjustment < 0) {
            playerCountAdjustment = 0;
        }
        int onlinePlayers = unmodifiedPing.getPlayers().map(ServerPing.Players::getOnline).orElse(0);
        int vanishedPlayers = APIRegistry.get(VanishAPI.class)
                .map(VanishAPI::getVanishedCount)
                .orElse(0);
        int vanishedAdjustedPlayers = onlinePlayers - vanishedPlayers;
        int playerCount = (int) (vanishedAdjustedPlayers * playerCountAdjustment);
        return new ServerPing.Players(playerCount, unmodifiedPing.getPlayers().get().getMax(), unmodifiedPing.getPlayers().get().getSample());
    }

    private Component getMOTD() {
        Component motd = motdCache.getIfPresent("motd");

        if (motd == null) {
            motd = readMOTDFromFile();
            motdCache.put("motd", motd);
        }
        return motd;
    }

    public void invalidateCache() {
        motdCache.invalidate("motd");
    }

    private Component readMOTDFromFile() {
        String line1 = (String) feature.getConfigHandler().getSetting("motdline1");
        List<String> words = CastUtils.safeCastToList(feature.getConfigHandler().getSetting("motdline2"), String.class);

        int size = words.size();
        int index1 = ThreadLocalRandom.current().nextInt(size);
        int index2 = ThreadLocalRandom.current().nextInt(size - 1);
        if (index2 >= index1) {
            index2++;
        }

        String line2 = getLine2(words, index1, index2);
        String completeMotd = line1 + "\n" + line2;

        return  ComponentUtils.deserializeMMComponent(completeMotd);
    }

    private static @NotNull String getLine2(List<String> words, int index1, int index2) {
        String randomWords = words.get(index1) + ". " + words.get(index2) + ". ";
        String content = randomWords + "HauntedMC.";

        int targetWidth = 58;
        if (content.length() < targetWidth) {
            int totalPadding = targetWidth - content.length();
            int leftPadding = totalPadding / 2;
            int rightPadding = totalPadding - leftPadding;
            content = " ".repeat(leftPadding) + content + " ".repeat(rightPadding);
        }

        return "<gradient:#BFBFBF:#FFFFFF>" + content;
    }
}
