package nl.hauntedmc.proxyfeatures.features.hlink.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.api.command.FeatureCommand;
import nl.hauntedmc.proxyfeatures.features.hlink.HLink;
import nl.hauntedmc.proxyfeatures.features.hlink.internal.HLinkHandler;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class LinkCommand implements FeatureCommand {

    private final HLink feature;
    private final HLinkHandler handler;

    public LinkCommand(HLink feature) {
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
        UUID playerId = player.getUniqueId();

        handler.addNewKeyAsync(player, 1).whenComplete((result, err) ->
                feature.getLifecycleManager().getTaskManager().scheduleTask(() -> {
                    Optional<Player> liveOpt = ProxyFeatures.getProxyInstance().getPlayer(playerId);
                    if (liveOpt.isEmpty()) {
                        return;
                    }
                    Player live = liveOpt.get();

                    HLinkResultPolicy.Decision decision = HLinkResultPolicy.evaluate(result, err);
                    if (decision.outcome() == HLinkResultPolicy.Outcome.ERROR) {
                        live.sendMessage(feature.getLocalizationHandler().getMessage("hlink.errorCreatingKey").forAudience(live).build());
                        return;
                    }
                    if (decision.outcome() == HLinkResultPolicy.Outcome.ALREADY_REGISTERED) {
                        live.sendMessage(feature.getLocalizationHandler().getMessage("hlink.errorAlreadyLinked").forAudience(live).build());
                        return;
                    }

                    String token = decision.token();
                    String link = handler.getLink(token);

                    live.sendMessage(feature.getLocalizationHandler().getMessage("hlink.header").forAudience(live).build());
                    Component clickable = feature.getLocalizationHandler().getMessage("hlink.clickLink").forAudience(live).build()
                            .clickEvent(ClickEvent.openUrl(link));
                    live.sendMessage(clickable);
                    live.sendMessage(feature.getLocalizationHandler().getMessage("hlink.footer").forAudience(live).build());
                })
        );
    }


    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("proxyfeatures.feature.hlink.command.link");
    }


    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        return CompletableFuture.completedFuture(List.of());
    }


    public String getName() {
        return "link";
    }
}
