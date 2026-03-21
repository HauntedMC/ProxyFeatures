package nl.hauntedmc.proxyfeatures.features.vanish.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.TabCompleteEvent;
import com.velocitypowered.api.proxy.Player;
import nl.hauntedmc.proxyfeatures.features.vanish.Vanish;

import java.util.List;
import java.util.Set;

public class TabCompleteListener {

    private final Vanish feature;

    public TabCompleteListener(Vanish feature) {
        this.feature = feature;
    }

    @Subscribe
    public void onTabComplete(TabCompleteEvent event) {
        Player player = event.getPlayer();

        // bypass can see vanished suggestions
        if (player.hasPermission("proxyfeatures.feature.vanish.bypass")) {
            return;
        }

        List<String> suggestions = event.getSuggestions();
        if (suggestions.isEmpty()) {
            return;
        }

        Set<String> vanishedNamesLower = VanishTabCompletePolicy.normalizeNamesLower(
                feature.getVanishAPI().getVanishedPlayers().stream()
                        .map(Player::getUsername)
                        .toList()
        );
        if (vanishedNamesLower.isEmpty()) {
            return;
        }

        VanishTabCompletePolicy.removeVanishedSuggestions(suggestions, vanishedNamesLower);
    }
}
