package nl.hauntedmc.proxyfeatures.features;

import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.proxyfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.proxyfeatures.features.commandhider.meta.Meta;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FeatureFactoryTest {

    @Test
    void factoryClassCanBeInstantiated() {
        assertNotNull(new FeatureFactory());
    }

    @Test
    void createFeatureReturnsNullAndLogsWhenClassIsMissing() {
        ProxyFeatures plugin = mock(ProxyFeatures.class);
        ComponentLogger logger = mock(ComponentLogger.class);
        when(plugin.getLogger()).thenReturn(logger);

        VelocityBaseFeature<?> created = FeatureFactory.createFeature(null, plugin);
        assertNull(created);
        verify(logger).error(contains("no class was registered"));
    }

    @Test
    void createFeatureReturnsNullWhenConstructorSignatureDoesNotMatch() {
        ProxyFeatures plugin = mock(ProxyFeatures.class);
        ComponentLogger logger = mock(ComponentLogger.class);
        when(plugin.getLogger()).thenReturn(logger);

        VelocityBaseFeature<?> created = FeatureFactory.createFeature(NoProxyCtorFeature.class, plugin);
        assertNull(created);
        verify(logger).error(contains("Failed to instantiate feature"), any(), any());
    }

    @Test
    void createFeatureInstantiatesFeatureWhenConstructorMatches(@TempDir Path tempDir) {
        ProxyFeatures plugin = mock(ProxyFeatures.class);
        ComponentLogger logger = mock(ComponentLogger.class);
        when(plugin.getLogger()).thenReturn(logger);
        when(plugin.getDataDirectory()).thenReturn(tempDir);

        VelocityBaseFeature<?> created = FeatureFactory.createFeature(ValidCtorFeature.class, plugin);
        assertNotNull(created);
        assertInstanceOf(ValidCtorFeature.class, created);
    }

    private static final class NoProxyCtorFeature extends VelocityBaseFeature<Meta> {
        NoProxyCtorFeature() {
            super(null, new Meta());
        }

        @Override
        public ConfigMap getDefaultConfig() {
            return new ConfigMap();
        }

        @Override
        public MessageMap getDefaultMessages() {
            return new MessageMap();
        }

        @Override
        public void initialize() {
        }

        @Override
        public void disable() {
        }
    }

    private static final class ValidCtorFeature extends VelocityBaseFeature<Meta> {
        ValidCtorFeature(ProxyFeatures plugin) {
            super(plugin, new Meta());
        }

        @Override
        public ConfigMap getDefaultConfig() {
            return new ConfigMap();
        }

        @Override
        public MessageMap getDefaultMessages() {
            return new MessageMap();
        }

        @Override
        public void initialize() {
        }

        @Override
        public void disable() {
        }
    }
}
