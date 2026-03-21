package nl.hauntedmc.proxyfeatures.features.commandlogger.listener;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.proxy.Player;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommandExecutionPolicyTest {

    @Test
    void extractAliasReturnsEmptyForNullAndBlankCommands() {
        assertEquals(Optional.empty(), CommandExecutionPolicy.extractAlias(null));
        assertEquals(Optional.empty(), CommandExecutionPolicy.extractAlias(""));
        assertEquals(Optional.empty(), CommandExecutionPolicy.extractAlias("   "));
    }

    @Test
    void extractAliasUsesFirstTokenAfterLeadingWhitespace() {
        assertEquals(Optional.of("say"), CommandExecutionPolicy.extractAlias("   say hello"));
        assertEquals(Optional.of("list"), CommandExecutionPolicy.extractAlias("list"));
        assertEquals(Optional.of("msg"), CommandExecutionPolicy.extractAlias("\tmsg\tRemy hi"));
    }

    @Test
    void describeSourceUsesPlayerNameAndUuid() {
        Player player = mock(Player.class);
        UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(player.getUsername()).thenReturn("Remy");
        when(player.getUniqueId()).thenReturn(id);

        String label = CommandExecutionPolicy.describeSource(player);

        assertEquals("Remy (11111111-1111-1111-1111-111111111111)", label);
    }

    @Test
    void describeSourceFallsBackToLowerCaseSimpleClassName() {
        String label = CommandExecutionPolicy.describeSource(new ConsoleLikeSource());
        assertEquals("consolelikesource", label);
    }

    private static final class ConsoleLikeSource implements CommandSource {
        @Override
        public Tristate getPermissionValue(String permission) {
            return Tristate.UNDEFINED;
        }
    }
}
