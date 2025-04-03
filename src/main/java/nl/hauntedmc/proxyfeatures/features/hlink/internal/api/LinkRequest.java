package nl.hauntedmc.proxyfeatures.features.hlink.internal.api;

public class LinkRequest {
    private String status = null;
    private String results;
    private String found;

    public String getStatus() {
        return this.status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getResults() {
        return this.results;
    }

    public void setResults(String results) {
        this.results = results;
    }

    public String getFound() {
        return this.found;
    }

    public void setFound(String found) {
        this.found = found;
    }
}