package nl.hauntedmc.proxyfeatures.features.hlink.internal;

import com.google.gson.Gson;
import com.velocitypowered.api.proxy.Player;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.track.Track;
import net.luckperms.api.track.TrackManager;
import nl.hauntedmc.proxyfeatures.features.hlink.HLink;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.features.hlink.internal.api.AccountRequest;
import nl.hauntedmc.proxyfeatures.features.hlink.internal.api.LinkRequest;
import nl.hauntedmc.proxyfeatures.common.http.SimpleHttpClient;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.message.BasicNameValuePair;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class HLinkHandler {

    private final HLink feature;
    private final ProxyFeatures plugin;
    private final Gson gson = new Gson();
    private final String apiKey;
    private final boolean friendly;
    private final String websiteUrl;

    // Cache for updatePlayerData: key = player UUID, value = CachedPlayerData (username & primary group)
    private final Map<UUID, CachedPlayerData> updateCache = new ConcurrentHashMap<>();

    public HLinkHandler(HLink feature) {
        this.feature = feature;
        this.plugin = feature.getPlugin();
        this.apiKey = (String) feature.getConfigHandler().getSetting("api-key");
        this.friendly = (boolean) feature.getConfigHandler().getSetting("full-friendly-urls-enabled");
        this.websiteUrl = (String) feature.getConfigHandler().getSetting("website-url");
    }

    private String buildApiUrl() {
        return friendly ? websiteUrl + "/msapi" : websiteUrl + "/index.php?msapi";
    }

    /**
     * Update the player's data on the API.
     * Uses a cache to check if the username and primary group have changed.
     * If nothing has changed, no API call is made.
     */
    public void updatePlayerData(Player player) {
        UUID uuid = player.getUniqueId();
        String username = player.getUsername();
        String primaryGroup = getPlayerGroups(player);

        CachedPlayerData cached = updateCache.get(uuid);
        if (cached != null && cached.username.equals(username) && cached.primaryGroup.equals(primaryGroup)) {
            return;
        }

        String apiUrl = buildApiUrl();
        List<NameValuePair> args = new ArrayList<>();
        args.add(new BasicNameValuePair("api_key", apiKey));
        args.add(new BasicNameValuePair("uuid", uuid.toString()));
        args.add(new BasicNameValuePair("username", username));
        args.add(new BasicNameValuePair("groups", primaryGroup));

        try {
            SimpleHttpClient.post(apiUrl + "/updatePlayerCache", args);
            updateCache.put(uuid, new CachedPlayerData(username, primaryGroup));
        } catch (Exception e) {
            plugin.getLogger().error("Error updating player data for {}", player.getUsername(), e);
        }
    }

    /**
     * Retrieves the player's primary group using LuckPerms.
     * If an error occurs or the user is not found, "default" is returned.
     */
    private String getPlayerGroups(Player player) {
        try {
            LuckPerms lp = LuckPermsProvider.get();
            User user = lp.getUserManager().getUser(player.getUniqueId());
            if (user == null) {
                return "default";
            }

            // Gather *direct* inheritance nodes, EXCLUDING any with a server context
            Set<String> directGroups = user.getNodes().stream()
                    .filter(NodeType.INHERITANCE::matches)                       // true for InheritanceNode :contentReference[oaicite:0]{index=0}
                    .map(NodeType.INHERITANCE::cast)
                    .filter(n -> n.getContexts().getValues("server").isEmpty())   // only keep global nodes :contentReference[oaicite:1]{index=1}
                    .map(InheritanceNode::getGroupName)
                    .collect(Collectors.toSet());

            if (directGroups.isEmpty()) {
                return "default";
            }

            // Iterate each track and pick the *highest* direct group present
            TrackManager tm = lp.getTrackManager();
            List<String> highestPerTrack = new ArrayList<>();

            for (Track track : tm.getLoadedTracks()) {                       // all loaded ladders :contentReference[oaicite:2]{index=2}
                List<String> ladder = track.getGroups();                     // ordered low→high :contentReference[oaicite:3]{index=3}
                for (int i = ladder.size() - 1; i >= 0; i--) {
                    String grp = ladder.get(i);
                    if (directGroups.contains(grp)) {
                        highestPerTrack.add(grp);
                        break;
                    }
                }
            }

            return String.join(",", highestPerTrack);

        } catch (Exception e) {
            plugin.getLogger().error("Error retrieving LuckPerms groups for {}", player.getUsername(), e);
            return "default";
        }
    }

    public String doesKeyExist(String uuid, int keyType) {
        String apiUrl = buildApiUrl();
        List<NameValuePair> args = new ArrayList<>();
        args.add(new BasicNameValuePair("api_key", apiKey));
        args.add(new BasicNameValuePair("uuid", uuid));
        args.add(new BasicNameValuePair("key_type", String.valueOf(keyType)));
        try {
            String response = SimpleHttpClient.post(apiUrl + "/checkForExistingLink", args);
            LinkRequest request = gson.fromJson(response, LinkRequest.class);
            if (request != null && request.getResults() != null && !request.getResults().isEmpty()) {
                return request.getResults();
            }
        } catch (Exception e) {
            plugin.getLogger().error("Error checking if key exists for {}", uuid, e);
        }
        return "false";
    }

    public boolean alreadyRegistered(String uuid) {
        String apiUrl = buildApiUrl();
        List<NameValuePair> args = new ArrayList<>();
        args.add(new BasicNameValuePair("api_key", apiKey));
        args.add(new BasicNameValuePair("uuid", uuid));
        try {
            String response = SimpleHttpClient.post(apiUrl + "/checkUserAccountExists", args);
            AccountRequest request = gson.fromJson(response, AccountRequest.class);
            return request != null && request.getExists();
        } catch (Exception e) {
            plugin.getLogger().error("Error checking registration for {}", uuid, e);
        }
        return false;
    }

    /**
     * Creates a new link key for the given player.
     * If the player is already registered, sends an error message and returns null.
     */
    public String addNewKey(Player player, int keyType) {
        String uuid = player.getUniqueId().toString();

        if (alreadyRegistered(uuid)) {
            if (keyType == 1) {
                player.sendMessage(feature.getLocalizationHandler().getMessage("hlink.errorAlreadyLinked").forAudience(player).build());
            } else {
                player.sendMessage(feature.getLocalizationHandler().getMessage("hlink.errorAlreadyRegistered").forAudience(player).build());
            }
            return null;
        }

        String token = doesKeyExist(uuid, keyType);
        if (!token.equalsIgnoreCase("false")) {
            return token;
        }
        token = UUID.randomUUID().toString();
        String groups = getPlayerGroups(player);
        String apiUrl = buildApiUrl();

        List<NameValuePair> argsCreate = new ArrayList<>();
        argsCreate.add(new BasicNameValuePair("api_key", apiKey));
        argsCreate.add(new BasicNameValuePair("token", token));
        argsCreate.add(new BasicNameValuePair("uuid", uuid));
        argsCreate.add(new BasicNameValuePair("mc_username", player.getUsername()));
        argsCreate.add(new BasicNameValuePair("valid", "1"));
        argsCreate.add(new BasicNameValuePair("key_type", String.valueOf(keyType)));

        List<NameValuePair> argsUpdate = new ArrayList<>();
        argsUpdate.add(new BasicNameValuePair("api_key", apiKey));
        argsUpdate.add(new BasicNameValuePair("uuid", uuid));
        argsUpdate.add(new BasicNameValuePair("username", player.getUsername()));
        argsUpdate.add(new BasicNameValuePair("groups", groups));

        try {
            SimpleHttpClient.post(apiUrl + "/createLinkKey", argsCreate);
            SimpleHttpClient.post(apiUrl + "/updatePlayerCache", argsUpdate);
        } catch (Exception e) {
            plugin.getLogger().error("Error adding new key for {}", player.getUsername(), e);
            player.sendMessage(feature.getLocalizationHandler().getMessage("hlink.errorCreatingKey").forAudience(player).build());
            return "errorCreatingKey";
        }
        return token;
    }

    public String getLink(String token) {
        return friendly
                ? websiteUrl + "/link-minecraft/?token=" + token + "&type=link"
                : websiteUrl + "/index.php?link-minecraft/&token=" + token + "&type=link";
    }

    // Simple POJO to cache player's username and primary group.
    private record CachedPlayerData(String username, String primaryGroup){}
}
