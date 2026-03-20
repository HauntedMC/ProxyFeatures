package nl.hauntedmc.proxyfeatures.api.io.packet;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

class PacketManagerTest {

    @Test
    void classCanBeInstantiated() {
        assertNotNull(new PacketManager());
    }

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
    void sendBroadcastUsesProxyPlayers() {
        Player a = mock(Player.class);
        Player b = mock(Player.class);
        Packet packet = mock(Packet.class);
        ProxyServer proxy = mock(ProxyServer.class);
        when(proxy.getAllPlayers()).thenReturn(List.of(a, b));

        try (MockedStatic<ProxyFeatures> mocked = mockStatic(ProxyFeatures.class)) {
            mocked.when(ProxyFeatures::getProxyInstance).thenReturn(proxy);
            PacketManager.sendBroadcast(packet);
        }

        verify(packet).sendTo(a);
        verify(packet).sendTo(b);
    }

    @Test
    void sendMethodsHandleEmptyInputsWithoutInteraction() {
        Player player = mock(Player.class);
        Packet packet = mock(Packet.class);
        PacketManager.sendUnicast(player);
        PacketManager.sendMulticast(List.of(), packet);
        verifyNoInteractions(packet);
    }
}
