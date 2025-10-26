package nl.hauntedmc.proxyfeatures.api.util.tools;

import java.util.Collections;
import java.util.List;

public final class Paginator {

    private Paginator() {
    }

    public record Page<T>(List<T> items, int page, int totalPages, int totalItems, int pageSize) {
    }

    /**
     * 1-based paging; clamps page into [1..totalPages].
     */
    public static <T> Page<T> paginate(List<T> all, int page, int pageSize) {
        if (all == null || all.isEmpty()) {
            return new Page<>(Collections.emptyList(), 1, 1, 0, pageSize);
        }
        int total = all.size();
        int pages = Math.max(1, (int) Math.ceil(total / (double) pageSize));
        int p = Math.min(Math.max(1, page), pages);

        int from = (p - 1) * pageSize;
        int to = Math.min(from + pageSize, total);

        return new Page<>(all.subList(from, to), p, pages, total, pageSize);
    }
}
