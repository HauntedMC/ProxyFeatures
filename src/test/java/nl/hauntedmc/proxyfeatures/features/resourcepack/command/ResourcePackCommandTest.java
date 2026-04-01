package nl.hauntedmc.proxyfeatures.features.resourcepack.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.player.ResourcePackInfo;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.features.resourcepack.ResourcePack;
import nl.hauntedmc.proxyfeatures.framework.localization.LocalizationHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResourcePackCommandTest {

    private ResourcePack feature;
    private ProxyFeatures plugin;
    private ProxyServer proxy;
    private LocalizationHandler localization;
    private LocalizationHandler.MessageBuilder builder;
    private ResourcePackCommand command;

    @BeforeEach
    void setUp() {
        feature = mock(ResourcePack.class);
        plugin = mock(ProxyFeatures.class);
        proxy = mock(ProxyServer.class);
        localization = mock(LocalizationHandler.class);
        builder = mock(LocalizationHandler.MessageBuilder.class);

        when(feature.getPlugin()).thenReturn(plugin);
        when(plugin.getProxy()).thenReturn(proxy);
        when(feature.getLocalizationHandler()).thenReturn(localization);

        when(localization.getMessage(anyString())).thenReturn(builder);
        when(builder.forAudience(any())).thenReturn(builder);
        when(builder.with(anyString(), anyString())).thenReturn(builder);
        when(builder.with(anyString(), any(Number.class))).thenReturn(builder);
        when(builder.with(anyString(), any(Component.class))).thenReturn(builder);
        when(builder.build()).thenReturn(Component.text("ok"));

        command = new ResourcePackCommand(feature);
    }

    @Test
    void metadataAndPermissionNodeAreCorrect() {
        CommandSource source = mock(CommandSource.class);
        when(source.hasPermission("proxyfeatures.feature.resourcepack.command")).thenReturn(true);

        assertEquals("resourcepack", command.getName());
        assertArrayEquals(new String[]{""}, command.getAliases());
        assertTrue(command.hasPermission(invocation(source)));
    }

    @Test
    void executeShowsUsageForUnsupportedSubcommands() {
        CommandSource source = mock(CommandSource.class);

        command.execute(invocation(source));
        command.execute(invocation(source, "unknown"));

        verify(localization, times(2)).getMessage("resourcepack.cmd_usage");
    }

    @Test
    void executeRequiresOtherPermissionForTargetedLookup() {
        CommandSource source = mock(CommandSource.class);
        when(source.hasPermission("proxyfeatures.feature.resourcepack.command.other")).thenReturn(false);

        command.execute(invocation(source, "list", "Remy"));

        verify(localization).getMessage("general.no_permission");
    }

    @Test
    void executeListForSelfRequiresPlayerSource() {
        CommandSource source = mock(CommandSource.class);

        command.execute(invocation(source, "list"));

        verify(localization).getMessage("resourcepack.cmd_notPlayer");
    }

    @Test
    void executeListForMissingTargetShowsNotFound() {
        CommandSource source = mock(CommandSource.class);
        when(source.hasPermission("proxyfeatures.feature.resourcepack.command.other")).thenReturn(true);
        when(proxy.getPlayer("Remy")).thenReturn(Optional.empty());

        command.execute(invocation(source, "list", "Remy"));

        verify(localization).getMessage("resourcepack.cmd_playerNotFound");
        verify(builder).with("player", "Remy");
    }

    @Test
    void executeListForPlayerShowsHeaderAndPackEntries() {
        Player source = mock(Player.class);
        when(source.getUsername()).thenReturn("Remy");

        ResourcePackInfo applied = mock(ResourcePackInfo.class);
        ResourcePackInfo pending = mock(ResourcePackInfo.class);
        when(applied.getUrl()).thenReturn("https://cdn.example.org/packs/applied.zip");
        when(pending.getUrl()).thenReturn("https://cdn.example.org/packs/pending.zip");
        when(source.getAppliedResourcePacks()).thenReturn(List.of(applied));
        when(source.getPendingResourcePacks()).thenReturn(List.of(pending));

        command.execute(invocation(source, "list"));

        verify(localization).getMessage("resourcepack.cmd_header");
        verify(localization, times(2)).getMessage("resourcepack.cmd_entry");
        verify(builder).with("player", "Remy");
        verify(builder).with("pack", "applied");
        verify(builder).with("pack", "pending");
        verify(builder).with("status", "Applied");
        verify(builder).with("status", "Pending");
    }

    @Test
    void suggestAsyncReturnsListSubcommandAndPlayerMatches() {
        CommandSource source = mock(CommandSource.class);
        Player remy = mock(Player.class);
        when(remy.getUsername()).thenReturn("Remy");
        Player alex = mock(Player.class);
        when(alex.getUsername()).thenReturn("Alex");
        when(proxy.getAllPlayers()).thenReturn(List.of(remy, alex));

        assertEquals(List.of("list"), command.suggestAsync(invocation(source, "")).join());
        assertEquals(List.of("Remy"), command.suggestAsync(invocation(source, "list", "re")).join());
        assertEquals(List.of(), command.suggestAsync(invocation(source, "other", "re")).join());
    }

    private static SimpleCommand.Invocation invocation(CommandSource source, String... args) {
        SimpleCommand.Invocation invocation = mock(SimpleCommand.Invocation.class);
        when(invocation.source()).thenReturn(source);
        when(invocation.arguments()).thenReturn(args);
        return invocation;
    }
}
