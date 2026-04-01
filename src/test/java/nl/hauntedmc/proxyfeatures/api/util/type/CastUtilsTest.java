package nl.hauntedmc.proxyfeatures.api.util.type;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CastUtilsTest {

    @Test
    void safeCastToListReturnsTypedListWhenAllItemsMatch() {
        List<String> result = CastUtils.safeCastToList(List.of("a", "b"), String.class);
        assertEquals(List.of("a", "b"), result);
    }

    @Test
    void safeCastToListThrowsWhenItemTypeMismatches() {
        assertThrows(ClassCastException.class, () -> CastUtils.safeCastToList(List.of("a", 1), String.class));
    }

    @Test
    void safeCastToListThrowsClassCastForNullItems() {
        ClassCastException ex = assertThrows(ClassCastException.class,
                () -> CastUtils.safeCastToList(java.util.Arrays.asList("a", null), String.class));
        assertEquals("Expected a java.lang.String, but found: null", ex.getMessage());
    }

    @Test
    void safeCastToListReturnsEmptyWhenNotAList() {
        assertEquals(List.of(), CastUtils.safeCastToList("not-a-list", String.class));
    }
}
