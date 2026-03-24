package nl.hauntedmc.proxyfeatures.framework.lifecycle;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.api.command.FeatureCommand;
import nl.hauntedmc.proxyfeatures.api.command.brigadier.BrigadierCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FeatureCommandManagerTest {

    private ProxyFeatures plugin;
    private CommandManager commandManager;
    private FeatureCommandManager manager;
    private CommandMeta simpleMeta;
    private CommandMeta brigadierMeta;

    @BeforeEach
    void setUp() {
        plugin = mock(ProxyFeatures.class);
        commandManager = mock(CommandManager.class);
        when(plugin.getCommandManager()).thenReturn(commandManager);
        when(plugin.getLogger()).thenReturn(ComponentLogger.logger("FeatureCommandManagerTest"));

        simpleMeta = mock(CommandMeta.class);
        brigadierMeta = mock(CommandMeta.class);

        CommandMeta.Builder simpleBuilder = mock(CommandMeta.Builder.class);
        when(simpleBuilder.aliases(any(String[].class))).thenReturn(simpleBuilder);
        when(simpleBuilder.plugin(plugin)).thenReturn(simpleBuilder);
        when(simpleBuilder.build()).thenReturn(simpleMeta);
        when(commandManager.metaBuilder(anyString())).thenReturn(simpleBuilder);

        CommandMeta.Builder brigadierBuilder = mock(CommandMeta.Builder.class);
        when(brigadierBuilder.aliases(any(String[].class))).thenReturn(brigadierBuilder);
        when(brigadierBuilder.plugin(plugin)).thenReturn(brigadierBuilder);
        when(brigadierBuilder.build()).thenReturn(brigadierMeta);
        when(commandManager.metaBuilder(any(com.velocitypowered.api.command.BrigadierCommand.class))).thenReturn(brigadierBuilder);

        manager = new FeatureCommandManager(plugin);
    }

    @Test
    void registersSimpleCommandAndSkipsDuplicate() {
        FeatureCommand command = mock(FeatureCommand.class);
        when(command.getName()).thenReturn("queue");
        when(command.getAliases()).thenReturn(new String[]{" q ", "", "queue", "q", null});

        manager.registerFeatureCommand(command);
        manager.registerFeatureCommand(command);

        verify(commandManager, times(1)).register(eq(simpleMeta), eq(command));
        assertEquals(1, manager.getRegisteredCommandCount());
        assertEquals(Set.of("queue"), manager.getAllRegisteredCommandNames());
    }

    @Test
    void registerSimpleCommandHandlesRegistrationFailure() {
        FeatureCommand command = mock(FeatureCommand.class);
        when(command.getName()).thenReturn("queue");
        when(command.getAliases()).thenReturn(new String[0]);
        when(commandManager.metaBuilder("queue")).thenThrow(new RuntimeException("boom"));

        manager.registerFeatureCommand(command);
        assertEquals(0, manager.getRegisteredCommandCount());
    }

    @Test
    void unregisterSimpleCommandHandlesUnknownAndException() {
        FeatureCommand command = mock(FeatureCommand.class);
        when(command.getName()).thenReturn("queue");
        when(command.getAliases()).thenReturn(new String[0]);
        manager.registerFeatureCommand(command);

        doThrow(new RuntimeException("boom")).when(commandManager).unregister("queue");
        manager.unregisterCommand("queue");
        manager.unregisterCommand("queue");

        assertEquals(0, manager.getRegisteredCommandCount());
    }

    @Test
    void unregisterAllSimpleCommandsIteratesSnapshot() {
        FeatureCommand one = mock(FeatureCommand.class);
        when(one.getName()).thenReturn("one");
        when(one.getAliases()).thenReturn(new String[0]);
        FeatureCommand two = mock(FeatureCommand.class);
        when(two.getName()).thenReturn("two");
        when(two.getAliases()).thenReturn(new String[0]);

        manager.registerFeatureCommand(one);
        manager.registerFeatureCommand(two);
        assertEquals(2, manager.getRegisteredCommandCount());

        manager.unregisterAllCommands();
        assertEquals(0, manager.getRegisteredCommandCount());
    }

    @Test
    void registersBrigadierCommandAndSkipsDuplicateAndCollision() {
        BrigadierCommand brigadier = mock(BrigadierCommand.class);
        when(brigadier.name()).thenReturn("proxyfeatures");
        when(brigadier.aliases()).thenReturn(List.of("pf"));
        when(brigadier.buildTree()).thenReturn(LiteralArgumentBuilder.<CommandSource>literal("proxyfeatures").build());
        when(commandManager.hasCommand("proxyfeatures")).thenReturn(false, false, true);

        manager.registerBrigadierCommand(brigadier);
        manager.registerBrigadierCommand(brigadier);

        BrigadierCommand collision = mock(BrigadierCommand.class);
        when(collision.name()).thenReturn("other");
        when(commandManager.hasCommand("other")).thenReturn(true);
        manager.registerBrigadierCommand(collision);

        assertEquals(1, manager.getRegisteredBrigadierCommandCount());
        verify(commandManager, times(1)).register(eq(brigadierMeta), any(com.velocitypowered.api.command.BrigadierCommand.class));
    }

    @Test
    void registerBrigadierHandlesFailure() {
        BrigadierCommand brigadier = mock(BrigadierCommand.class);
        when(brigadier.name()).thenReturn("proxyfeatures");
        when(brigadier.aliases()).thenReturn(List.of());
        when(brigadier.buildTree()).thenThrow(new RuntimeException("boom"));
        when(commandManager.hasCommand("proxyfeatures")).thenReturn(false);

        manager.registerBrigadierCommand(brigadier);
        assertEquals(0, manager.getRegisteredBrigadierCommandCount());
    }

    @Test
    void unregisterBrigadierByMetaAndFallbackAndFailurePath() throws Exception {
        BrigadierCommand brigadier = mock(BrigadierCommand.class);
        when(brigadier.name()).thenReturn("proxyfeatures");
        when(brigadier.aliases()).thenReturn(List.of("pf"));
        when(brigadier.buildTree()).thenReturn(LiteralArgumentBuilder.<CommandSource>literal("proxyfeatures").build());
        when(commandManager.hasCommand("proxyfeatures")).thenReturn(false);
        manager.registerBrigadierCommand(brigadier);

        manager.unregisterBrigadierCommand("proxyfeatures");
        assertEquals(0, manager.getRegisteredBrigadierCommandCount());

        Map<String, BrigadierCommand> commands = privateMap("registeredBrigadierCommands");
        Map<String, CommandMeta> metas = privateMap("brigadierMetas");
        commands.put("fallback", brigadier);
        metas.clear();
        manager.unregisterBrigadierCommand("fallback");
        verify(commandManager).unregister("fallback");
        verify(commandManager).unregister("pf");

        commands.put("failing", brigadier);
        metas.put("failing", brigadierMeta);
        doThrow(new RuntimeException("boom")).when(commandManager).unregister(brigadierMeta);
        manager.unregisterBrigadierCommand("failing");
        manager.unregisterBrigadierCommand("missing");
    }

    @Test
    void unregisterAllBrigadierCommandsHandlesEmptyAndNonEmpty() {
        manager.unregisterAllBrigadierCommands();

        BrigadierCommand brigadier = mock(BrigadierCommand.class);
        when(brigadier.name()).thenReturn("proxyfeatures");
        when(brigadier.aliases()).thenReturn(List.of());
        when(brigadier.buildTree()).thenReturn(LiteralArgumentBuilder.<CommandSource>literal("proxyfeatures").build());
        when(commandManager.hasCommand("proxyfeatures")).thenReturn(false);
        manager.registerBrigadierCommand(brigadier);

        manager.unregisterAllBrigadierCommands();
        assertEquals(0, manager.getRegisteredBrigadierCommandCount());
    }

    @Test
    void reportingMethodsExposeCombinedCountsAndNames() {
        FeatureCommand command = mock(FeatureCommand.class);
        when(command.getName()).thenReturn("queue");
        when(command.getAliases()).thenReturn(new String[0]);
        manager.registerFeatureCommand(command);

        BrigadierCommand brigadier = mock(BrigadierCommand.class);
        when(brigadier.name()).thenReturn("proxyfeatures");
        when(brigadier.aliases()).thenReturn(List.of());
        when(brigadier.buildTree()).thenReturn(LiteralArgumentBuilder.<CommandSource>literal("proxyfeatures").build());
        when(commandManager.hasCommand("proxyfeatures")).thenReturn(false);
        manager.registerBrigadierCommand(brigadier);

        assertEquals(1, manager.getRegisteredCommands().size());
        assertEquals(1, manager.getRegisteredBrigadierCommands().size());
        assertEquals(2, manager.getTotalRegisteredCommandCount());
        assertEquals(Set.of("queue", "proxyfeatures"), manager.getAllRegisteredCommandNames());
    }

    @SuppressWarnings("unchecked")
    private <K, V> Map<K, V> privateMap(String fieldName) throws Exception {
        Field f = FeatureCommandManager.class.getDeclaredField(fieldName);
        f.setAccessible(true);
        return (Map<K, V>) f.get(manager);
    }
}
