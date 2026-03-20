package nl.hauntedmc.proxyfeatures.framework.lifecycle;

import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import nl.hauntedmc.dataprovider.api.DataProviderAPI;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataprovider.database.DatabaseProvider;
import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.dataprovider.platform.velocity.VelocityDataProvider;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.framework.config.MainConfigHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.util.Optional;
import java.util.function.Function;
import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FeatureDataManagerTest {

    private ProxyFeatures plugin;
    private ComponentLogger logger;
    private MainConfigHandler config;

    @BeforeEach
    void setUp() {
        plugin = mock(ProxyFeatures.class);
        logger = mock(ComponentLogger.class);
        config = mock(MainConfigHandler.class);
        when(plugin.getLogger()).thenReturn(logger);
        when(plugin.getConfigHandler()).thenReturn(config);
        when(config.getGlobalSetting(anyString(), eq(String.class), anyString())).thenReturn("");
    }

    @Test
    void registerConnectionBeforeInitReturnsEmpty() {
        DataProviderAPI api = mock(DataProviderAPI.class);
        FeatureDataManager manager = new FeatureDataManager(plugin, api, key -> "");

        Optional<DatabaseProvider> result = manager.registerConnection("main", DatabaseType.MYSQL, "default");
        assertTrue(result.isEmpty());
    }

    @Test
    void initDataProviderFailsWhenApiUnavailable() {
        FeatureDataManager manager = new FeatureDataManager(plugin, null, key -> "token");
        manager.initDataProvider("Queue");

        Optional<DatabaseProvider> result = manager.registerConnection("main", DatabaseType.MYSQL, "default");
        assertTrue(result.isEmpty());
    }

    @Test
    void initDataProviderUsesEnvTokenAndRegistersConnectedProvider() {
        DataProviderAPI api = mock(DataProviderAPI.class);
        DatabaseProvider provider = mock(DatabaseProvider.class);
        when(provider.isConnected()).thenReturn(true);
        when(api.registerDatabase("Queue", DatabaseType.MYSQL, "default")).thenReturn(provider);

        FeatureDataManager manager = new FeatureDataManager(plugin, api, key -> "  token  ");
        manager.initDataProvider("Queue");

        Optional<DatabaseProvider> registered = manager.registerConnection("main", DatabaseType.MYSQL, "default");
        assertTrue(registered.isPresent());
        assertEquals(1, manager.getActiveConnCount());
        assertTrue(manager.getDataProvider("main").isPresent());
        verify(api).authenticate("Queue", "token");
    }

    @Test
    void initDataProviderFallsBackToConfigToken() {
        DataProviderAPI api = mock(DataProviderAPI.class);
        when(config.getGlobalSetting("dataprovider_token", String.class, "")).thenReturn("  cfg-token ");

        FeatureDataManager manager = new FeatureDataManager(plugin, api, key -> "");
        manager.initDataProvider("Queue");

        verify(api).authenticate("Queue", "cfg-token");
    }

    @Test
    void initDataProviderHandlesMissingTokenAndAuthenticationFailure() {
        DataProviderAPI api = mock(DataProviderAPI.class);
        FeatureDataManager managerMissing = new FeatureDataManager(plugin, api, key -> "");
        managerMissing.initDataProvider("Queue");
        verify(api, never()).authenticate(anyString(), anyString());

        DataProviderAPI failingApi = mock(DataProviderAPI.class);
        doThrow(new RuntimeException("boom")).when(failingApi).authenticate("Queue", "env-token");
        FeatureDataManager managerFailing = new FeatureDataManager(plugin, failingApi, key -> "env-token");
        managerFailing.initDataProvider("Queue");
        Optional<DatabaseProvider> result = managerFailing.registerConnection("main", DatabaseType.MYSQL, "default");
        assertTrue(result.isEmpty());
    }

    @Test
    void registerConnectionHandlesNullAndDisconnectedProviders() {
        DataProviderAPI api = mock(DataProviderAPI.class);
        FeatureDataManager manager = new FeatureDataManager(plugin, api, key -> "token");
        manager.initDataProvider("Queue");

        when(api.registerDatabase("Queue", DatabaseType.MYSQL, "default")).thenReturn(null);
        assertTrue(manager.registerConnection("main", DatabaseType.MYSQL, "default").isEmpty());

        DatabaseProvider disconnected = mock(DatabaseProvider.class);
        when(disconnected.isConnected()).thenReturn(false);
        when(api.registerDatabase("Queue", DatabaseType.MYSQL, "default")).thenReturn(disconnected);
        assertTrue(manager.registerConnection("main", DatabaseType.MYSQL, "default").isEmpty());
    }

    @Test
    void createOrmContextReturnsEmptyWhenProviderMissingAndSuccessWhenPresent() {
        DataProviderAPI api = mock(DataProviderAPI.class);
        DatabaseProvider provider = mock(DatabaseProvider.class);
        when(provider.isConnected()).thenReturn(true);
        when(api.registerDatabase("Queue", DatabaseType.MYSQL, "default")).thenReturn(provider);
        ORMContext orm = mock(ORMContext.class);

        TestableFeatureDataManager manager = new TestableFeatureDataManager(plugin, api, key -> "token", orm);
        manager.initDataProvider("Queue");

        assertTrue(manager.createORMContext("missing", String.class).isEmpty());

        manager.registerConnection("main", DatabaseType.MYSQL, "default");
        Optional<ORMContext> created = manager.createORMContext("main", String.class);
        assertTrue(created.isPresent());
        assertSame(orm, created.get());
        assertTrue(manager.getORMContext().isPresent());
    }

    @Test
    void closeAllConnectionsShutsDownOrmAndUnregistersDatabases() {
        DataProviderAPI api = mock(DataProviderAPI.class);
        DatabaseProvider provider = mock(DatabaseProvider.class);
        when(provider.isConnected()).thenReturn(true);
        when(api.registerDatabase("Queue", DatabaseType.MYSQL, "default")).thenReturn(provider);
        ORMContext orm = mock(ORMContext.class);

        TestableFeatureDataManager manager = new TestableFeatureDataManager(plugin, api, key -> "token", orm);
        manager.initDataProvider("Queue");
        manager.registerConnection("main", DatabaseType.MYSQL, "default");
        manager.createORMContext("main", String.class);

        manager.closeAllConnections();

        verify(orm).shutdown();
        verify(api).unregisterAllDatabases("Queue");
        assertEquals(0, manager.getActiveConnCount());
        assertTrue(manager.getORMContext().isEmpty());
    }

    @Test
    void closeAllConnectionsWithoutApiStillClearsState() {
        ORMContext orm = mock(ORMContext.class);
        TestableFeatureDataManager manager = new TestableFeatureDataManager(plugin, null, key -> "", orm);
        manager.initDataProvider("Queue");
        manager.closeAllConnections();
        verify(orm, never()).shutdown();
        assertEquals(0, manager.getActiveConnCount());
    }

    @Test
    void defaultConstructorUsesVelocityDataProviderAndRealOrmFactoryPathIsReachable() {
        DataProviderAPI api = mock(DataProviderAPI.class);
        when(config.getGlobalSetting("dataprovider_token", String.class, "")).thenReturn("cfg-token");

        try (MockedStatic<VelocityDataProvider> mocked = mockStatic(VelocityDataProvider.class)) {
            mocked.when(VelocityDataProvider::getDataProviderAPI).thenReturn(api);

            FeatureDataManager manager = new FeatureDataManager(plugin);
            manager.initDataProvider("Queue");
            verify(api).authenticate("Queue", "cfg-token");

            DatabaseProvider provider = mock(DatabaseProvider.class);
            DataSource dataSource = mock(DataSource.class);
            when(provider.getDataSource()).thenReturn(dataSource);
            try (MockedConstruction<ORMContext> ignored = mockConstruction(ORMContext.class)) {
                ORMContext context = manager.newOrmContext("Queue", provider, String.class);
                assertNotNull(context);
            }
        }
    }

    @Test
    void defaultConstructorHandlesVelocityApiLookupFailure() {
        try (MockedStatic<VelocityDataProvider> mocked = mockStatic(VelocityDataProvider.class)) {
            mocked.when(VelocityDataProvider::getDataProviderAPI).thenThrow(new RuntimeException("boom"));
            FeatureDataManager manager = new FeatureDataManager(plugin);
            manager.initDataProvider("Queue");
            assertTrue(manager.registerConnection("main", DatabaseType.MYSQL, "default").isEmpty());
        }
    }

    private static final class TestableFeatureDataManager extends FeatureDataManager {
        private final ORMContext orm;

        private TestableFeatureDataManager(ProxyFeatures plugin,
                                           DataProviderAPI dataProviderAPI,
                                           Function<String, String> envLookup,
                                           ORMContext orm) {
            super(plugin, dataProviderAPI, envLookup);
            this.orm = orm;
        }

        @Override
        ORMContext newOrmContext(String featureName, DatabaseProvider provider, Class<?>... entityClasses) {
            return orm;
        }
    }
}
