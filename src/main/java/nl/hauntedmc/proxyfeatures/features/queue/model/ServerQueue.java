package nl.hauntedmc.proxyfeatures.features.queue.model;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Thread-safe priority FIFO queue with soft reservation (grace window).
 * Implementation notes:
 * - We bucket players by integer priority (higher first). Within each bucket, FIFO order.
 * - A quick index maps playerId -> entry for O(1) membership; position is computed by linear scan.
 * - Grace reservations are tracked separately; on expiry, we remove the player from the queue.
 */
public class ServerQueue {
    private final String serverName;

    // Priority buckets: high -> low (reverse natural order)
    private final NavigableMap<Integer, Deque<QueueEntry>> buckets = new TreeMap<>(Comparator.reverseOrder());

    // Fast lookup for membership & details
    private final Map<UUID, QueueEntry> index = new ConcurrentHashMap<>();

    // Grace reservations: playerId -> expiry timestamp
    private final Map<UUID, Instant> graceMap = new ConcurrentHashMap<>();

    public ServerQueue(String serverName) {
        this.serverName = serverName;
    }

    public String serverName() { return serverName; }

    public synchronized QueueEntry enqueue(UUID playerId, int priority) {
        if (index.containsKey(playerId)) return index.get(playerId); // idempotent
        QueueEntry entry = new QueueEntry(playerId, priority, Instant.now());
        buckets.computeIfAbsent(priority, p -> new ArrayDeque<>()).addLast(entry);
        index.put(playerId, entry);
        graceMap.remove(playerId); // clear any grace if rejoining
        return entry;
    }

    /** Returns next candidate from the head (highest priority, FIFO), removing it from data structures. */
    public synchronized QueueEntry pollNextConnectable() {
        QueueEntry head = peekHead();
        if (head == null) return null;
        remove(head.playerId());
        return head;
    }

    private QueueEntry peekHead() {
        for (Deque<QueueEntry> dq : buckets.values()) {
            QueueEntry e = dq.peekFirst();
            if (e != null) return e;
        }
        return null;
    }

    /** Requeue an entry to the front of its priority bucket (used after a failed connect attempt). */
    public synchronized void requeueFront(QueueEntry entry) {
        Deque<QueueEntry> dq = buckets.computeIfAbsent(entry.priority(), p -> new ArrayDeque<>());
        dq.addFirst(entry);
        index.put(entry.playerId(), entry);
    }

    public synchronized void remove(UUID playerId) {
        QueueEntry e = index.remove(playerId);
        if (e == null) return;
        Deque<QueueEntry> dq = buckets.get(e.priority());
        if (dq != null) {
            dq.removeIf(qe -> qe.playerId().equals(playerId));
            if (dq.isEmpty()) buckets.remove(e.priority());
        }
    }

    public synchronized boolean contains(UUID playerId) { return index.containsKey(playerId); }

    /** Zero-based position within the entire queue (priority order respected). */
    public synchronized Optional<Integer> positionOf(UUID playerId) {
        QueueEntry e = index.get(playerId);
        if (e == null) return Optional.empty();
        int pos = 0;
        for (Map.Entry<Integer, Deque<QueueEntry>> be : buckets.entrySet()) {
            for (QueueEntry qe : be.getValue()) {
                if (qe.playerId().equals(playerId)) {
                    return Optional.of(pos);
                }
                pos++;
            }
        }
        return Optional.empty();
    }

    /** Begin a grace window for a currently queued player. */
    public synchronized void startGrace(UUID playerId, Duration grace) {
        if (index.containsKey(playerId)) {
            graceMap.put(playerId, Instant.now().plus(grace));
        }
    }

    /** Clear reservation & remove from queue after successful connect. */
    public synchronized void clearReservation(UUID playerId) {
        graceMap.remove(playerId);
        remove(playerId);
    }

    /** Remove all players whose grace has expired. */
    public synchronized void expireGraces() {
        Instant now = Instant.now();
        Set<UUID> toRemove = new HashSet<>();
        for (Map.Entry<UUID, Instant> e : graceMap.entrySet()) {
            if (now.isAfter(e.getValue())) {
                toRemove.add(e.getKey());
            }
        }
        for (UUID id : toRemove) {
            graceMap.remove(id);
            remove(id);
        }
    }

    /** Iterate over the whole queue in display order, providing index and entry. */
    public synchronized void forEachIndexed(BiConsumer<Integer, QueueEntry> consumer) {
        int idx = 0;
        for (Map.Entry<Integer, Deque<QueueEntry>> be : buckets.entrySet()) {
            for (QueueEntry qe : be.getValue()) {
                consumer.accept(idx++, qe);
            }
        }
    }

    /** Move a specific player to the very front of their priority bucket. */
    public synchronized boolean moveToFront(UUID playerId) {
        QueueEntry e = index.get(playerId);
        if (e == null) return false;
        Deque<QueueEntry> dq = buckets.get(e.priority());
        if (dq == null) return false;
        dq.removeIf(qe -> qe.playerId().equals(playerId));
        dq.addFirst(e);
        return true;
    }
}
