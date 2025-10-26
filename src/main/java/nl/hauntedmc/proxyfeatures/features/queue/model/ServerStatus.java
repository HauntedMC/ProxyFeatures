package nl.hauntedmc.proxyfeatures.features.queue.model;


public final class ServerStatus {
    public final boolean online;
    public final int onlinePlayers;
    public final int maxPlayers;

    private ServerStatus(boolean online, int onlinePlayers, int maxPlayers) {
        this.online = online;
        this.onlinePlayers = onlinePlayers;
        this.maxPlayers = maxPlayers;
    }

    public static ServerStatus online(int online, int max) {
        return new ServerStatus(true, online, max);
    }

    public static ServerStatus offline() {
        return new ServerStatus(false, -1, -1);
    }

    public static ServerStatus unknown() {
        return new ServerStatus(false, -1, -1);
    }

    public boolean isOnline() {
        return online;
    }
}

