package nl.hauntedmc.proxyfeatures.features.friends.listener;

import nl.hauntedmc.proxyfeatures.features.friends.entity.FriendSnapshot;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FriendActivityPolicyTest {

    @Test
    void classifyServerConnectTreatsMissingPreviousAsOnline() {
        FriendActivityPolicy.ConnectPlan plan = FriendActivityPolicy.classifyServerConnect(
                Optional.empty(),
                "hub"
        );

        assertEquals(FriendActivityPolicy.ConnectType.ONLINE, plan.type());
        assertEquals("hub", plan.to());
    }

    @Test
    void classifyServerConnectIgnoresSwitchToSameServer() {
        FriendActivityPolicy.ConnectPlan plan = FriendActivityPolicy.classifyServerConnect(
                Optional.of("Hub"),
                "hub"
        );

        assertEquals(FriendActivityPolicy.ConnectType.NONE, plan.type());
    }

    @Test
    void classifyServerConnectDetectsActualSwitch() {
        FriendActivityPolicy.ConnectPlan plan = FriendActivityPolicy.classifyServerConnect(
                Optional.of("hub"),
                "skyblock"
        );

        assertEquals(FriendActivityPolicy.ConnectType.SWITCH, plan.type());
        assertEquals("hub", plan.from());
        assertEquals("skyblock", plan.to());
    }

    @Test
    void parseFriendUuidsSkipsInvalidSnapshots() {
        UUID valid = UUID.fromString("11111111-1111-1111-1111-111111111111");
        List<UUID> out = FriendActivityPolicy.parseFriendUuids(Arrays.asList(
                new FriendSnapshot(1L, valid.toString(), "Remy"),
                new FriendSnapshot(2L, "not-a-uuid", "Bad"),
                new FriendSnapshot(3L, " ", "Blank"),
                null
        ));

        assertEquals(List.of(valid), out);
    }
}
