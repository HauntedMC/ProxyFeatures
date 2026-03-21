package nl.hauntedmc.proxyfeatures.features.resourcepack.listener;

final class ResourcePackOfferPolicy {

    enum Action {
        RESUME,
        SEND_OFFER
    }

    private ResourcePackOfferPolicy() {
    }

    static Action resolve(boolean packExists, boolean alreadyApplied) {
        return packExists && !alreadyApplied
                ? Action.SEND_OFFER
                : Action.RESUME;
    }
}
