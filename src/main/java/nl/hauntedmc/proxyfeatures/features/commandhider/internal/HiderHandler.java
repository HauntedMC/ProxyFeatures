package nl.hauntedmc.proxyfeatures.features.commandhider.internal;

import nl.hauntedmc.proxyfeatures.api.util.type.CastUtils;
import nl.hauntedmc.proxyfeatures.features.commandhider.CommandHider;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HiderHandler {


    private final List<String> hiddenCommands;

    public HiderHandler(CommandHider feature) {
        this.hiddenCommands = CastUtils.safeCastToList(feature.getConfigHandler().getSetting("hidden-commands"), String.class);
    }

    public Set<String> getHiddenCommands() {
        return new HashSet<>(hiddenCommands);
    }
}
