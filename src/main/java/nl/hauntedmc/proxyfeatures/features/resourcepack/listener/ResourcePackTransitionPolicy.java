package nl.hauntedmc.proxyfeatures.features.resourcepack.listener;

final class ResourcePackTransitionPolicy {

    record TransitionPlan(boolean removePreviousPack, String offerPackIdentifier, boolean resumeImmediately) {
    }

    private ResourcePackTransitionPolicy() {
    }

    static TransitionPlan plan(boolean hasPreviousServer, String currentServer, boolean previousPackExists, boolean currentPackExists) {
        if (!hasPreviousServer) {
            return new TransitionPlan(false, "global", false);
        }

        if (currentPackExists) {
            return new TransitionPlan(previousPackExists, currentServer, false);
        }

        return new TransitionPlan(previousPackExists, null, true);
    }
}
