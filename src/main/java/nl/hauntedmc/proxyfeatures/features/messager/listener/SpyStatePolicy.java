package nl.hauntedmc.proxyfeatures.features.messager.listener;

final class SpyStatePolicy {

    enum Action {
        NONE,
        ENABLE,
        DISABLE
    }

    private SpyStatePolicy() {
    }

    static Action reconcile(boolean hasPermission, boolean currentlyEnabled) {
        if (hasPermission && !currentlyEnabled) {
            return Action.ENABLE;
        }
        if (!hasPermission && currentlyEnabled) {
            return Action.DISABLE;
        }
        return Action.NONE;
    }
}
