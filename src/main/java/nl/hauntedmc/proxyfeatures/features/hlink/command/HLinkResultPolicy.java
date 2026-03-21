package nl.hauntedmc.proxyfeatures.features.hlink.command;

import nl.hauntedmc.proxyfeatures.features.hlink.internal.HLinkHandler;

final class HLinkResultPolicy {

    enum Outcome {
        ERROR,
        ALREADY_REGISTERED,
        SUCCESS
    }

    record Decision(Outcome outcome, String token) {
    }

    private HLinkResultPolicy() {
    }

    static Decision evaluate(HLinkHandler.LinkResult result, Throwable error) {
        if (error != null || result == null || result.type() == HLinkHandler.LinkResultType.ERROR) {
            return new Decision(Outcome.ERROR, null);
        }

        if (result.type() == HLinkHandler.LinkResultType.ALREADY_REGISTERED) {
            return new Decision(Outcome.ALREADY_REGISTERED, null);
        }

        String token = result.token();
        if (token == null || token.isBlank()) {
            return new Decision(Outcome.ERROR, null);
        }

        return new Decision(Outcome.SUCCESS, token);
    }
}
