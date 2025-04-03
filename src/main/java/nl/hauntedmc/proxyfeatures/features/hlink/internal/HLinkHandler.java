package nl.hauntedmc.proxyfeatures.features.hlink.internal;

import com.google.gson.Gson;
import com.velocitypowered.api.proxy.Player;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import nl.hauntedmc.proxyfeatures.features.hlink.HLink;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.features.hlink.internal.api.AccountRequest;
import nl.hauntedmc.proxyfeatures.features.hlink.internal.api.LinkRequest;
import nl.hauntedmc.proxyfeatures.features.hlink.internal.util.SimpleHttpClient;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.message.BasicNameValuePair;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class HLinkHandler {

    private final HLink feature;
    private final ProxyFeatures plugin;
    private final Gson gson = new Gson();
    private final String apiKey;
    private final boolean friendly;
    private final String websiteUrl;

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

    public void updatePlayerData(Player player) {
        String apiUrl = buildApiUrl();
        String groups = getPlayerGroups(player);
        List<NameValuePair> args = new ArrayList<>();
        args.add(new BasicNameValuePair("api_key", apiKey));
        args.add(new BasicNameValuePair("uuid", player.getUniqueId().toString()));
        args.add(new BasicNameValuePair("username", player.getUsername()));
        args.add(new BasicNameValuePair("groups", groups));
        try {
            feature.getPlugin().getLogger().info("Updating: {}", groups);
            SimpleHttpClient.post(apiUrl + "/updatePlayerCache", args);
        } catch (Exception e) {
            plugin.getLogger().error("Error updating player data for {}", player.getUsername(), e);
        }
    }

    private String getPlayerGroups(Player player) {
        try {
            LuckPerms luckPerms = LuckPermsProvider.get();
            User user = luckPerms.getUserManager().getUser(player.getUniqueId());
            if (user != null) {
            return user.getPrimaryGroup();
            }
        } catch (Exception e) {
            plugin.getLogger().error("Error retrieving LuckPerms groups for {}", player.getUsername(), e);
        }
        return "default";
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

    public String addNewKey(Player player, int keyType) {
        String uuid = player.getUniqueId().toString();

        if (alreadyRegistered(uuid)) {
            if (keyType == 1) {
                player.sendMessage(feature.getLocalizationHandler().getMessage("hlink.errorAlreadyLinked", player));
            } else {
                player.sendMessage(feature.getLocalizationHandler().getMessage("hlink.errorAlreadyRegistered", player));
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
            player.sendMessage(feature.getLocalizationHandler().getMessage("hlink.errorCreatingKey", player));
            return "errorCreatingKey";
        }
        return token;
    }

    public String getLink(String token) {
        return friendly
                ? websiteUrl + "/link-minecraft/?token=" + token + "&type=link"
                : websiteUrl + "/index.php?link-minecraft/&token=" + token + "&type=link";
    }
}
