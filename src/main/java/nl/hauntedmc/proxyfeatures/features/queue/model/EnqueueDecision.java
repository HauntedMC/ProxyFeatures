package nl.hauntedmc.proxyfeatures.features.queue.model;

/**
 * Result of the pre-connect decision. Accessors are allow() and bypass().
 */
public record EnqueueDecision(boolean allow, boolean bypass) {
    public static final EnqueueDecision ALLOW = new EnqueueDecision(true, false);
    public static final EnqueueDecision ALLOW_BYPASS = new EnqueueDecision(true, true);
    public static final EnqueueDecision DENY_QUEUED = new EnqueueDecision(false, false);
}
