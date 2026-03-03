package nl.hauntedmc.proxyfeatures.features.votifier.internal;

import nl.hauntedmc.proxyfeatures.api.io.config.ConfigService;
import nl.hauntedmc.proxyfeatures.api.io.config.ConfigView;
import nl.hauntedmc.proxyfeatures.features.votifier.Votifier;

public final class VotifierLocalState {

    private static final String FILE = "local/votifier_state.yml";

    private final ConfigView view;

    public VotifierLocalState(Votifier feature) {
        this.view = new ConfigService(feature.getPlugin()).view(FILE, false);
    }

    public int lastResetYearMonth() {
        Integer v = view.get("last_reset_yyyymm", Integer.class);
        return v == null ? 0 : v;
    }

    public void setLastResetYearMonth(int yyyymm) {
        view.put("last_reset_yyyymm", yyyymm);
    }
}