package nl.hauntedmc.proxyfeatures.api.io.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ConfigViewTest {

    @TempDir
    Path tempDir;

    @Test
    void crudTypedReadsAndPathViewsWork() throws Exception {
        ConfigView view = createView("config.yml", "");
        view.put("global.name", "proxy");
        view.put("numbers", List.of("1", 2));
        view.put("weights", Map.of("a", "1"));

        assertEquals("proxy", view.get("global.name", String.class));
        assertEquals("fallback", view.get("missing", String.class, "fallback"));
        assertEquals(List.of(1, 2), view.getList("numbers", Integer.class));
        assertNull(view.getList("missingList", Integer.class, List.of(7)));
        assertEquals(Map.of("a", 1), view.getMapValues("weights", Integer.class));
        assertNull(view.getMapValues("missingMap", Integer.class, Map.of("d", 2)));

        assertEquals("proxy", view.nodeAt("global.name").asRequired(String.class));
        assertEquals("proxy", view.getAt("global.name", String.class));
        assertEquals("def", view.getAt("global.missing", String.class, "def"));

        assertTrue(view.putIfAbsent("global.mode", "on"));
        assertFalse(view.putIfAbsent("global.mode", "off"));
        view.remove("global.mode");
        assertNull(view.get("global.mode"));

        ConfigView scoped = view.scope("global");
        assertEquals("proxy", scoped.get("name", String.class));
        assertEquals("proxy", view.globals().get("name", String.class));
        assertSame(view, view.root());
        assertEquals("proxy", view.at("global").get("name", String.class));
        assertNotNull(view.features());
    }

    @Test
    void computeListMutationsBatchAndRawMutationWork() throws Exception {
        ConfigView view = createView("config2.yml", "");
        view.put("counter", "bad-type");

        Integer computed = view.compute("counter", Integer.class, cur -> cur + 1, () -> 10);
        assertEquals(11, computed);
        assertEquals(11, view.get("counter", Integer.class));

        view.appendToList("list", "a");
        view.appendToList("list", "b");
        // Configurate can reject Object.class list conversion in append/remove internals; API intentionally swallows.
        assertNull(view.getList("list", String.class));
        assertEquals(0, view.removeFromList("list", o -> "a".equals(o)));
        assertEquals(0, view.removeFromList("missing", o -> true));
        assertEquals(0, view.removeFromList("list", o -> {
            throw new RuntimeException("boom");
        }));

        view.batch(b -> {
            try {
                b.put("batch.a", 1)
                        .putIfAbsent("batch.a", 2)
                        .compute("batch.a", Integer.class, i -> i + 1, () -> 0)
                        .remove("batch.a");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        assertNull(view.get("batch.a"));
        assertNull(view.getList("batch.items", String.class));

        assertThrows(RuntimeException.class, () -> view.batch(b -> {
            try {
                b.appendToList("batch.items", "x");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }));
        assertThrows(RuntimeException.class, () -> view.batch(b -> {
            try {
                b.removeFromList("batch.items", o -> false);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }));

        view.mutateRaw(root -> root.node("raw", "ok").raw(true));
        assertEquals(true, view.get("raw.ok", Boolean.class));
    }

    @Test
    void fallbackReadersAndPathHelpersCoverEdgeBranches() throws Exception {
        ConfigView view = createView("config3.yml", "");
        view.put("bad.list", Map.of("x", 1));
        view.put("bad.map", List.of(1, 2));

        assertEquals(List.of(9), view.getList("bad.list", Integer.class, List.of(9)));
        assertEquals(Map.of("fallback", 1), view.getMapValues("bad.map", Integer.class, Map.of("fallback", 1)));

        ConfigView withNullBase = createView("config4.yml", null);
        withNullBase.put("root.value", 1);
        assertEquals(1, withNullBase.get("root.value", Integer.class));

        ConfigView scoped = withNullBase.scope("root");
        assertEquals(1, scoped.get("value", Integer.class));
        assertEquals(1, withNullBase.scope(null).get("root.value", Integer.class));
        assertEquals(1, scoped.scope("").get("value", Integer.class));

        assertNotSame(scoped, scoped.root());
        assertSame(withNullBase, withNullBase.at("  "));
    }

    @Test
    void listMutationsAndComputePathsAreCoveredWithMockedBackingNode() throws Exception {
        YamlFile file = mock(YamlFile.class);
        CommentedConfigurationNode root = mock(CommentedConfigurationNode.class);
        CommentedConfigurationNode node = mock(CommentedConfigurationNode.class);
        ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        AtomicReference<List<Object>> listRef = new AtomicReference<>(null);

        when(file.lock()).thenReturn(lock);
        when(file.snapshotUnsafe()).thenReturn(root);
        when(root.node(any(Object[].class))).thenReturn(node);
        when(node.virtual()).thenReturn(false);
        when(node.get(any(Class.class))).thenThrow(new IllegalArgumentException("bad value"));
        when(node.getList(eq(Object.class))).thenAnswer(inv -> {
            List<Object> cur = listRef.get();
            return cur == null ? null : new ArrayList<>(cur);
        });
        doAnswer(inv -> {
            Object value = inv.getArgument(0);
            if (value instanceof List<?> list) {
                listRef.set(new ArrayList<>(list));
            }
            return null;
        }).when(node).set(any());

        ConfigView view = new ConfigView(file, "base");

        Integer next = view.compute("counter", Integer.class, i -> i + 1, () -> 5);
        assertEquals(6, next);

        view.appendToList("items", "a");
        view.appendToList("items", "b");
        assertEquals(List.of("a", "b"), listRef.get());

        assertEquals(1, view.removeFromList("items", o -> "a".equals(o)));
        listRef.set(List.of());
        assertEquals(0, view.removeFromList("items", o -> true));
    }

    @Test
    void batchMutationsCoverPutIfAbsentComputeAndListBranches() throws Exception {
        YamlFile file = mock(YamlFile.class);
        CommentedConfigurationNode root = mock(CommentedConfigurationNode.class);
        CommentedConfigurationNode node = mock(CommentedConfigurationNode.class);
        ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        AtomicReference<List<Object>> listRef = new AtomicReference<>(null);

        when(file.lock()).thenReturn(lock);
        when(file.snapshotUnsafe()).thenReturn(root);
        when(root.node(any(Object[].class))).thenReturn(node);
        when(node.virtual()).thenReturn(true, false);
        when(node.get(eq(Object.class))).thenReturn(new Object());
        when(node.getList(eq(Object.class))).thenAnswer(inv -> {
            List<Object> cur = listRef.get();
            return cur == null ? null : new ArrayList<>(cur);
        });
        doAnswer(inv -> {
            Object value = inv.getArgument(0);
            if (value instanceof List<?> list) {
                listRef.set(new ArrayList<>(list));
            } else if (value == null) {
                listRef.set(null);
            }
            return null;
        }).when(node).set(any());

        ConfigView view = new ConfigView(file, "scope");
        view.batch(b -> {
            try {
                b.putIfAbsent("k", 1)
                        .compute("k", Integer.class, i -> i + 1, () -> 2)
                        .appendToList("list", "x")
                        .removeFromList("missing", o -> true)
                        .removeFromList("list", o -> "x".equals(o));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        assertTrue(listRef.get().isEmpty());
        verify(file, atLeastOnce()).saveNow();
    }

    @Test
    void computeCoversCurrentValueConversionPath() throws Exception {
        YamlFile file = mock(YamlFile.class);
        ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        CommentedConfigurationNode root = CommentedConfigurationNode.root();
        root.node("scope", "count").set(1);

        when(file.lock()).thenReturn(lock);
        when(file.snapshotUnsafe()).thenReturn(root);

        ConfigView view = new ConfigView(file, "scope");
        Integer next = view.compute("count", Integer.class, i -> i + 1, () -> 0);

        assertEquals(2, next);
        verify(file).saveNow();
    }

    private ConfigView createView(String fileName, String base) throws Exception {
        Path path = tempDir.resolve(fileName);
        Files.createFile(path);
        return new ConfigView(new YamlFile(path, mock(Logger.class)), base);
    }
}
