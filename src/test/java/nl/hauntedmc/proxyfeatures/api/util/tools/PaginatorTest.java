package nl.hauntedmc.proxyfeatures.api.util.tools;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PaginatorTest {

    @Test
    void paginateReturnsSingleEmptyPageForNullOrEmptyInput() {
        Paginator.Page<String> nullPage = Paginator.paginate(null, 1, 10);
        assertEquals(List.of(), nullPage.items());
        assertEquals(1, nullPage.page());
        assertEquals(1, nullPage.totalPages());
        assertEquals(0, nullPage.totalItems());

        Paginator.Page<String> emptyPage = Paginator.paginate(List.of(), 2, 10);
        assertEquals(List.of(), emptyPage.items());
        assertEquals(1, emptyPage.page());
        assertEquals(1, emptyPage.totalPages());
        assertEquals(0, emptyPage.totalItems());
    }

    @Test
    void paginateClampsPageAndReturnsCorrectSlice() {
        List<Integer> all = List.of(1, 2, 3, 4, 5);
        Paginator.Page<Integer> page = Paginator.paginate(all, 99, 2);

        assertEquals(List.of(5), page.items());
        assertEquals(3, page.page());
        assertEquals(3, page.totalPages());
        assertEquals(5, page.totalItems());
        assertEquals(2, page.pageSize());
    }
}
