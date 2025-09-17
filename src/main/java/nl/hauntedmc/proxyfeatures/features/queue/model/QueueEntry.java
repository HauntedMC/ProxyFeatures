package nl.hauntedmc.proxyfeatures.features.queue.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable queue record: who, their priority, and when they were enqueued.
 */
public record QueueEntry(UUID playerId, int priority, Instant enqueuedAt) {
}
