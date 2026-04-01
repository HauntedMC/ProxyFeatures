package nl.hauntedmc.proxyfeatures.features.slashserver.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.proxyfeatures.features.slashserver.SlashServer;
import nl.hauntedmc.proxyfeatures.framework.localization.LocalizationHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SlashServerAdminCommandTest {

    private SlashServer feature;
    private LocalizationHandler localization;
    private LocalizationHandler.MessageBuilder builder;
    private SlashServerAdminCommand command;

    @BeforeEach
    void setUp() {
        feature = mock(SlashServer.class);
        localization = mock(LocalizationHandler.class);
        builder = mock(LocalizationHandler.MessageBuilder.class);

        when(feature.getLocalizationHandler()).thenReturn(localization);
        when(localization.getMessage(anyString())).thenReturn(builder);
        when(builder.with(anyString(), anyString())).thenReturn(builder);
        when(builder.with(anyString(), any(Number.class))).thenReturn(builder);
        when(builder.with(anyString(), any(Component.class))).thenReturn(builder);
        when(builder.forAudience(any())).thenReturn(builder);
        when(builder.build()).thenReturn(Component.text("ok"));

        command = new SlashServerAdminCommand(feature);
    }

    @Test
    void executeWithoutArgsShowsUsage() {
        CommandSource source = mock(CommandSource.class);

        command.execute(invocation(source));

        verify(localization).getMessage("slash.admin.usage");
    }

    @Test
    void statusUnknownServerShowsUnknownMessage() {
        CommandSource source = mock(CommandSource.class);
        when(source.hasPermission("proxyfeatures.feature.slashserver.command.status")).thenReturn(true);
        when(feature.normalizeServerName("survival")).thenReturn("survival");
        when(feature.hasConfiguredServer("survival")).thenReturn(false);

        command.execute(invocation(source, "status", "survival"));

        verify(localization).getMessage("slash.admin.unknown_server");
        verify(feature, never()).setServerEnabled(anyString(), anyBoolean());
    }

    @Test
    void enableUpdatesConfigWhenServerExistsAndStateChanges() {
        CommandSource source = mock(CommandSource.class);
        RegisteredServer server = mock(RegisteredServer.class);
        when(source.hasPermission("proxyfeatures.feature.slashserver.command.enable")).thenReturn(true);
        when(feature.normalizeServerName("survival")).thenReturn("survival");
        when(feature.hasConfiguredServer("survival")).thenReturn(true);
        when(feature.findServer("survival")).thenReturn(Optional.of(server));
        when(feature.isServerEnabled("survival")).thenReturn(false);

        command.execute(invocation(source, "enable", "survival"));

        verify(feature).setServerEnabled("survival", true);
        verify(localization).getMessage("slash.admin.set_enabled");
    }

    @Test
    void enableDoesNotWriteWhenAlreadyEnabled() {
        CommandSource source = mock(CommandSource.class);
        RegisteredServer server = mock(RegisteredServer.class);
        when(source.hasPermission("proxyfeatures.feature.slashserver.command.enable")).thenReturn(true);
        when(feature.normalizeServerName("survival")).thenReturn("survival");
        when(feature.hasConfiguredServer("survival")).thenReturn(true);
        when(feature.findServer("survival")).thenReturn(Optional.of(server));
        when(feature.isServerEnabled("survival")).thenReturn(true);

        command.execute(invocation(source, "enable", "survival"));

        verify(feature, never()).setServerEnabled(anyString(), anyBoolean());
        verify(localization).getMessage("slash.admin.already_enabled");
    }

    @Test
    void suggestAsyncReturnsCommandAndServerCompletions() {
        when(feature.getConfiguredServerNames()).thenReturn(List.of("survival", "skyblock", "creative"));

        assertEquals(List.of("list", "status", "enable", "disable"),
                command.suggestAsync(invocation(mock(CommandSource.class))).join());

        assertEquals(List.of("enable"), command.suggestAsync(invocation(mock(CommandSource.class), "en")).join());
        assertEquals(List.of("survival"),
                command.suggestAsync(invocation(mock(CommandSource.class), "enable", "su")).join());
    }

    @Test
    void hasPermissionUsesBasePermissionNode() {
        CommandSource source = mock(CommandSource.class);
        when(source.hasPermission("proxyfeatures.feature.slashserver.command")).thenReturn(true);

        assertTrue(command.hasPermission(invocation(source, "list")));
    }

    private static SimpleCommand.Invocation invocation(CommandSource source, String... args) {
        SimpleCommand.Invocation invocation = mock(SimpleCommand.Invocation.class);
        when(invocation.source()).thenReturn(source);
        when(invocation.arguments()).thenReturn(args);
        return invocation;
    }
}
