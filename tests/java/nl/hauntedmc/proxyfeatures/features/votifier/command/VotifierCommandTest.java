package nl.hauntedmc.proxyfeatures.features.votifier.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.proxyfeatures.framework.localization.LocalizationHandler;
import nl.hauntedmc.proxyfeatures.features.votifier.Votifier;
import nl.hauntedmc.proxyfeatures.features.votifier.internal.VoteLeaderboardEntry;
import nl.hauntedmc.proxyfeatures.features.votifier.internal.VotifierService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class VotifierCommandTest {

    private Votifier feature;
    private VotifierService service;
    private LocalizationHandler localization;
    private LocalizationHandler.MessageBuilder messageBuilder;
    private VotifierCommand command;

    @BeforeEach
    void setUp() {
        feature = mock(Votifier.class);
        service = mock(VotifierService.class);
        localization = mock(LocalizationHandler.class);
        messageBuilder = mock(LocalizationHandler.MessageBuilder.class);

        when(feature.getService()).thenReturn(service);
        when(feature.getLocalizationHandler()).thenReturn(localization);
        when(feature.isRunning()).thenReturn(true);
        when(feature.currentHost()).thenReturn("0.0.0.0");
        when(feature.currentPort()).thenReturn(8249);
        when(feature.currentTimeoutMs()).thenReturn(5000);
        when(feature.currentKeyBits()).thenReturn(2048);

        when(localization.getMessage(anyString())).thenReturn(messageBuilder);
        when(messageBuilder.forAudience(any())).thenReturn(messageBuilder);
        when(messageBuilder.with(anyString(), anyString())).thenReturn(messageBuilder);
        when(messageBuilder.with(anyString(), any(Component.class))).thenReturn(messageBuilder);
        when(messageBuilder.build()).thenReturn(Component.text("ok"));

        when(service.currentYearMonth()).thenReturn(YearMonth.of(2026, 4));

        command = new VotifierCommand(feature);
    }

    @Test
    void parseRemindModeSupportsAliasesAndRejectsUnknown() throws Exception {
        assertEquals(VotifierService.RemindMode.ON, invokeStaticParseMode("on"));
        assertEquals(VotifierService.RemindMode.ON, invokeStaticParseMode("aan"));
        assertEquals(VotifierService.RemindMode.ON, invokeStaticParseMode("1"));
        assertEquals(VotifierService.RemindMode.OFF, invokeStaticParseMode("off"));
        assertEquals(VotifierService.RemindMode.OFF, invokeStaticParseMode("uit"));
        assertEquals(VotifierService.RemindMode.OFF, invokeStaticParseMode("0"));
        assertEquals(VotifierService.RemindMode.TOGGLE, invokeStaticParseMode("toggle"));
        assertNull(invokeStaticParseMode("unknown"));
    }

    @Test
    void parseMonthSupportsCurrentPreviousIsoAndEuFormats() throws Exception {
        assertEquals(YearMonth.of(2026, 4), invokeParseMonth("current"));
        assertEquals(YearMonth.of(2026, 3), invokeParseMonth("previous"));
        assertEquals(YearMonth.of(2026, 2), invokeParseMonth("2026-02"));
        assertEquals(YearMonth.of(2026, 1), invokeParseMonth("01-2026"));
        assertEquals(YearMonth.of(2026, 4), invokeParseMonth("bad-input"));
    }

    @Test
    void statusCommandRequiresPermission() {
        CommandSource sender = mock(CommandSource.class);
        when(sender.hasPermission(anyString())).thenReturn(true);
        when(sender.hasPermission("proxyfeatures.feature.votifier.command.status")).thenReturn(false);

        CommandDispatcher<CommandSource> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(command.buildTree());

        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("vote status", sender));
        verify(sender, never()).sendMessage(any(Component.class));
    }

    @Test
    void topCommandSendsEmptyMessageWhenStatsDisabled() throws Exception {
        CommandSource sender = mock(CommandSource.class);
        when(sender.hasPermission(anyString())).thenReturn(true);
        when(service.isStatsEnabled()).thenReturn(false);

        CommandDispatcher<CommandSource> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(command.buildTree());
        dispatcher.execute("vote top current 5", sender);

        verify(localization).getMessage("votifier.command.top.empty");
        verify(sender).sendMessage(any(Component.class));
    }

    @Test
    void topCommandRendersHeaderAndEntriesWhenDataExists() throws Exception {
        CommandSource sender = mock(CommandSource.class);
        when(sender.hasPermission(anyString())).thenReturn(true);
        when(service.isStatsEnabled()).thenReturn(true);
        when(service.topForMonth(eq(YearMonth.of(2026, 4)), eq(2)))
                .thenReturn(List.of(new VoteLeaderboardEntry(1L, "Remy", 9, 99L)));

        CommandDispatcher<CommandSource> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(command.buildTree());
        dispatcher.execute("vote top current 2", sender);

        verify(localization).getMessage("votifier.command.top.header");
        verify(localization).getMessage("votifier.command.top.entry");
        verify(sender, atLeast(2)).sendMessage(any(Component.class));
    }

    @SuppressWarnings("unchecked")
    private static VotifierService.RemindMode invokeStaticParseMode(String raw) throws Exception {
        Method m = VotifierCommand.class.getDeclaredMethod("parseRemindMode", String.class);
        m.setAccessible(true);
        return (VotifierService.RemindMode) m.invoke(null, raw);
    }

    private YearMonth invokeParseMonth(String raw) throws Exception {
        Method m = VotifierCommand.class.getDeclaredMethod("parseMonth", String.class);
        m.setAccessible(true);
        return (YearMonth) m.invoke(command, raw);
    }
}
