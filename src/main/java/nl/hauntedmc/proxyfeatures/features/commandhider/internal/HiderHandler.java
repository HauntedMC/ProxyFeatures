package nl.hauntedmc.proxyfeatures.features.commandhider.internal;

import nl.hauntedmc.commonlib.util.CastUtils;
import nl.hauntedmc.proxyfeatures.features.commandhider.CommandHider;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HiderHandler {


    private final CommandHider feature;
    private final List<String> hiddenCommands;

    public HiderHandler(CommandHider feature) {
        this.feature = feature;
        this.hiddenCommands = CastUtils.safeCastToList(feature.getConfigHandler().getSetting("command_whitelist"), String.class);
    }

    public Set<String> getHiddenCommands() {
        return new HashSet<>(hiddenCommands);
    }
}
