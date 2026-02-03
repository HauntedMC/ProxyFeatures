package nl.hauntedmc.proxyfeatures.features.commandhider.internal;

import nl.hauntedmc.proxyfeatures.features.commandhider.CommandHider;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public final class HiderHandler {

    private static final Locale LOCALE = Locale.ROOT;

    private final CommandHider feature;
    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(Snapshot.empty());

    public HiderHandler(CommandHider feature) {
        this.feature = feature;
    }

    /**
     * Refreshes the cached, normalized hidden command snapshot from config.
     * This avoids per-event allocations and guarantees consistent results during a tick.
     */
    public void refreshFromConfig() {
        List<String> raw = feature.getConfigHandler().getList("hidden-commands", String.class, List.of());
        if (raw == null || raw.isEmpty()) {
            snapshot.set(Snapshot.empty());
            return;
        }

        // Preserve order while de-duplicating.
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        for (String s : raw) {
            String norm = normalizeCommandLiteral(s);
            if (!norm.isEmpty()) ordered.add(norm);
        }

        if (ordered.isEmpty()) {
            snapshot.set(Snapshot.empty());
            return;
        }

        List<String> list = List.copyOf(ordered);
        Set<String> set = Set.copyOf(ordered);
        snapshot.set(new Snapshot(list, set));
    }

    /**
     * @return immutable normalized list (order preserved) for display/suggestions.
     */
    public List<String> hiddenCommandsList() {
        return snapshot.get().list();
    }

    /**
     * @return immutable normalized set for fast membership checks.
     */
    public Set<String> hiddenCommandsSet() {
        return snapshot.get().set();
    }

    /**
     * Normalizes a command literal from config or user input:
     * - trim
     * - strip leading '/'
     * - lowercase
     */
    public static String normalizeCommandLiteral(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.isEmpty()) return "";

        while (!s.isEmpty() && s.charAt(0) == '/') {
            s = s.substring(1).trim();
        }

        if (s.isEmpty()) return "";
        return s.toLowerCase(LOCALE);
    }

    /**
     * Returns a normalized list suitable for saving back to config, with stable ordering.
     */
    public static List<String> normalizeToUniqueList(List<String> raw) {
        if (raw == null || raw.isEmpty()) return List.of();

        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        for (String s : raw) {
            String norm = normalizeCommandLiteral(s);
            if (!norm.isEmpty()) ordered.add(norm);
        }
        if (ordered.isEmpty()) return List.of();

        return new ArrayList<>(ordered);
    }

    private record Snapshot(List<String> list, Set<String> set) {
        static Snapshot empty() {
            return new Snapshot(List.of(), Set.of());
        }
    }
}
