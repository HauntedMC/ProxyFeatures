package nl.hauntedmc.proxyfeatures.framework.loader.dependency;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DependencyCheckResultTest {

    @Test
    void okIsTrueOnlyWhenBothSetsAreEmpty() {
        assertTrue(new DependencyCheckResult(Set.of(), Set.of()).ok());
        assertFalse(new DependencyCheckResult(Set.of("p"), Set.of()).ok());
        assertFalse(new DependencyCheckResult(Set.of(), Set.of("f")).ok());
    }

    @Test
    void constructorDefensivelyCopiesAndMakesSetsUnmodifiable() {
        Set<String> plugins = new LinkedHashSet<>();
        Set<String> features = new LinkedHashSet<>();
        plugins.add("a");
        features.add("b");

        DependencyCheckResult result = new DependencyCheckResult(plugins, features);
        plugins.add("x");
        features.add("y");

        assertEquals(Set.of("a"), result.missingPluginDependencies());
        assertEquals(Set.of("b"), result.missingFeatureDependencies());
        assertThrows(UnsupportedOperationException.class, () -> result.missingPluginDependencies().add("z"));
        assertThrows(UnsupportedOperationException.class, () -> result.missingFeatureDependencies().add("z"));
    }
}
