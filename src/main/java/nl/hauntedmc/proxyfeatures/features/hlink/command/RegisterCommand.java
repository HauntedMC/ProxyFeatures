package nl.hauntedmc.proxyfeatures.features.hlink.command;

import nl.hauntedmc.proxyfeatures.commands.FeatureCommand;
import nl.hauntedmc.proxyfeatures.features.hlink.HLink;
import nl.hauntedmc.proxyfeatures.features.hlink.internal.HLinkHandler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class RegisterCommand implements FeatureCommand {

    private final HLink feature;
    private final HLinkHandler handler;

    public RegisterCommand(HLink feature) {
        this.feature = feature;
        this.handler = feature.getHLinkHandler();
    }

    public String[] getAliases() {
        return new String[]{""};
    }

    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        if (!(source instanceof Player player)) {
            source.sendMessage(feature.getLocalizationHandler().getMessage("general.player_command").forAudience(source).build());
            return;
        }
        String token = handler.addNewKey(player, 2);

        if (token == null) {
            return;
        }

        String link = handler.getLink(token);

        source.sendMessage(feature.getLocalizationHandler().getMessage("hlink.header").forAudience(player).build());
        Component clickable = feature.getLocalizationHandler().getMessage("hlink.clickLink").forAudience(player).build()
                .clickEvent(ClickEvent.openUrl(link));
        source.sendMessage(clickable);
        source.sendMessage(feature.getLocalizationHandler().getMessage("hlink.footer").forAudience(player).build());
    }

    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("proxyfeatures.feature.hlink.command.register");
    }

    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        return CompletableFuture.completedFuture(List.of());
    }

    public String getName() {
        return "register";
    }
}
