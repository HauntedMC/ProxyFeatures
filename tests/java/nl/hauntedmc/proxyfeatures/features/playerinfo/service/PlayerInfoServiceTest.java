package nl.hauntedmc.proxyfeatures.features.playerinfo.service;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.ServerInfo;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.features.playerinfo.PlayerInfo;
import nl.hauntedmc.proxyfeatures.framework.config.FeatureConfigHandler;
import nl.hauntedmc.proxyfeatures.framework.log.FeatureLogger;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PlayerInfoServiceTest {

    @Test
    void constructorFallsBackForInvalidDatePatternAndFmtHandlesNull() {
        PlayerInfo feature = mock(PlayerInfo.class);
        FeatureConfigHandler cfg = mock(FeatureConfigHandler.class);
        FeatureLogger logger = mock(FeatureLogger.class);
        ORMContext orm = mock(ORMContext.class);
        when(feature.getConfigHandler()).thenReturn(cfg);
        when(feature.getLogger()).thenReturn(logger);
        when(feature.getOrmContext()).thenReturn(orm);
        when(cfg.get("timezone", String.class, "")).thenReturn("UTC");
        when(cfg.get("datetimeFormat", String.class, "dd-MM-yyyy HH:mm:ss")).thenReturn("invalid[");

        PlayerInfoService service = new PlayerInfoService(feature);

        assertEquals("01-01-1970 00:00:00", service.fmt(Instant.EPOCH));
        assertEquals("—", service.fmt(null));
        verify(logger).warn(contains("Invalid datetimeFormat"));
    }

    @Test
    void getOnlineStatusResolvesByNameThenUuidAndHandlesOffline() {
        PlayerInfo feature = playerInfoWithDefaults();
        PlayerInfoService service = new PlayerInfoService(feature);

        ProxyServer proxy = mock(ProxyServer.class);
        Player byName = mock(Player.class);
        ServerConnection serverConnection = mock(ServerConnection.class);
        ServerInfo serverInfo = new ServerInfo("hub", new InetSocketAddress("127.0.0.1", 25565));
        when(byName.getCurrentServer()).thenReturn(Optional.of(serverConnection));
        when(serverConnection.getServerInfo()).thenReturn(serverInfo);
        when(proxy.getPlayer("Remy")).thenReturn(Optional.of(byName));

        UUID uuid = UUID.randomUUID();
        Player byUuid = mock(Player.class);
        when(byUuid.getCurrentServer()).thenReturn(Optional.empty());
        when(proxy.getPlayer(uuid.toString())).thenReturn(Optional.empty());
        when(proxy.getPlayer(uuid)).thenReturn(Optional.of(byUuid));

        when(proxy.getPlayer("missing")).thenReturn(Optional.empty());

        try (MockedStatic<ProxyFeatures> mocked = mockStatic(ProxyFeatures.class)) {
            mocked.when(ProxyFeatures::getProxyInstance).thenReturn(proxy);

            PlayerInfoService.OnlineStatus nameStatus = service.getOnlineStatus("Remy");
            assertTrue(nameStatus.online());
            assertEquals("hub", nameStatus.serverName());

            PlayerInfoService.OnlineStatus uuidStatus = service.getOnlineStatus(uuid.toString());
            assertTrue(uuidStatus.online());
            assertEquals("unknown", uuidStatus.serverName());

            PlayerInfoService.OnlineStatus offline = service.getOnlineStatus("missing");
            assertFalse(offline.online());
            assertNull(offline.serverName());
        }
    }

    @Test
    void findPlayerByNameUsesLowercaseParameterAndFindByLastIpHandlesBlankInput() {
        PlayerInfo feature = playerInfoWithDefaults();
        ORMContext orm = feature.getOrmContext();
        Session session = mock(Session.class);
        @SuppressWarnings("unchecked")
        Query<PlayerEntity> playerQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<String> ipQuery = mock(Query.class);

        when(orm.runInTransaction(any())).thenAnswer(invocation -> {
            ORMContext.TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.execute(session);
        });

        PlayerEntity player = new PlayerEntity();
        player.setId(1L);
        player.setUsername("Remy");

        when(session.createQuery("SELECT p FROM PlayerEntity p WHERE lower(p.username) = :name", PlayerEntity.class))
                .thenReturn(playerQuery);
        when(playerQuery.setParameter("name", "remy")).thenReturn(playerQuery);
        when(playerQuery.setMaxResults(1)).thenReturn(playerQuery);
        when(playerQuery.uniqueResultOptional()).thenReturn(Optional.of(player));

        when(session.createQuery(
                "SELECT c.player.username FROM PlayerConnectionInfoEntity c " +
                        "WHERE c.ipAddress = :ip AND c.player.id <> :ex " +
                        "ORDER BY c.player.username ASC",
                String.class)).thenReturn(ipQuery);
        when(ipQuery.setParameter("ip", "1.2.3.4")).thenReturn(ipQuery);
        when(ipQuery.setParameter("ex", 5L)).thenReturn(ipQuery);
        when(ipQuery.getResultList()).thenReturn(List.of("Alpha", "Beta"));

        PlayerInfoService service = new PlayerInfoService(feature);

        Optional<PlayerEntity> found = service.findPlayerEntityByName("ReMy");
        assertTrue(found.isPresent());
        assertEquals("Remy", found.get().getUsername());

        assertEquals(List.of(), service.findUsernamesByLastIp(" ", 5L));
        assertEquals(List.of("Alpha", "Beta"), service.findUsernamesByLastIp("1.2.3.4", 5L));
    }

    private static PlayerInfo playerInfoWithDefaults() {
        PlayerInfo feature = mock(PlayerInfo.class);
        FeatureConfigHandler cfg = mock(FeatureConfigHandler.class);
        FeatureLogger logger = mock(FeatureLogger.class);
        ORMContext orm = mock(ORMContext.class);
        when(feature.getConfigHandler()).thenReturn(cfg);
        when(feature.getLogger()).thenReturn(logger);
        when(feature.getOrmContext()).thenReturn(orm);
        when(cfg.get("timezone", String.class, "")).thenReturn("UTC");
        when(cfg.get("datetimeFormat", String.class, "dd-MM-yyyy HH:mm:ss")).thenReturn("dd-MM-yyyy HH:mm:ss");
        return feature;
    }
}
