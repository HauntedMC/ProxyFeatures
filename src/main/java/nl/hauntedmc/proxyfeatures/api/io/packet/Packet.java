package nl.hauntedmc.proxyfeatures.api.io.packet;

import com.velocitypowered.api.proxy.Player;

/**
 * Interface for all packet types, enabling abstraction from PacketEvents or other libraries.
 */
public interface Packet {
    void sendTo(Player player);
}
