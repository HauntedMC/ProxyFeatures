package nl.hauntedmc.proxyfeatures.api.io.packet;

import com.velocitypowered.api.proxy.Player;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.*;

class PacketManagerTest {

    @Test
    void sendUnicastSendsAllPacketsToSinglePlayer() {
        Player player = mock(Player.class);
        Packet first = mock(Packet.class);
        Packet second = mock(Packet.class);

        PacketManager.sendUnicast(player, first, second);

        verify(first).sendTo(player);
        verify(second).sendTo(player);
    }

    @Test
    void sendMulticastSendsAllPacketsToAllTargets() {
        Player a = mock(Player.class);
        Player b = mock(Player.class);
        Packet packet = mock(Packet.class);

        PacketManager.sendMulticast(List.of(a, b), packet);

        verify(packet).sendTo(a);
        verify(packet).sendTo(b);
    }

    @Test
    void sendBroadcastUsesProvidedPlayers() {
        Player a = mock(Player.class);
        Player b = mock(Player.class);
        Packet packet = mock(Packet.class);

        PacketManager.sendBroadcast(List.of(a, b), packet);

        verify(packet).sendTo(a);
        verify(packet).sendTo(b);
    }

    @Test
    void sendMethodsHandleEmptyInputsWithoutInteraction() {
        Player player = mock(Player.class);
        Packet packet = mock(Packet.class);
        PacketManager.sendUnicast(player);
        PacketManager.sendMulticast(List.of(), packet);
        PacketManager.sendBroadcast(List.of(), packet);
        verifyNoInteractions(packet);
    }
}
