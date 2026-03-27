package nl.hauntedmc.proxyfeatures.framework.lifecycle;

import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.PluginManager;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import nl.hauntedmc.dataprovider.api.DataProviderAPI;
import nl.hauntedmc.dataprovider.api.DataProviderApiSupplier;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataprovider.database.DatabaseProvider;
import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.framework.config.MainConfigHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import javax.sql.DataSource;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeatureDataManagerTest {

    private ProxyFeatures plugin;
    private ComponentLogger logger;
    private MainConfigHandler config;
    private PluginManager pluginManager;

    @BeforeEach
    void setUp() {
        plugin = mock(ProxyFeatures.class);
        logger = ComponentLogger.logger("FeatureDataManagerTest");
        config = mock(MainConfigHandler.class);
        pluginManager = mock(PluginManager.class);
        when(plugin.getLogger()).thenReturn(logger);
        when(plugin.getConfigHandler()).thenReturn(config);
        when(plugin.getPluginManager()).thenReturn(pluginManager);
        when(config.getGlobalSetting(anyString(), eq(String.class), anyString())).thenReturn("");
    }

    @Test
    void registerConnectionBeforeInitReturnsEmpty() {
        DataProviderAPI api = mock(DataProviderAPI.class);
        FeatureDataManager manager = new FeatureDataManager(plugin, api);

        Optional<DatabaseProvider> result = manager.registerConnection("main", DatabaseType.MYSQL, "default");
        assertTrue(result.isEmpty());
    }

    @Test
    void initDataProviderRejectsBlankFeatureName() {
        DataProviderAPI api = mock(DataProviderAPI.class);
        FeatureDataManager manager = new FeatureDataManager(plugin, api);

        manager.initDataProvider("   ");
        Optional<DatabaseProvider> result = manager.registerConnection("main", DatabaseType.MYSQL, "default");
        assertTrue(result.isEmpty());
    }

    @Test
    void initDataProviderFailsWhenApiUnavailable() {
        FeatureDataManager manager = new FeatureDataManager(plugin, null);
        manager.initDataProvider("Queue");

        Optional<DatabaseProvider> result = manager.registerConnection("main", DatabaseType.MYSQL, "default");
        assertTrue(result.isEmpty());
    }

    @Test
    void initDataProviderRegistersConnectedProvider() {
        DataProviderAPI api = mock(DataProviderAPI.class);
        DatabaseProvider provider = mock(DatabaseProvider.class);
        when(provider.isConnected()).thenReturn(true);
        when(api.registerDatabaseOptional(DatabaseType.MYSQL, "default")).thenReturn(Optional.of(provider));

        FeatureDataManager manager = new FeatureDataManager(plugin, api);
        manager.initDataProvider("Queue");

        Optional<DatabaseProvider> registered = manager.registerConnection("main", DatabaseType.MYSQL, "default");
        assertTrue(registered.isPresent());
        assertEquals(1, manager.getActiveConnCount());
        assertTrue(manager.getDataProvider("main").isPresent());
        verify(api).registerDatabaseOptional(DatabaseType.MYSQL, "default");
    }

    @Test
    void registerConnectionHandlesApiFailureNullAndDisconnectedProviders() {
        DataProviderAPI api = mock(DataProviderAPI.class);
        DatabaseProvider disconnected = mock(DatabaseProvider.class);
        when(disconnected.isConnected()).thenReturn(false);
        when(api.registerDatabaseOptional(DatabaseType.MYSQL, "default"))
                .thenThrow(new RuntimeException("boom"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(disconnected));

        FeatureDataManager manager = new FeatureDataManager(plugin, api);
        manager.initDataProvider("Queue");

        assertTrue(manager.registerConnection("main", DatabaseType.MYSQL, "default").isEmpty());
        assertTrue(manager.registerConnection("main", DatabaseType.MYSQL, "default").isEmpty());
        assertTrue(manager.registerConnection("main", DatabaseType.MYSQL, "default").isEmpty());
    }

    @Test
    void createOrmContextReturnsEmptyWhenProviderMissing() {
        DataProviderAPI api = mock(DataProviderAPI.class);
        FeatureDataManager manager = new FeatureDataManager(plugin, api);
        manager.initDataProvider("Queue");

        assertTrue(manager.createORMContext("missing", String.class).isEmpty());
    }

    @Test
    void createOrmContextReturnsEmptyWhenDataSourceUnsupportedOrNull() {
        DataProviderAPI apiUnsupported = mock(DataProviderAPI.class);
        DatabaseProvider providerUnsupported = mock(DatabaseProvider.class);
        when(providerUnsupported.isConnected()).thenReturn(true);
        when(providerUnsupported.getDataSourceOptional()).thenReturn(Optional.empty());
        when(apiUnsupported.registerDatabaseOptional(DatabaseType.MYSQL, "default")).thenReturn(Optional.of(providerUnsupported));

        FeatureDataManager managerUnsupported = new FeatureDataManager(plugin, apiUnsupported);
        managerUnsupported.initDataProvider("Queue");
        assertTrue(managerUnsupported.registerConnection("main", DatabaseType.MYSQL, "default").isPresent());
        assertTrue(managerUnsupported.createORMContext("main", String.class).isEmpty());

        DataProviderAPI apiNullDs = mock(DataProviderAPI.class);
        DatabaseProvider providerNullDs = mock(DatabaseProvider.class);
        when(providerNullDs.isConnected()).thenReturn(true);
        when(providerNullDs.getDataSourceOptional()).thenReturn(Optional.empty());
        when(apiNullDs.registerDatabaseOptional(DatabaseType.MYSQL, "default")).thenReturn(Optional.of(providerNullDs));

        FeatureDataManager managerNullDs = new FeatureDataManager(plugin, apiNullDs);
        managerNullDs.initDataProvider("Queue");
        assertTrue(managerNullDs.registerConnection("main", DatabaseType.MYSQL, "default").isPresent());
        assertTrue(managerNullDs.createORMContext("main", String.class).isEmpty());
    }

    @Test
    void createOrmContextSuccessPathStoresCreatedContext() {
        DataProviderAPI api = mock(DataProviderAPI.class);
        DatabaseProvider provider = mock(DatabaseProvider.class);
        DataSource dataSource = mock(DataSource.class);
        ORMContext orm = mock(ORMContext.class);

        when(provider.isConnected()).thenReturn(true);
        when(provider.getDataSourceOptional()).thenReturn(Optional.of(dataSource));
        when(api.registerDatabaseOptional(DatabaseType.MYSQL, "default")).thenReturn(Optional.of(provider));

        TestableFeatureDataManager manager = new TestableFeatureDataManager(plugin, api, orm);
        manager.initDataProvider("Queue");
        manager.registerConnection("main", DatabaseType.MYSQL, "default");

        Optional<ORMContext> created = manager.createORMContext("main", String.class);
        assertTrue(created.isPresent());
        assertSame(orm, created.get());
        assertTrue(manager.getORMContext().isPresent());
        assertEquals("Queue", manager.lastOwnerName);
        assertSame(dataSource, manager.lastDataSource);
    }

    @Test
    void closeAllConnectionsShutsDownOrmAndUnregistersDatabases() {
        DataProviderAPI api = mock(DataProviderAPI.class);
        DatabaseProvider provider = mock(DatabaseProvider.class);
        DataSource dataSource = mock(DataSource.class);
        ORMContext orm = mock(ORMContext.class);

        when(provider.isConnected()).thenReturn(true);
        when(provider.getDataSourceOptional()).thenReturn(Optional.of(dataSource));
        when(api.registerDatabaseOptional(DatabaseType.MYSQL, "default")).thenReturn(Optional.of(provider));

        TestableFeatureDataManager manager = new TestableFeatureDataManager(plugin, api, orm);
        manager.initDataProvider("Queue");
        manager.registerConnection("main", DatabaseType.MYSQL, "default");
        manager.createORMContext("main", String.class);

        manager.closeAllConnections();

        verify(orm).shutdown();
        verify(api).unregisterDatabase(DatabaseType.MYSQL, "default");
        assertEquals(0, manager.getActiveConnCount());
        assertTrue(manager.getORMContext().isEmpty());
        assertTrue(manager.registerConnection("main", DatabaseType.MYSQL, "default").isEmpty());
    }

    @Test
    void closeAllConnectionsContinuesWhenUnregisterThrows() {
        DataProviderAPI api = mock(DataProviderAPI.class);
        DatabaseProvider provider = mock(DatabaseProvider.class);
        DataSource dataSource = mock(DataSource.class);
        ORMContext orm = mock(ORMContext.class);

        when(provider.isConnected()).thenReturn(true);
        when(provider.getDataSourceOptional()).thenReturn(Optional.of(dataSource));
        when(api.registerDatabaseOptional(DatabaseType.MYSQL, "default")).thenReturn(Optional.of(provider));
        doThrow(new RuntimeException("boom")).when(api).unregisterDatabase(DatabaseType.MYSQL, "default");

        TestableFeatureDataManager manager = new TestableFeatureDataManager(plugin, api, orm);
        manager.initDataProvider("Queue");
        manager.registerConnection("main", DatabaseType.MYSQL, "default");
        manager.createORMContext("main", String.class);

        assertDoesNotThrow(manager::closeAllConnections);
        verify(orm).shutdown();
        verify(api).unregisterDatabase(DatabaseType.MYSQL, "default");
        assertEquals(0, manager.getActiveConnCount());
        assertTrue(manager.getORMContext().isEmpty());
    }

    @Test
    void defaultConstructorUsesVelocityDataProviderAndOrmFactoryPathIsReachable() {
        DataProviderAPI api = mock(DataProviderAPI.class);
        DatabaseProvider provider = mock(DatabaseProvider.class);
        DataSource dataSource = mock(DataSource.class);
        DataProviderApiSupplier supplier = mock(DataProviderApiSupplier.class);
        PluginContainer container = mock(PluginContainer.class);
        when(provider.isConnected()).thenReturn(true);
        when(provider.getDataSource()).thenReturn(dataSource);
        when(api.registerDatabaseOptional(DatabaseType.MYSQL, "default")).thenReturn(Optional.of(provider));
        when(pluginManager.getPlugin("dataprovider")).thenReturn(Optional.of(container));
        doReturn(Optional.of(supplier)).when(container).getInstance();
        when(supplier.dataProviderApi()).thenReturn(api);
        when(config.getGlobalSetting("dataprovider_orm_schema_mode", String.class, "validate")).thenReturn(" update ");

        FeatureDataManager manager = new FeatureDataManager(plugin);
        manager.initDataProvider("Queue");
        assertTrue(manager.registerConnection("main", DatabaseType.MYSQL, "default").isPresent());

        AtomicReference<MockedConstruction.Context> capturedContext = new AtomicReference<>();
        try (MockedConstruction<ORMContext> constructed = mockConstruction(
                ORMContext.class,
                (mock, context) -> capturedContext.set(context)
        )) {
            ORMContext context = manager.newOrmContext("Queue", dataSource, String.class);
            assertNotNull(context);
            assertEquals(1, constructed.constructed().size());
            assertNotNull(capturedContext.get());
            assertEquals("Queue", capturedContext.get().arguments().get(0));
            assertSame(dataSource, capturedContext.get().arguments().get(1));
            assertEquals("update", capturedContext.get().arguments().get(3));
        }
    }

    @Test
    void defaultConstructorHandlesVelocityApiLookupFailure() {
        DataProviderApiSupplier supplier = mock(DataProviderApiSupplier.class);
        PluginContainer container = mock(PluginContainer.class);
        when(pluginManager.getPlugin("dataprovider")).thenReturn(Optional.of(container));
        doReturn(Optional.of(supplier)).when(container).getInstance();
        when(supplier.dataProviderApi()).thenThrow(new RuntimeException("boom"));

        FeatureDataManager manager = new FeatureDataManager(plugin);
        manager.initDataProvider("Queue");
        assertTrue(manager.registerConnection("main", DatabaseType.MYSQL, "default").isEmpty());
    }

    private static final class TestableFeatureDataManager extends FeatureDataManager {
        private final ORMContext orm;
        private String lastOwnerName;
        private DataSource lastDataSource;

        private TestableFeatureDataManager(ProxyFeatures plugin, DataProviderAPI dataProviderAPI, ORMContext orm) {
            super(plugin, dataProviderAPI);
            this.orm = orm;
        }

        @Override
        ORMContext newOrmContext(String ownerName, DataSource dataSource, Class<?>... entityClasses) {
            this.lastOwnerName = ownerName;
            this.lastDataSource = dataSource;
            return orm;
        }
    }
}
