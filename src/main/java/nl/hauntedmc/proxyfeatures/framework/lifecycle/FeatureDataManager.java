package nl.hauntedmc.proxyfeatures.framework.lifecycle;

import nl.hauntedmc.dataprovider.api.DataProviderAPI;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
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
    private final ConcurrentHashMap<String, DatabaseProvider> databaseProviders = new ConcurrentHashMap<>();

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
        if (featureName == null) {
            plugin.getLogger().error("Feature name is not set. Did you call initDataProvider()?");
            return Optional.empty();
        }
        if (!initialized) {
            plugin.getLogger().error("DataProvider is not initialized for feature '{}'.", featureName);
            return Optional.empty();
        }

        final DatabaseProvider provider;
        try {
            provider = dataProviderAPI.registerDatabase(databaseType, connectionName);
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

        if (provider == null || !provider.isConnected()) {
            plugin.getLogger().error(
                "Database provider '{}' is null or not connected (type={}, connection='{}').",
                identifier,
                databaseType,
                connectionName
            );
            return Optional.empty();
        }

        databaseProviders.put(identifier, provider);
        plugin.getLogger().info("Successfully registered connection '{}' of type {}", identifier, databaseType);
        return Optional.of(provider);
    }

    public Optional<DatabaseProvider> getDataProvider(String identifier) {
        return Optional.ofNullable(databaseProviders.get(identifier));
    }

    public Optional<ORMContext> createORMContext(String identifier, Class<?>... entityClasses) {
        DatabaseProvider provider = databaseProviders.get(identifier);
        if (provider == null) {
            plugin.getLogger().error("Could not find database provider for identifier: {}", identifier);
            return Optional.empty();
        }

        final DataSource dataSource;
        try {
            dataSource = provider.getDataSource();
        } catch (UnsupportedOperationException ex) {
            plugin.getLogger().error(
                "Database '{}' does not expose a DataSource. ORMContext requires a relational provider.",
                identifier,
                ex
            );
            return Optional.empty();
        }

        if (dataSource == null) {
            plugin.getLogger().error("Database '{}' returned a null DataSource.", identifier);
            return Optional.empty();
        }

        try {
            String ownerName = (featureName == null || featureName.isBlank())
                ? plugin.getClass().getSimpleName()
                : featureName;
            this.ormContext = newOrmContext(ownerName, dataSource, entityClasses);
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
            try {
                dataProviderAPI.unregisterAllDatabases();
                plugin.getLogger().info("Unregistered all DataProvider databases for this plugin context.");
            } catch (Exception ex) {
                plugin.getLogger().error("Failed to unregister DataProvider databases.", ex);
            }
        }

        databaseProviders.clear();
        ormContext = null;
        initialized = false;
    }

    public int getActiveConnCount() {
        return databaseProviders.size();
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
}
