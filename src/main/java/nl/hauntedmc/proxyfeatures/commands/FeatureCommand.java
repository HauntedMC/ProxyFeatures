package nl.hauntedmc.proxyfeatures.commands;

import com.velocitypowered.api.command.SimpleCommand;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface FeatureCommand extends SimpleCommand {

    void execute(final Invocation invocation);
    boolean hasPermission(final Invocation invocation);
    CompletableFuture<List<String>> suggestAsync(final Invocation invocation);
    String getName();
    String[] getAliases();
}
