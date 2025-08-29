package nl.hauntedmc.proxyfeatures.features.vanish.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.TabCompleteEvent;
import com.velocitypowered.api.proxy.Player;
import nl.hauntedmc.proxyfeatures.features.vanish.Vanish;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

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

        Set<String> vanishedNamesLower = feature.getVanishAPI().getVanishedPlayers().stream()
                .map(p -> p.getUsername().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        if (vanishedNamesLower.isEmpty()) {
            return;
        }

        suggestions.removeIf(s -> {
            String ls = s.toLowerCase(Locale.ROOT);
            return vanishedNamesLower.contains(ls);
        });
    }
}
