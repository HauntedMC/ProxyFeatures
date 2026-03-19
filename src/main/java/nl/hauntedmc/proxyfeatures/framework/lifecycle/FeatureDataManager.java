package nl.hauntedmc.proxyfeatures.framework.lifecycle;

import nl.hauntedmc.dataprovider.api.DataProviderAPI;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataprovider.database.DatabaseProvider;
import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.dataprovider.platform.velocity.VelocityDataProvider;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class FeatureDataManager {
    private static final String DATA_PROVIDER_TOKEN_ENV = "PROXYFEATURES_DATAPROVIDER_TOKEN";
    private static final String DATA_PROVIDER_TOKEN_CONFIG_KEY = "dataprovider_token";

    private final ProxyFeatures plugin;
    private final DataProviderAPI dataProviderAPI;
    private ORMContext ormContext;
    private String featureName;
    private boolean authenticated;
    private final ConcurrentHashMap<String, DatabaseProvider> databaseProviders;


    /**
     * Constructs a new FeatureDataManager.
     *
     * @param plugin your main plugin class (for logging, etc.)
     */
    public FeatureDataManager(ProxyFeatures plugin) {
        dataProviderAPI = VelocityDataProvider.getDataProviderAPI();
        databaseProviders = new ConcurrentHashMap<>();
        this.plugin = plugin;
    }

    /**
     * Authenticates your feature with the DataProviderAPI using the provided token.
     *
     * @param featureName the name of the feature
     */
    public void initDataProvider(String featureName) {
        this.featureName = featureName;
        this.authenticated = false;

        String token = resolveDataProviderToken();
        if (token == null) {
            plugin.getLogger().error(
                    "DataProvider token missing for feature '{}'. Set env {} or config key global.{}.",
                    featureName,
                    DATA_PROVIDER_TOKEN_ENV,
                    DATA_PROVIDER_TOKEN_CONFIG_KEY
            );
            return;
        }

        try {
            dataProviderAPI.authenticate(featureName, token);
            authenticated = true;
            plugin.getLogger().info("DataProvider authenticated feature '{}'.", featureName);
        } catch (Throwable t) {
            plugin.getLogger().error("Failed to authenticate DataProvider for feature '{}'.", featureName, t);
        }
    }

    /**
     * Registers a connection (DatabaseProvider) for the given identifier, using the specified
     * database type and connection name.
     *
     * @param identifier     a label for the provider
     * @param databaseType   the type of database (e.g. MYSQL, MONGODB, etc.)
     * @param connectionName the name or key used to differentiate the database config
     * @return an Optional containing the DatabaseProvider if registration was successful; empty otherwise
     */
    public Optional<DatabaseProvider> registerConnection(String identifier, DatabaseType databaseType, String connectionName) {

        if (featureName == null) {
            plugin.getLogger().error("Feature name is not set. Did you call initDataProvider()?");
            return Optional.empty();
        }
        if (!authenticated) {
            plugin.getLogger().error("DataProvider is not authenticated for feature '{}'.", featureName);
            return Optional.empty();
        }

        DatabaseProvider provider = dataProviderAPI.registerDatabase(featureName, databaseType, connectionName);
        if (provider == null || !provider.isConnected()) {
            plugin.getLogger().error("Database Provider is not connected.");
            return Optional.empty();
        }
        databaseProviders.put(identifier, provider);
        plugin.getLogger().info("Successfully registered connection '{}' of type {}", identifier, databaseType);
        return Optional.of(provider);
    }

    /**
     * Retrieves the DatabaseProvider associated with the given identifier.
     *
     * @param identifier the key used to register the provider
     * @return an Optional containing the DatabaseProvider, or empty if none is found
     */
    public Optional<DatabaseProvider> getDataProvider(String identifier) {
        return Optional.ofNullable(databaseProviders.get(identifier));
    }


    /**
     * Creates an ORM context for the specified database identifier and entity classes.
     * This overwrites any previously-created single ORM context in this manager.
     *
     * @param identifier    the database connection identifier
     * @param entityClasses the entity classes you want to manage with ORM
     * @return an Optional containing the newly created ORMContext, or empty if creation fails
     */
    public Optional<ORMContext> createORMContext(String identifier, Class<?>... entityClasses) {
        DatabaseProvider provider = databaseProviders.get(identifier);
        if (provider == null) {
            plugin.getLogger().error("Could not find database provider for identifier: {}", identifier);
            return Optional.empty();
        }

        // Create and store the ORMContext
        this.ormContext = new ORMContext(featureName, provider.getDataSource(), entityClasses);
        plugin.getLogger().info("Created ORMContext for identifier '{}'", identifier);
        return Optional.of(ormContext);
    }


    /**
     * Retrieves the single ORMContext that this manager holds, if any.
     *
     * @return an Optional of the current ORMContext
     */
    public Optional<ORMContext> getORMContext() {
        return Optional.ofNullable(ormContext);
    }


    /**
     * Closes the current ORMContext (if any) and unregisters all databases associated with
     * this feature name.
     */
    public void closeAllConnections() {
        if (ormContext != null) {
            ormContext.shutdown();
            plugin.getLogger().info("ORMContext has been shut down.");
        }

        if (!databaseProviders.isEmpty() && featureName != null) {
            dataProviderAPI.unregisterAllDatabases(featureName);
            plugin.getLogger().info("Unregistered all databases for feature '{}'.", featureName);
        }

        databaseProviders.clear();
        ormContext = null;
        authenticated = false;
    }

    /**
     * @return the current count of active connections
     */
    public int getActiveConnCount() {
        return databaseProviders.size();
    }

    private String resolveDataProviderToken() {
        String env = System.getenv(DATA_PROVIDER_TOKEN_ENV);
        if (env != null && !env.isBlank()) {
            return env.trim();
        }

        if (plugin.getConfigHandler() != null) {
            String cfg = plugin.getConfigHandler().getGlobalSetting(DATA_PROVIDER_TOKEN_CONFIG_KEY, String.class, "");
            if (cfg != null && !cfg.isBlank()) {
                return cfg.trim();
            }
        }

        return null;
    }
}
