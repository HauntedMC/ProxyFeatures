package nl.hauntedmc.proxyfeatures.framework.lifecycle;

import nl.hauntedmc.dataprovider.api.DataProviderAPI;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataprovider.database.DataAccess;
import nl.hauntedmc.dataprovider.database.DatabaseProvider;
import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.dataprovider.platform.common.logger.ILoggerAdapter;
import nl.hauntedmc.dataprovider.platform.velocity.VelocityDataProvider;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;

import javax.sql.DataSource;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class FeatureDataManager {
    private static final String ORM_SCHEMA_MODE_CONFIG_KEY = "dataprovider_orm_schema_mode";
    private static final String DEFAULT_ORM_SCHEMA_MODE = "validate";

    private final ProxyFeatures plugin;
    private final DataProviderAPI dataProviderAPI;
    private final ILoggerAdapter ormLogger;
    private final ConcurrentHashMap<String, ConnectionRegistration> connectionsByIdentifier = new ConcurrentHashMap<>();

    private ORMContext ormContext;
    private String featureName;
    private boolean initialized;

    public FeatureDataManager(ProxyFeatures plugin) {
        this(plugin, resolveApiSafely(plugin));
    }

    FeatureDataManager(ProxyFeatures plugin, DataProviderAPI dataProviderAPI) {
        this.plugin = plugin;
        this.dataProviderAPI = dataProviderAPI;
        this.ormLogger = createOrmLoggerAdapter(plugin);
    }

    public void initDataProvider(String featureName) {
        this.featureName = featureName;
        this.initialized = false;

        if (featureName == null || featureName.isBlank()) {
            plugin.getLogger().error("Feature name cannot be null or blank.");
            return;
        }

        if (dataProviderAPI == null) {
            plugin.getLogger().error("DataProviderAPI is not available for feature '{}'.", featureName);
            return;
        }

        initialized = true;
        plugin.getLogger().info(
            "DataProvider initialized for feature '{}'. Caller identity is resolved automatically by DataProvider.",
            featureName
        );
    }

    public Optional<DatabaseProvider> registerConnection(String identifier, DatabaseType databaseType, String connectionName) {
        if (!isReady()) {
            return Optional.empty();
        }

        ConnectionRegistration existing = connectionsByIdentifier.get(identifier);
        if (existing != null) {
            if (existing.databaseType == databaseType
                    && existing.connectionName.equals(connectionName)
                    && isProviderConnected(existing.provider)) {
                return Optional.of(existing.provider);
            }
            releaseConnection(existing, identifier);
            connectionsByIdentifier.remove(identifier, existing);
        }

        Optional<DatabaseProvider> registered;
        try {
            registered = dataProviderAPI.registerDatabaseOptional(databaseType, connectionName);
        } catch (Exception ex) {
            plugin.getLogger().error(
                "Failed to register database '{}' (type={}, connection='{}') for feature '{}'.",
                identifier,
                databaseType,
                connectionName,
                featureName,
                ex
            );
            return Optional.empty();
        }

        if (registered.isEmpty() || !isProviderConnected(registered.get())) {
            plugin.getLogger().error(
                    "Database provider '{}' is null or not connected (type={}, connection='{}').",
                    identifier,
                    databaseType,
                    connectionName
            );
            return Optional.empty();
        }

        DatabaseProvider provider = registered.get();
        ConnectionRegistration newRegistration = new ConnectionRegistration(databaseType, connectionName, provider);
        ConnectionRegistration replaced = connectionsByIdentifier.put(identifier, newRegistration);
        if (replaced != null && replaced != newRegistration) {
            releaseConnection(replaced, identifier);
        }

        plugin.getLogger().info("Successfully registered connection '{}' of type {}", identifier, databaseType);
        return Optional.of(provider);
    }

    public Optional<DatabaseProvider> getDataProvider(String identifier) {
        ConnectionRegistration registration = connectionsByIdentifier.get(identifier);
        if (registration == null) {
            return Optional.empty();
        }
        return Optional.of(registration.provider);
    }

    public <T extends DataAccess> Optional<T> registerDataAccess(
            String identifier,
            DatabaseType databaseType,
            String connectionName,
            Class<T> expectedDataAccessType
    ) {
        return registerConnection(identifier, databaseType, connectionName)
                .flatMap(provider -> {
                    Optional<T> dataAccess = provider.getDataAccessOptional(expectedDataAccessType);
                    if (dataAccess.isEmpty()) {
                        plugin.getLogger().error(
                                "Connection '{}' is not compatible with expected data access type {}.",
                                identifier,
                                expectedDataAccessType.getSimpleName()
                        );
                    }
                    return dataAccess;
                });
    }

    public <T extends DataAccess> Optional<T> getDataAccess(String identifier, Class<T> expectedDataAccessType) {
        return getDataProvider(identifier).flatMap(provider -> provider.getDataAccessOptional(expectedDataAccessType));
    }

    public Optional<ORMContext> createORMContext(String identifier, Class<?>... entityClasses) {
        Optional<DatabaseProvider> providerOptional = getDataProvider(identifier);
        if (providerOptional.isEmpty()) {
            plugin.getLogger().error("Could not find database provider for identifier: {}", identifier);
            return Optional.empty();
        }
        DatabaseProvider provider = providerOptional.get();

        Optional<DataSource> dataSource = provider.getDataSourceOptional();
        if (dataSource.isEmpty()) {
            plugin.getLogger().error(
                    "Database '{}' does not expose a DataSource. ORMContext requires a relational provider.",
                    identifier
            );
            return Optional.empty();
        }

        try {
            String ownerName = (featureName == null || featureName.isBlank())
                ? plugin.getClass().getSimpleName()
                : featureName;
            this.ormContext = newOrmContext(ownerName, dataSource.get(), entityClasses);
            plugin.getLogger().info("Created ORMContext for identifier '{}'", identifier);
            return Optional.of(ormContext);
        } catch (Exception ex) {
            plugin.getLogger().error("Failed to create ORMContext for identifier '{}'.", identifier, ex);
            return Optional.empty();
        }
    }

    public Optional<ORMContext> getORMContext() {
        return Optional.ofNullable(ormContext);
    }

    public void closeAllConnections() {
        if (ormContext != null) {
            ormContext.shutdown();
            plugin.getLogger().info("ORMContext has been shut down.");
        }

        if (dataProviderAPI != null) {
            for (var entry : connectionsByIdentifier.entrySet()) {
                releaseConnection(entry.getValue(), entry.getKey());
            }
        }

        connectionsByIdentifier.clear();
        ormContext = null;
        initialized = false;
    }

    public int getActiveConnCount() {
        return connectionsByIdentifier.size();
    }

    ORMContext newOrmContext(String ownerName, DataSource dataSource, Class<?>... entityClasses) {
        return new ORMContext(ownerName, dataSource, ormLogger, resolveOrmSchemaMode(), entityClasses);
    }

    private String resolveOrmSchemaMode() {
        if (plugin.getConfigHandler() != null) {
            String configured = plugin.getConfigHandler().getGlobalSetting(
                ORM_SCHEMA_MODE_CONFIG_KEY,
                String.class,
                DEFAULT_ORM_SCHEMA_MODE
            );
            if (configured != null && !configured.isBlank()) {
                return configured.trim();
            }
        }
        return DEFAULT_ORM_SCHEMA_MODE;
    }

    private static ILoggerAdapter createOrmLoggerAdapter(ProxyFeatures plugin) {
        return new ILoggerAdapter() {
            @Override
            public void info(String message) {
                plugin.getLogger().info(message);
            }

            @Override
            public void warn(String message) {
                plugin.getLogger().warn(message);
            }

            @Override
            public void error(String message) {
                plugin.getLogger().error(message);
            }

            @Override
            public void info(String message, Throwable throwable) {
                plugin.getLogger().info(message, throwable);
            }

            @Override
            public void warn(String message, Throwable throwable) {
                plugin.getLogger().warn(message, throwable);
            }

            @Override
            public void error(String message, Throwable throwable) {
                plugin.getLogger().error(message, throwable);
            }
        };
    }

    private static DataProviderAPI resolveApiSafely(ProxyFeatures plugin) {
        try {
            return VelocityDataProvider.getDataProviderAPI();
        } catch (RuntimeException ex) {
            if (plugin != null) {
                plugin.getLogger().warn("DataProviderAPI unavailable: {}", ex.getMessage());
            }
            return null;
        }
    }

    private boolean isReady() {
        if (featureName == null) {
            plugin.getLogger().error("Feature name is not set. Did you call initDataProvider()?");
            return false;
        }
        if (!initialized) {
            plugin.getLogger().error("DataProvider is not initialized for feature '{}'.", featureName);
            return false;
        }
        return true;
    }

    private boolean isProviderConnected(DatabaseProvider provider) {
        if (provider == null) {
            return false;
        }
        try {
            return provider.isConnected();
        } catch (Exception ex) {
            return false;
        }
    }

    private void releaseConnection(ConnectionRegistration registration, String identifier) {
        if (registration == null || dataProviderAPI == null) {
            return;
        }
        try {
            dataProviderAPI.unregisterDatabase(registration.databaseType, registration.connectionName);
        } catch (Exception ex) {
            plugin.getLogger().error(
                    "Failed to unregister connection '{}' (type={}, connection='{}').",
                    identifier,
                    registration.databaseType,
                    registration.connectionName,
                    ex
            );
        }
    }

    private record ConnectionRegistration(
            DatabaseType databaseType,
            String connectionName,
            DatabaseProvider provider
    ) {
    }
}
