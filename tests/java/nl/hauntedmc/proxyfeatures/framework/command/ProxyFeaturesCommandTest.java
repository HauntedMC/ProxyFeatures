package nl.hauntedmc.proxyfeatures.framework.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.features.VelocityBaseFeature;
import nl.hauntedmc.proxyfeatures.framework.lifecycle.FeatureCommandManager;
import nl.hauntedmc.proxyfeatures.framework.lifecycle.FeatureDataManager;
import nl.hauntedmc.proxyfeatures.framework.lifecycle.FeatureLifecycleManager;
import nl.hauntedmc.proxyfeatures.framework.lifecycle.FeatureListenerManager;
import nl.hauntedmc.proxyfeatures.framework.lifecycle.FeatureTaskManager;
import nl.hauntedmc.proxyfeatures.framework.loader.FeatureLoadManager;
import nl.hauntedmc.proxyfeatures.framework.loader.FeatureRegistry;
import nl.hauntedmc.proxyfeatures.framework.loader.disable.FeatureDisableResponse;
import nl.hauntedmc.proxyfeatures.framework.loader.disable.FeatureDisableResult;
import nl.hauntedmc.proxyfeatures.framework.loader.enable.FeatureEnableResponse;
import nl.hauntedmc.proxyfeatures.framework.loader.enable.FeatureEnableResult;
import nl.hauntedmc.proxyfeatures.framework.loader.reload.FeatureReloadResponse;
import nl.hauntedmc.proxyfeatures.framework.loader.reload.FeatureReloadResult;
import nl.hauntedmc.proxyfeatures.framework.loader.softreload.FeatureSoftReloadResponse;
import nl.hauntedmc.proxyfeatures.framework.loader.softreload.FeatureSoftReloadResult;
import nl.hauntedmc.proxyfeatures.framework.localization.LocalizationHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

class ProxyFeaturesCommandTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private ProxyFeatures plugin;
    private FeatureLoadManager loadManager;
    private FeatureRegistry registry;
    private LocalizationHandler localization;
    private LocalizationHandler.MessageBuilder messageBuilder;
    private CommandSource sender;
    private ProxyFeaturesCommand command;

    @BeforeEach
    void setUp() {
        plugin = mock(ProxyFeatures.class);
        loadManager = mock(FeatureLoadManager.class);
        registry = new FeatureRegistry();
        localization = mock(LocalizationHandler.class);
        messageBuilder = mock(LocalizationHandler.MessageBuilder.class);
        sender = mock(CommandSource.class);

        when(plugin.getFeatureLoadManager()).thenReturn(loadManager);
        when(plugin.getLocalizationHandler()).thenReturn(localization);
        when(plugin.getLogger()).thenReturn(mock(ComponentLogger.class));
        when(loadManager.getFeatureRegistry()).thenReturn(registry);
        when(sender.hasPermission(anyString())).thenReturn(true);

        when(localization.getMessage(anyString())).thenReturn(messageBuilder);
        when(messageBuilder.forAudience(any())).thenReturn(messageBuilder);
        when(messageBuilder.with(anyString(), anyString())).thenReturn(messageBuilder);
        when(messageBuilder.with(anyString(), any(Number.class))).thenReturn(messageBuilder);
        when(messageBuilder.with(anyString(), any(Component.class))).thenReturn(messageBuilder);
        when(messageBuilder.autoLinkUrls(anyBoolean())).thenReturn(messageBuilder);
        when(messageBuilder.autoLinkUnderline(anyBoolean())).thenReturn(messageBuilder);
        when(messageBuilder.build()).thenReturn(Component.text("localized"));

        command = new ProxyFeaturesCommand(plugin);
    }

    @SuppressWarnings("unchecked")
    @Test
    void metadataAndDispatcherExecutionWorkForAllSubcommands() throws Exception {
        assertEquals("proxyfeatures", command.name());
        assertTrue(command.description().contains("framework command"));

        VelocityBaseFeature<?> queue = feature("Queue", "1.0", List.of("luckperms"), List.of());
        registry.registerLoadedFeature("Queue", queue);
        registry.registerAvailableFeature("Queue", (Class<? extends VelocityBaseFeature<?>>) (Class<?>) VelocityBaseFeature.class);
        registry.registerAvailableFeature("Friends", (Class<? extends VelocityBaseFeature<?>>) (Class<?>) VelocityBaseFeature.class);

        when(loadManager.enableFeature(anyString())).thenReturn(new FeatureEnableResponse(FeatureEnableResult.SUCCESS, Set.of(), Set.of()));
        when(loadManager.disableFeature(anyString())).thenReturn(new FeatureDisableResponse(FeatureDisableResult.SUCCESS, "Queue", Set.of()));
        when(loadManager.softReloadFeature(anyString())).thenReturn(new FeatureSoftReloadResponse(FeatureSoftReloadResult.SUCCESS, "Queue"));
        when(loadManager.reloadFeature(anyString())).thenReturn(new FeatureReloadResponse(FeatureReloadResult.SUCCESS, "Queue", Set.of()));

        CommandDispatcher<CommandSource> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(command.buildTree());

        dispatcher.execute("proxyfeatures", sender);
        dispatcher.execute("proxyfeatures status", sender);
        dispatcher.execute("proxyfeatures list", sender);
        dispatcher.execute("proxyfeatures list --version", sender);
        dispatcher.execute("proxyfeatures info Queue", sender);
        dispatcher.execute("proxyfeatures reloadlocal", sender);
        dispatcher.execute("proxyfeatures softreload Queue", sender);
        dispatcher.execute("proxyfeatures reload Queue", sender);
        dispatcher.execute("proxyfeatures enable Friends", sender);
        dispatcher.execute("proxyfeatures disable Queue", sender);

        verify(sender, atLeastOnce()).sendMessage(any(Component.class));
        verify(loadManager).softReloadFeature("Queue");
        verify(loadManager, atLeastOnce()).reloadFeature("Queue");
        verify(loadManager).enableFeature("Friends");
        verify(loadManager).disableFeature("Queue");
        verify(localization).getMessage("general.usage");
        verify(localization).getMessage("command.reloadlocal.success");
        verify(localization).getMessage("command.softreload.success");
        verify(localization).getMessage("command.reload.success");
        verify(localization).getMessage("command.enable.success");
        verify(localization).getMessage("command.disable.success");
    }

    @SuppressWarnings("unchecked")
    @Test
    void handleInfoCoversBlankLoadedCaseInsensitiveAvailableAndMissing() throws Exception {
        VelocityBaseFeature<?> queue = feature("Queue", "2.0", List.of("A"), List.of("B"));
        registry.registerLoadedFeature("Queue", queue);
        registry.registerAvailableFeature("Friends", (Class<? extends VelocityBaseFeature<?>>) (Class<?>) VelocityBaseFeature.class);

        invoke("handleInfo", new Class[]{CommandSource.class, String.class}, sender, null);
        invoke("handleInfo", new Class[]{CommandSource.class, String.class}, sender, "Queue");
        invoke("handleInfo", new Class[]{CommandSource.class, String.class}, sender, "queue");
        invoke("handleInfo", new Class[]{CommandSource.class, String.class}, sender, "Friends");
        invoke("handleInfo", new Class[]{CommandSource.class, String.class}, sender, "friends");
        invoke("handleInfo", new Class[]{CommandSource.class, String.class}, sender, "missing");

        ArgumentCaptor<Component> msgCaptor = ArgumentCaptor.forClass(Component.class);
        verify(sender, times(6)).sendMessage(msgCaptor.capture());
        List<String> messages = msgCaptor.getAllValues().stream().map(PLAIN::serialize).toList();
        assertTrue(messages.get(0).contains("Please provide a feature name"));
        assertTrue(messages.get(1).contains("Feature: Queue"));
        assertTrue(messages.get(1).contains("Status: enabled"));
        assertTrue(messages.get(1).contains("Version: v2.0"));
        assertTrue(messages.get(2).contains("Feature: Queue"));
        assertTrue(messages.get(3).contains("Feature: Friends"));
        assertTrue(messages.get(3).contains("Status: disabled"));
        assertTrue(messages.get(4).contains("Feature: Friends"));
        assertTrue(messages.get(5).contains("Feature not found: missing"));
    }

    @Test
    void enableDisableSoftReloadAndReloadSwitchBranchesAreCovered() throws Exception {
        when(loadManager.enableFeature("Queue")).thenReturn(
                new FeatureEnableResponse(FeatureEnableResult.SUCCESS, Set.of(), Set.of()),
                new FeatureEnableResponse(FeatureEnableResult.NOT_FOUND, Set.of(), Set.of()),
                new FeatureEnableResponse(FeatureEnableResult.ALREADY_LOADED, Set.of(), Set.of()),
                new FeatureEnableResponse(FeatureEnableResult.MISSING_PLUGIN_DEPENDENCY, Set.of("A"), Set.of()),
                new FeatureEnableResponse(FeatureEnableResult.MISSING_FEATURE_DEPENDENCY, Set.of(), Set.of("B")),
                new FeatureEnableResponse(FeatureEnableResult.FAILED, Set.of(), Set.of())
        );

        invoke("handleEnable", new Class[]{CommandSource.class, String.class}, sender, "Queue");
        invoke("handleEnable", new Class[]{CommandSource.class, String.class}, sender, "Queue");
        invoke("handleEnable", new Class[]{CommandSource.class, String.class}, sender, "Queue");
        invoke("handleEnable", new Class[]{CommandSource.class, String.class}, sender, "Queue");
        invoke("handleEnable", new Class[]{CommandSource.class, String.class}, sender, "Queue");
        invoke("handleEnable", new Class[]{CommandSource.class, String.class}, sender, "Queue");

        when(loadManager.disableFeature("Queue")).thenReturn(
                new FeatureDisableResponse(FeatureDisableResult.SUCCESS, "Queue", Set.of("Friends")),
                new FeatureDisableResponse(FeatureDisableResult.SUCCESS, "Queue", Set.of()),
                new FeatureDisableResponse(FeatureDisableResult.NOT_LOADED, "Queue", Set.of()),
                new FeatureDisableResponse(FeatureDisableResult.FAILED, "Queue", Set.of())
        );
        invoke("handleDisable", new Class[]{CommandSource.class, String.class}, sender, "Queue");
        invoke("handleDisable", new Class[]{CommandSource.class, String.class}, sender, "Queue");
        invoke("handleDisable", new Class[]{CommandSource.class, String.class}, sender, "Queue");
        invoke("handleDisable", new Class[]{CommandSource.class, String.class}, sender, "Queue");

        when(loadManager.softReloadFeature("Queue")).thenReturn(
                new FeatureSoftReloadResponse(FeatureSoftReloadResult.SUCCESS, "Queue"),
                new FeatureSoftReloadResponse(FeatureSoftReloadResult.NOT_LOADED, "Queue"),
                new FeatureSoftReloadResponse(FeatureSoftReloadResult.FAILED, "Queue")
        );
        invoke("handleSoftReload", new Class[]{CommandSource.class, String.class}, sender, "Queue");
        invoke("handleSoftReload", new Class[]{CommandSource.class, String.class}, sender, "Queue");
        invoke("handleSoftReload", new Class[]{CommandSource.class, String.class}, sender, "Queue");

        when(loadManager.reloadFeature("Queue")).thenReturn(
                new FeatureReloadResponse(FeatureReloadResult.SUCCESS, "Queue", Set.of("Friends")),
                new FeatureReloadResponse(FeatureReloadResult.SUCCESS, "Queue", Set.of()),
                new FeatureReloadResponse(FeatureReloadResult.NOT_LOADED, "Queue", Set.of()),
                new FeatureReloadResponse(FeatureReloadResult.FAILED, "Queue", Set.of())
        );
        invoke("handleReload", new Class[]{CommandSource.class, String.class}, sender, "Queue");
        invoke("handleReload", new Class[]{CommandSource.class, String.class}, sender, "Queue");
        invoke("handleReload", new Class[]{CommandSource.class, String.class}, sender, "Queue");
        invoke("handleReload", new Class[]{CommandSource.class, String.class}, sender, "Queue");

        verify(localization).getMessage("command.enable.success");
        verify(localization).getMessage("command.enable.not_found");
        verify(localization).getMessage("command.enable.already_loaded");
        verify(localization).getMessage("command.enable.missing_plugin_dependency");
        verify(localization).getMessage("command.enable.missing_feature_dependency");
        verify(localization).getMessage("command.enable.failed");

        verify(localization).getMessage("command.disable.success_with_dependents");
        verify(localization).getMessage("command.disable.success");
        verify(localization).getMessage("command.disable.not_loaded");
        verify(localization).getMessage("command.disable.failed");

        verify(localization).getMessage("command.softreload.success");
        verify(localization).getMessage("command.softreload.not_loaded");
        verify(localization).getMessage("command.softreload.failed");

        verify(localization).getMessage("command.reload.success_with_dependents");
        verify(localization).getMessage("command.reload.success");
        verify(localization).getMessage("command.reload.not_loaded");
        verify(localization).getMessage("command.reload.failed");

        verify(messageBuilder).with("plugins", "A");
        verify(messageBuilder).with("features", "B");
        verify(messageBuilder, atLeastOnce()).with("dependents", "Friends");
    }

    @SuppressWarnings("unchecked")
    @Test
    void suggestionAndRenderingHelpersReturnExpectedValues() throws Exception {
        VelocityBaseFeature<?> queue = feature("Queue", "1.0", List.of(), List.of());
        VelocityBaseFeature<?> vanish = feature("Vanish", "1.0", List.of(), List.of());
        registry.registerLoadedFeature("Queue", queue);
        registry.registerLoadedFeature("Vanish", vanish);
        registry.registerAvailableFeature("Queue", (Class<? extends VelocityBaseFeature<?>>) (Class<?>) VelocityBaseFeature.class);
        registry.registerAvailableFeature("Friends", (Class<? extends VelocityBaseFeature<?>>) (Class<?>) VelocityBaseFeature.class);
        registry.registerAvailableFeature("Vanish", (Class<? extends VelocityBaseFeature<?>>) (Class<?>) VelocityBaseFeature.class);

        Suggestions loaded = ((CompletableFuture<Suggestions>) invoke("suggestLoadedFeatures",
                new Class[]{SuggestionsBuilder.class},
                new SuggestionsBuilder("qu", 0))).join();
        assertEquals(List.of("Queue"), loaded.getList().stream().map(Suggestion::getText).toList());

        Suggestions enable = ((CompletableFuture<Suggestions>) invoke("suggestEnableCandidates",
                new Class[]{SuggestionsBuilder.class},
                new SuggestionsBuilder("f", 0))).join();
        assertEquals(List.of("Friends"), enable.getList().stream().map(Suggestion::getText).toList());

        Suggestions any = ((CompletableFuture<Suggestions>) invoke("suggestAnyFeature",
                new Class[]{SuggestionsBuilder.class},
                new SuggestionsBuilder("v", 0))).join();
        assertEquals(List.of("Vanish"), any.getList().stream().map(Suggestion::getText).toList());

        Suggestions anyDisabled = ((CompletableFuture<Suggestions>) invoke("suggestAnyFeature",
                new Class[]{SuggestionsBuilder.class},
                new SuggestionsBuilder("f", 0))).join();
        assertEquals(List.of("Friends"), anyDisabled.getList().stream().map(Suggestion::getText).toList());

        Component none = (Component) invoke("renderCsvColored",
                new Class[]{List.class, net.kyori.adventure.text.format.NamedTextColor.class,
                        net.kyori.adventure.text.format.NamedTextColor.class, boolean.class},
                null,
                net.kyori.adventure.text.format.NamedTextColor.GREEN,
                net.kyori.adventure.text.format.NamedTextColor.GRAY,
                true);
        assertTrue(PLAIN.serialize(none).contains("none"));

        Component emptyHidden = (Component) invoke("renderCsvColored",
                new Class[]{List.class, net.kyori.adventure.text.format.NamedTextColor.class,
                        net.kyori.adventure.text.format.NamedTextColor.class, boolean.class},
                List.of(),
                net.kyori.adventure.text.format.NamedTextColor.GREEN,
                net.kyori.adventure.text.format.NamedTextColor.GRAY,
                false);
        assertEquals("", PLAIN.serialize(emptyHidden));

        Component csv = (Component) invoke("renderCsvColored",
                new Class[]{List.class, net.kyori.adventure.text.format.NamedTextColor.class,
                        net.kyori.adventure.text.format.NamedTextColor.class, boolean.class},
                List.of("A", "B"),
                net.kyori.adventure.text.format.NamedTextColor.GREEN,
                net.kyori.adventure.text.format.NamedTextColor.GRAY,
                false);
        assertTrue(PLAIN.serialize(csv).contains("A, B"));
    }

    @Test
    void statusAndListHelpersEmitMessages() throws Exception {
        VelocityBaseFeature<?> queue = feature("Queue", "1.0", List.of(), List.of());
        VelocityBaseFeature<?> friends = feature("Friends", "2.0", List.of(), List.of());
        registry.registerLoadedFeature("Queue", queue);
        registry.registerLoadedFeature("Friends", friends);

        invoke("sendPluginStatus", new Class[]{CommandSource.class}, sender);
        invoke("listLoadedFeaturesOneLine", new Class[]{CommandSource.class, boolean.class}, sender, false);
        invoke("listLoadedFeaturesOneLine", new Class[]{CommandSource.class, boolean.class}, sender, true);

        ArgumentCaptor<Component> msgCaptor = ArgumentCaptor.forClass(Component.class);
        verify(sender, times(9)).sendMessage(msgCaptor.capture());
        List<String> messages = msgCaptor.getAllValues().stream().map(PLAIN::serialize).toList();
        assertTrue(messages.stream().anyMatch(m -> m.contains("Number of loaded features: 2")));
        assertTrue(messages.stream().anyMatch(m -> m.contains("Number of active database connections: 6")));
        assertTrue(messages.stream().anyMatch(m -> m.contains("Number of active tasks: 2")));
        assertTrue(messages.stream().anyMatch(m -> m.contains("Number of registered listeners: 4")));
        assertTrue(messages.stream().anyMatch(m -> m.contains("Number of registered commands: 2")));
        assertTrue(messages.stream().anyMatch(m -> m.contains("Enabled Features (2): Friends, Queue")));
        assertTrue(messages.stream().anyMatch(m -> m.contains("Enabled Features (2): Friends (v2.0), Queue (v1.0)")));
    }

    @SuppressWarnings("unchecked")
    @Test
    void reloadLocalFailureBranchSendsFailureMessage() throws Exception {
        registry.registerAvailableFeature("Queue", (Class<? extends VelocityBaseFeature<?>>) (Class<?>) VelocityBaseFeature.class);
        doThrow(new RuntimeException("boom")).when(localization).reloadLocalization();

        CommandDispatcher<CommandSource> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(command.buildTree());
        dispatcher.execute("proxyfeatures reloadlocal", sender);

        verify(localization, atLeastOnce()).getMessage("command.reloadlocal.fail");
        verify(localization, never()).getMessage("command.reloadlocal.success");
        verify(sender, atLeastOnce()).sendMessage(any(Component.class));
    }

    @Test
    void commandTreeEnforcesRootAndSubcommandPermissions() {
        CommandDispatcher<CommandSource> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(command.buildTree());

        when(sender.hasPermission(anyString())).thenReturn(false);
        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("proxyfeatures", sender));
        verify(sender, never()).sendMessage(any(Component.class));

        reset(sender);
        when(sender.hasPermission(anyString())).thenReturn(false);
        when(sender.hasPermission("proxyfeatures.use")).thenReturn(true);
        when(sender.hasPermission("proxyfeatures.command.status")).thenReturn(false);
        assertDoesNotThrow(() -> dispatcher.execute("proxyfeatures", sender));
        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("proxyfeatures status", sender));
        verify(sender, times(1)).sendMessage(any(Component.class));
    }

    private VelocityBaseFeature<?> feature(String name, String version, List<String> pluginDeps, List<String> featureDeps) {
        VelocityBaseFeature<?> feature = mock(VelocityBaseFeature.class);
        when(feature.getFeatureName()).thenReturn(name);
        when(feature.getFeatureVersion()).thenReturn(version);
        when(feature.getPluginDependencies()).thenReturn(pluginDeps);
        when(feature.getDependencies()).thenReturn(featureDeps);

        FeatureLifecycleManager lifecycle = mock(FeatureLifecycleManager.class);
        FeatureCommandManager cmd = mock(FeatureCommandManager.class);
        FeatureTaskManager task = mock(FeatureTaskManager.class);
        FeatureListenerManager listeners = mock(FeatureListenerManager.class);
        FeatureDataManager data = mock(FeatureDataManager.class);

        when(cmd.getRegisteredCommands()).thenReturn(Map.of(name.toLowerCase(), mock(nl.hauntedmc.proxyfeatures.api.command.FeatureCommand.class)));
        when(task.getActiveTaskCount()).thenReturn(1);
        when(listeners.getRegisteredListenerCount()).thenReturn(2);
        when(data.getActiveConnCount()).thenReturn(3);

        when(lifecycle.getCommandManager()).thenReturn(cmd);
        when(lifecycle.getTaskManager()).thenReturn(task);
        when(lifecycle.getListenerManager()).thenReturn(listeners);
        when(lifecycle.getDataManager()).thenReturn(data);
        when(feature.getLifecycleManager()).thenReturn(lifecycle);
        return feature;
    }

    private Object invoke(String methodName, Class<?>[] types, Object... args) throws Exception {
        Method m = ProxyFeaturesCommand.class.getDeclaredMethod(methodName, types);
        m.setAccessible(true);
        return m.invoke(command, args);
    }
}
