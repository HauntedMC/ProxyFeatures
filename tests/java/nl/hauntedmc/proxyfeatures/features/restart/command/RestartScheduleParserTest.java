package nl.hauntedmc.proxyfeatures.features.restart.command;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RestartScheduleParserTest {

    private static final ZoneId ZONE = ZoneId.of("UTC");
    private static final ZonedDateTime NOW = ZonedDateTime.parse("2026-03-23T10:00:00Z[UTC]"); // Monday

    @Test
    void parseAcceptsSingleIsoDateTimeToken() {
        ZonedDateTime out = RestartScheduleParser.parse(
                new String[]{"schedule", "2026-03-25T14:30"},
                ZONE,
                NOW
        );

        assertEquals(ZonedDateTime.parse("2026-03-25T14:30:00Z[UTC]"), out);
    }

    @Test
    void parseAcceptsDateAndTimeInEitherOrder() {
        ZonedDateTime out = RestartScheduleParser.parse(
                new String[]{"schedule", "14:30", "2026-03-25"},
                ZONE,
                NOW
        );

        assertEquals(ZonedDateTime.parse("2026-03-25T14:30:00Z[UTC]"), out);
    }

    @Test
    void parseDayAliasRollsToNextWeekWhenTimeAlreadyPassedToday() {
        ZonedDateTime out = RestartScheduleParser.parse(
                new String[]{"schedule", "mon", "09:00"},
                ZONE,
                NOW
        );

        assertEquals(ZonedDateTime.parse("2026-03-30T09:00:00Z[UTC]"), out);
    }

    @Test
    void parseDayAliasUsesSameDayWhenTimeIsStillInFuture() {
        ZonedDateTime out = RestartScheduleParser.parse(
                new String[]{"schedule", "mon", "11:00"},
                ZONE,
                NOW
        );

        assertEquals(ZonedDateTime.parse("2026-03-23T11:00:00Z[UTC]"), out);
    }

    @Test
    void parseAcceptsAlternativeDateAndTimeFormats() {
        ZonedDateTime out = RestartScheduleParser.parse(
                new String[]{"schedule", "25-03-2026", "14.30"},
                ZONE,
                NOW
        );

        assertEquals(ZonedDateTime.parse("2026-03-25T14:30:00Z[UTC]"), out);
    }

    @Test
    void parseReturnsNullForInvalidInputs() {
        assertNull(RestartScheduleParser.parse(new String[]{"schedule"}, ZONE, NOW));
        assertNull(RestartScheduleParser.parse(new String[]{"schedule", "nonsense"}, ZONE, NOW));
        assertNull(RestartScheduleParser.parse(new String[]{"schedule", "2026-03-25", "nonsense"}, ZONE, NOW));
    }
}
