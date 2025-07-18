package nl.hauntedmc.proxyfeatures.commands;

import com.velocitypowered.api.command.SimpleCommand;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public abstract class FeatureCommand implements SimpleCommand {

    public abstract void execute(final Invocation invocation);
    public abstract boolean hasPermission(final Invocation invocation);
    public abstract CompletableFuture<List<String>> suggestAsync(final Invocation invocation);
    public abstract String getName();
    public abstract String[] getAliases();
}
