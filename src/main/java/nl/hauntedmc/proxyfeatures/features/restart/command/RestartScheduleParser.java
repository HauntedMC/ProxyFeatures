package nl.hauntedmc.proxyfeatures.features.restart.command;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;

final class RestartScheduleParser {

    private static final DateTimeFormatter[] DATE_FORMATS = new DateTimeFormatter[]{
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd-MM-uuuu"),
            DateTimeFormatter.ofPattern("dd/MM/uuuu"),
            DateTimeFormatter.ofPattern("dd.MM.uuuu")
    };

    private static final DateTimeFormatter[] TIME_FORMATS = new DateTimeFormatter[]{
            DateTimeFormatter.ofPattern("H:mm"),
            DateTimeFormatter.ofPattern("HH:mm"),
            DateTimeFormatter.ofPattern("H.mm"),
            DateTimeFormatter.ofPattern("HH.mm")
    };

    private static final Map<String, DayOfWeek> DAY_ALIASES = Map.ofEntries(
            Map.entry("mon", DayOfWeek.MONDAY),
            Map.entry("monday", DayOfWeek.MONDAY),
            Map.entry("ma", DayOfWeek.MONDAY),
            Map.entry("maandag", DayOfWeek.MONDAY),

            Map.entry("tue", DayOfWeek.TUESDAY),
            Map.entry("tuesday", DayOfWeek.TUESDAY),
            Map.entry("di", DayOfWeek.TUESDAY),
            Map.entry("dinsdag", DayOfWeek.TUESDAY),

            Map.entry("wed", DayOfWeek.WEDNESDAY),
            Map.entry("wednesday", DayOfWeek.WEDNESDAY),
            Map.entry("wo", DayOfWeek.WEDNESDAY),
            Map.entry("woensdag", DayOfWeek.WEDNESDAY),

            Map.entry("thu", DayOfWeek.THURSDAY),
            Map.entry("thursday", DayOfWeek.THURSDAY),
            Map.entry("do", DayOfWeek.THURSDAY),
            Map.entry("donderdag", DayOfWeek.THURSDAY),

            Map.entry("fri", DayOfWeek.FRIDAY),
            Map.entry("friday", DayOfWeek.FRIDAY),
            Map.entry("vr", DayOfWeek.FRIDAY),
            Map.entry("vrijdag", DayOfWeek.FRIDAY),

            Map.entry("sat", DayOfWeek.SATURDAY),
            Map.entry("saturday", DayOfWeek.SATURDAY),
            Map.entry("za", DayOfWeek.SATURDAY),
            Map.entry("zaterdag", DayOfWeek.SATURDAY),

            Map.entry("sun", DayOfWeek.SUNDAY),
            Map.entry("sunday", DayOfWeek.SUNDAY),
            Map.entry("zo", DayOfWeek.SUNDAY),
            Map.entry("zondag", DayOfWeek.SUNDAY)
    );

    private RestartScheduleParser() {
    }

    static ZonedDateTime parse(String[] args, ZoneId zone, ZonedDateTime now) {
        if (args.length == 2) {
            return parseSingleTokenDateTime(args[1], zone);
        }

        if (args.length == 3) {
            String a = args[1];
            String b = args[2];

            LocalTime timeA = tryParseTime(a);
            LocalTime timeB = tryParseTime(b);

            if (timeA != null && timeB == null) {
                return combineDateOrDayWithTime(b, timeA, zone, now);
            }
            if (timeB != null && timeA == null) {
                return combineDateOrDayWithTime(a, timeB, zone, now);
            }

            LocalDate dateA = tryParseDate(a);
            LocalDate dateB = tryParseDate(b);

            if (dateA != null && timeB != null) {
                return ZonedDateTime.of(dateA, timeB, zone);
            }
            if (dateB != null && timeA != null) {
                return ZonedDateTime.of(dateB, timeA, zone);
            }

            return null;
        }

        if (args.length >= 4) {
            String dateToken = args[1];
            String timeToken = args[2];

            LocalDate date = tryParseDate(dateToken);
            LocalTime time = tryParseTime(timeToken);

            if (date != null && time != null) {
                return ZonedDateTime.of(date, time, zone);
            }

            return null;
        }

        return null;
    }

    private static ZonedDateTime parseSingleTokenDateTime(String token, ZoneId zone) {
        if (token == null) return null;

        try {
            LocalDateTime ldt = LocalDateTime.parse(token, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            return ldt.atZone(zone);
        } catch (DateTimeParseException ignored) {
        }

        int tIndex = token.indexOf('T');
        if (tIndex > 0 && tIndex < token.length() - 1) {
            String datePart = token.substring(0, tIndex);
            String timePart = token.substring(tIndex + 1);

            LocalDate date = tryParseDate(datePart);
            LocalTime time = tryParseTime(timePart);
            if (date != null && time != null) {
                return ZonedDateTime.of(date, time, zone);
            }
        }

        return null;
    }

    private static ZonedDateTime combineDateOrDayWithTime(String dateOrDayToken,
                                                          LocalTime time,
                                                          ZoneId zone,
                                                          ZonedDateTime now) {
        LocalDate date = tryParseDate(dateOrDayToken);
        if (date != null) {
            return ZonedDateTime.of(date, time, zone);
        }

        DayOfWeek dow = tryParseDayOfWeek(dateOrDayToken);
        if (dow == null) return null;

        ZonedDateTime candidate = now.withHour(time.getHour()).withMinute(time.getMinute()).withSecond(0).withNano(0);

        int delta = (dow.getValue() - candidate.getDayOfWeek().getValue() + 7) % 7;
        candidate = candidate.plusDays(delta);

        if (!candidate.isAfter(now)) {
            candidate = candidate.plusWeeks(1);
        }

        return candidate;
    }

    private static LocalDate tryParseDate(String token) {
        if (token == null) return null;

        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(token, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    private static LocalTime tryParseTime(String token) {
        if (token == null) return null;

        for (DateTimeFormatter formatter : TIME_FORMATS) {
            try {
                return LocalTime.parse(token, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    private static DayOfWeek tryParseDayOfWeek(String token) {
        if (token == null) return null;
        return DAY_ALIASES.get(token.toLowerCase(Locale.ROOT));
    }
}
