package nl.hauntedmc.proxyfeatures.features.hlink.internal;

import com.google.gson.Gson;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.track.Track;
import net.luckperms.api.track.TrackManager;
import nl.hauntedmc.proxyfeatures.api.util.http.SimpleHttpClient;
import nl.hauntedmc.proxyfeatures.features.hlink.HLink;
import nl.hauntedmc.proxyfeatures.features.hlink.internal.api.AccountRequest;
import nl.hauntedmc.proxyfeatures.features.hlink.internal.api.LinkRequest;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class HLinkHandler {

    public enum LinkResultType { SUCCESS, ALREADY_REGISTERED, ERROR }
    public record LinkResult(LinkResultType type, String token) {}

    private final HLink feature;
    private final Gson gson = new Gson();
    private final String apiKey;
    private final boolean friendly;
    private final String websiteUrl;
    private final ExecutorService httpExecutor;

    // Cache for updatePlayerData: key = player UUID, value = CachedPlayerData (username & primary group)
    private final Map<UUID, CachedPlayerData> updateCache = new ConcurrentHashMap<>();

    public HLinkHandler(HLink feature) {
        this.feature = feature;
        this.apiKey = feature.getConfigHandler().get("api-key", String.class, "").trim();
        this.friendly = feature.getConfigHandler().get("full-friendly-urls-enabled", Boolean.class, true);
        String configuredWebsiteUrl = feature.getConfigHandler().get("website-url", String.class, "");
        this.websiteUrl = normalizeBaseUrl(configuredWebsiteUrl);
        if (configuredWebsiteUrl != null && !configuredWebsiteUrl.isBlank() && this.websiteUrl.isBlank()) {
            feature.getLogger().warn("HLink website-url is invalid; HTTP requests are disabled until it is corrected.");
        }

        this.httpExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "hlink-http");
            t.setDaemon(true);
            return t;
        });
    }

    private String buildApiUrl() {
        return friendly ? websiteUrl + "/msapi" : websiteUrl + "/index.php?msapi";
    }

    private static String normalizeBaseUrl(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String trimmed = raw.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.isBlank()) return "";

        try {
            URI uri = URI.create(trimmed);
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                return "";
            }
            return trimmed;
        } catch (IllegalArgumentException ex) {
            return "";
        }
    }

    /**
     * Update the player's data on the API.
     * Uses a cache to check if the username and primary group have changed.
     * If nothing has changed, no API call is made.
     */
    public void updatePlayerData(Player player) {
        if (player == null) return;
        updatePlayerDataAsync(player);
    }

    public CompletableFuture<Boolean> updatePlayerDataAsync(Player player) {
        if (player == null) return CompletableFuture.completedFuture(false);
        UUID uuid = player.getUniqueId();
        String username = player.getUsername();
        return CompletableFuture.supplyAsync(() -> updatePlayerData(uuid, username), httpExecutor);
    }

    private boolean updatePlayerData(UUID uuid, String username) {
        if (!isConfigured()) return false;
        String primaryGroup = getPlayerGroups(uuid, username);

        CachedPlayerData cached = updateCache.get(uuid);
        if (cached != null && cached.username.equals(username) && cached.primaryGroup.equals(primaryGroup)) {
            return true;
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
            return true;
        } catch (Exception e) {
            feature.getLogger().error(Component.text("Error updating player data for " + username));
            return false;
        }
    }

    /**
     * Retrieves the player's primary group using LuckPerms.
     * If an error occurs or the user is not found, "default" is returned.
     */
    private String getPlayerGroups(UUID uuid, String username) {
        try {
            LuckPerms lp = LuckPermsProvider.get();
            User user = lp.getUserManager().getUser(uuid);
            if (user == null) {
                return "default";
            }

            // Gather *direct* inheritance nodes, EXCLUDING any with a server context
            Set<String> directGroups = user.getNodes().stream()
                    .filter(NodeType.INHERITANCE::matches)
                    .map(NodeType.INHERITANCE::cast)
                    .filter(n -> n.getContexts().getValues("server").isEmpty())
                    .map(InheritanceNode::getGroupName)
                    .collect(Collectors.toSet());

            if (directGroups.isEmpty()) {
                return "default";
            }

            // Iterate each track and pick the *highest* direct group present
            List<String> highestPerTrack = getTracks(lp, directGroups);

            return String.join(",", highestPerTrack);

        } catch (RuntimeException e) {
            feature.getLogger().error(Component.text("Error retrieving LuckPerms groups for "
                    + username + ": " + e.getMessage()));
            return "default";
        }
    }

    private static @NotNull List<String> getTracks(LuckPerms lp, Set<String> directGroups) {
        TrackManager tm = lp.getTrackManager();
        List<String> highestPerTrack = new ArrayList<>();

        for (Track track : tm.getLoadedTracks()) {
            List<String> ladder = track.getGroups();
            for (int i = ladder.size() - 1; i >= 0; i--) {
                String grp = ladder.get(i);
                if (directGroups.contains(grp)) {
                    highestPerTrack.add(grp);
                    break;
                }
            }
        }
        return highestPerTrack;
    }

    public String doesKeyExist(String uuid, int keyType) {
        if (!isConfigured()) return "false";
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
            feature.getLogger().error(Component.text("Error checking if key exists for " + uuid));
        }
        return "false";
    }

    public boolean alreadyRegistered(String uuid) {
        if (!isConfigured()) return false;
        String apiUrl = buildApiUrl();
        List<NameValuePair> args = new ArrayList<>();
        args.add(new BasicNameValuePair("api_key", apiKey));
        args.add(new BasicNameValuePair("uuid", uuid));
        try {
            String response = SimpleHttpClient.post(apiUrl + "/checkUserAccountExists", args);
            AccountRequest request = gson.fromJson(response, AccountRequest.class);
            return request != null && request.getExists();
        } catch (Exception e) {
            feature.getLogger().error(Component.text("Error checking registration for " + uuid));

        }
        return false;
    }

    /**
     * Creates or retrieves a link key for the given player asynchronously.
     */
    public CompletableFuture<LinkResult> addNewKeyAsync(Player player, int keyType) {
        if (player == null) {
            return CompletableFuture.completedFuture(new LinkResult(LinkResultType.ERROR, null));
        }
        UUID uuid = player.getUniqueId();
        String username = player.getUsername();
        return CompletableFuture.supplyAsync(() -> addNewKey(uuid, username, keyType), httpExecutor);
    }

    private LinkResult addNewKey(UUID playerId, String username, int keyType) {
        if (!isConfigured()) {
            return new LinkResult(LinkResultType.ERROR, null);
        }
        String uuid = playerId.toString();
        if (alreadyRegistered(uuid)) {
            return new LinkResult(LinkResultType.ALREADY_REGISTERED, null);
        }

        String token = doesKeyExist(uuid, keyType);
        if (!token.equalsIgnoreCase("false")) {
            return new LinkResult(LinkResultType.SUCCESS, token);
        }
        token = UUID.randomUUID().toString();
        String groups = getPlayerGroups(playerId, username);
        String apiUrl = buildApiUrl();

        List<NameValuePair> argsCreate = new ArrayList<>();
        argsCreate.add(new BasicNameValuePair("api_key", apiKey));
        argsCreate.add(new BasicNameValuePair("token", token));
        argsCreate.add(new BasicNameValuePair("uuid", uuid));
        argsCreate.add(new BasicNameValuePair("mc_username", username));
        argsCreate.add(new BasicNameValuePair("valid", "1"));
        argsCreate.add(new BasicNameValuePair("key_type", String.valueOf(keyType)));

        List<NameValuePair> argsUpdate = new ArrayList<>();
        argsUpdate.add(new BasicNameValuePair("api_key", apiKey));
        argsUpdate.add(new BasicNameValuePair("uuid", uuid));
        argsUpdate.add(new BasicNameValuePair("username", username));
        argsUpdate.add(new BasicNameValuePair("groups", groups));

        try {
            SimpleHttpClient.post(apiUrl + "/createLinkKey", argsCreate);
            SimpleHttpClient.post(apiUrl + "/updatePlayerCache", argsUpdate);
        } catch (Exception e) {
            feature.getLogger().error(Component.text("Error adding new key for " + username));
            return new LinkResult(LinkResultType.ERROR, null);
        }
        return new LinkResult(LinkResultType.SUCCESS, token);
    }

    public String getLink(String token) {
        return friendly
                ? websiteUrl + "/link-minecraft/?token=" + token + "&type=link"
                : websiteUrl + "/index.php?link-minecraft/&token=" + token + "&type=link";
    }

    public void shutdown() {
        httpExecutor.shutdownNow();
        updateCache.clear();
    }

    private boolean isConfigured() {
        return !apiKey.isBlank() && !websiteUrl.isBlank();
    }

    // Simple POJO to cache player's username and primary group.
    private record CachedPlayerData(String username, String primaryGroup) {
    }
}
