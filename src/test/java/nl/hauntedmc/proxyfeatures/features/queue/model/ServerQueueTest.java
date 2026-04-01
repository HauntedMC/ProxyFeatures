package nl.hauntedmc.proxyfeatures.features.queue.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ServerQueueTest {

    @Test
    void enqueueIsIdempotentAndPollingRespectsPriorityThenFifo() {
        ServerQueue queue = new ServerQueue("lobby");
        UUID low = UUID.randomUUID();
        UUID highFirst = UUID.randomUUID();
        UUID highSecond = UUID.randomUUID();

        QueueEntry lowEntry = queue.enqueue(low, 1);
        QueueEntry highA = queue.enqueue(highFirst, 3);
        QueueEntry highB = queue.enqueue(highSecond, 3);
        QueueEntry duplicate = queue.enqueue(low, 9);

        assertSame(lowEntry, duplicate);
        assertEquals("lobby", queue.serverName());
        assertEquals(Optional.of(2), queue.positionOf(low));

        assertEquals(highA, queue.pollNextConnectable());
        assertEquals(highB, queue.pollNextConnectable());
        assertEquals(lowEntry, queue.pollNextConnectable());
        assertNull(queue.pollNextConnectable());
    }

    @Test
    void requeueAndMoveOperationsMaintainExpectedOrdering() {
        ServerQueue queue = new ServerQueue("queue");
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        queue.enqueue(a, 1);
        queue.enqueue(b, 1);
        queue.enqueue(c, 1);

        assertTrue(queue.moveToFront(c));
        assertEquals(Optional.of(0), queue.positionOf(c));
        assertFalse(queue.moveToFront(UUID.randomUUID()));

        QueueEntry vip = new QueueEntry(UUID.randomUUID(), 5, Instant.now());
        queue.requeueFront(vip);
        assertEquals(vip, queue.pollNextConnectable());

        queue.remove(a);
        assertFalse(queue.contains(a));
    }

    @Test
    void graceLifecycleAndIndexedIterationWork() {
        ServerQueue queue = new ServerQueue("queue");
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        queue.enqueue(p1, 2);
        queue.enqueue(p2, 1);

        queue.startGrace(p1, Duration.ofMillis(-1));
        queue.expireGraces();
        assertFalse(queue.contains(p1));
        assertTrue(queue.contains(p2));

        queue.clearReservation(p2);
        assertFalse(queue.contains(p2));
        queue.startGrace(UUID.randomUUID(), Duration.ofSeconds(1));

        queue.enqueue(p1, 2);
        queue.enqueue(p2, 2);
        List<UUID> order = new ArrayList<>();
        queue.forEachIndexed((idx, entry) -> {
            assertEquals(order.size(), idx);
            order.add(entry.playerId());
        });
        assertEquals(List.of(p1, p2), order);
    }

    @Test
    void emptyAndUnknownQueriesReturnExpectedDefaults() {
        ServerQueue queue = new ServerQueue("queue");
        UUID missing = UUID.randomUUID();
        assertNull(queue.pollNextConnectable());
        assertTrue(queue.positionOf(missing).isEmpty());
        assertFalse(queue.moveToFront(missing));
    }
}
