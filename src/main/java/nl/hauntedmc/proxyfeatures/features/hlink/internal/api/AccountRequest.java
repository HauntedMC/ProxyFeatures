package nl.hauntedmc.proxyfeatures.features.hlink.internal.api;

public class AccountRequest {
    private String status = null;
    private boolean exists;

    public String getStatus() {
        return this.status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean getExists() {
        return this.exists;
    }

    public void setExists(boolean exists) {
        this.exists = exists;
    }
}
