package nl.hauntedmc.proxyfeatures.api.feature.meta;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BaseMetaTest {

    @Test
    void defaultDependencyMethodsReturnEmptyLists() {
        BaseMeta meta = new BaseMeta() {
            @Override
            public String getFeatureName() {
                return "Queue";
            }

            @Override
            public String getFeatureVersion() {
                return "1.0";
            }
        };

        assertEquals(List.of(), meta.getDependencies());
        assertEquals(List.of(), meta.getPluginDependencies());
    }
}
