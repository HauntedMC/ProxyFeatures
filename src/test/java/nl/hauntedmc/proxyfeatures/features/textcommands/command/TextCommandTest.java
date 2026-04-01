package nl.hauntedmc.proxyfeatures.features.textcommands.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.proxyfeatures.features.textcommands.TextCommands;
import nl.hauntedmc.proxyfeatures.framework.localization.LocalizationHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TextCommandTest {

    private TextCommands feature;
    private LocalizationHandler localization;
    private LocalizationHandler.MessageBuilder builder;
    private TextCommand command;

    @BeforeEach
    void setUp() {
        feature = mock(TextCommands.class);
        localization = mock(LocalizationHandler.class);
        builder = mock(LocalizationHandler.MessageBuilder.class);

        when(feature.getLocalizationHandler()).thenReturn(localization);
        when(localization.getMessage(anyString())).thenReturn(builder);
        when(builder.withPlaceholders(any())).thenReturn(builder);
        when(builder.forAudience(any())).thenReturn(builder);
        when(builder.build()).thenReturn(Component.text("output"));

        command = new TextCommand(feature, "store", "text.store", Map.of("url", "https://example.test"));
    }

    @Test
    void executeRejectsNonPlayerSource() {
        CommandSource source = mock(CommandSource.class);

        command.execute(invocation(source));

        verify(localization).getMessage("general.player_command");
    }

    @Test
    void executeSendsConfiguredMessageToPlayer() {
        Player player = mock(Player.class);
        Component output = Component.text("output");
        when(builder.build()).thenReturn(output);

        command.execute(invocation(player));

        verify(localization).getMessage("text.store");
        verify(player).sendMessage(output);
    }

    @Test
    void metadataMethodsReturnConfiguredValues() {
        assertEquals("store", command.getName());
        assertEquals(0, command.getAliases().length);
    }

    private static SimpleCommand.Invocation invocation(CommandSource source, String... args) {
        SimpleCommand.Invocation invocation = mock(SimpleCommand.Invocation.class);
        when(invocation.source()).thenReturn(source);
        when(invocation.arguments()).thenReturn(args);
        return invocation;
    }
}
