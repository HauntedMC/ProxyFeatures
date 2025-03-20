package nl.hauntedmc.proxyfeatures.features.staffchat.internal;

public class ChatChannel {
    private final String id;
    private final String prefix;
    private final String permission;
    private final String formatKey;

    public ChatChannel(String id, String prefix, String permission, String formatKey) {
        this.id = id;
        this.prefix = prefix;
        this.permission = permission;
        this.formatKey = formatKey;
    }

    public String getId() {
        return id;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getPermission() {
        return permission;
    }

    public String getFormatKey() {
        return formatKey;
    }
}
