package nl.hauntedmc.proxyfeatures.features.restart.command;

import com.velocitypowered.api.command.CommandSource;
import nl.hauntedmc.proxyfeatures.api.command.FeatureCommand;
import nl.hauntedmc.proxyfeatures.features.restart.Restart;
import nl.hauntedmc.proxyfeatures.features.restart.internal.RestartHandler;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class ProxyRestartCommand implements FeatureCommand {

    private static final String BASE_PERMISSION = "proxyfeatures.feature.restart.command.proxyrestart";
    private static final String FORCE_PERMISSION = "proxyfeatures.feature.restart.command.proxyrestart.force";
    private static final String SCHEDULE_PERMISSION = "proxyfeatures.feature.restart.command.proxyrestart.schedule";
    private static final String CANCEL_PERMISSION = "proxyfeatures.feature.restart.command.proxyrestart.cancel";
    private static final String STATUS_PERMISSION = "proxyfeatures.feature.restart.command.proxyrestart.status";

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

    private final Restart feature;
    private final RestartHandler handler;

    public ProxyRestartCommand(Restart feature) {
        this.feature = feature;
        this.handler = feature.getHandler();
    }

    public String getName() {
        return "proxyrestart";
    }

    public String[] getAliases() {
        return new String[0];
    }

    public boolean hasPermission(Invocation invocation) {
        String[] args = invocation.arguments();
        CommandSource src = invocation.source();

        if (args.length == 0) {
            return src.hasPermission(BASE_PERMISSION);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "force" -> src.hasPermission(FORCE_PERMISSION);
            case "schedule" -> src.hasPermission(SCHEDULE_PERMISSION);
            case "cancel" -> src.hasPermission(CANCEL_PERMISSION);
            case "status" -> src.hasPermission(STATUS_PERMISSION);
            default -> src.hasPermission(BASE_PERMISSION);
        };
    }

    public void execute(Invocation invocation) {
        CommandSource src = invocation.source();
        String[] args = invocation.arguments();

        if (args.length == 0) {
            boolean started = handler.startCountdown();
            if (started) {
                src.sendMessage(feature.getLocalizationHandler().getMessage("restart.started").forAudience(src).build());
            } else {
                src.sendMessage(feature.getLocalizationHandler().getMessage("restart.already_running").forAudience(src).build());
            }
            return;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "force" -> {
                if (!src.hasPermission(FORCE_PERMISSION)) {
                    src.sendMessage(feature.getLocalizationHandler().getMessage("general.no_permission").forAudience(src).build());
                    return;
                }
                src.sendMessage(feature.getLocalizationHandler().getMessage("restart.forced").forAudience(src).build());
                handler.forceRestart();
                return;
            }
            case "schedule" -> {
                if (!src.hasPermission(SCHEDULE_PERMISSION)) {
                    src.sendMessage(feature.getLocalizationHandler().getMessage("general.no_permission").forAudience(src).build());
                    return;
                }

                ZonedDateTime target = parseScheduleTarget(args);
                if (target == null) {
                    src.sendMessage(feature.getLocalizationHandler().getMessage("restart.schedule.invalid_datetime").forAudience(src).build());
                    src.sendMessage(feature.getLocalizationHandler().getMessage("restart.schedule.cmd_usage").forAudience(src).build());
                    return;
                }

                RestartHandler.ScheduleResult result = handler.scheduleRestart(target);
                if (result == RestartHandler.ScheduleResult.SUCCESS) {
                    src.sendMessage(feature.getLocalizationHandler()
                            .getMessage("restart.schedule.set")
                            .with("datetime", handler.formatDateTime(target))
                            .forAudience(src)
                            .build());
                    return;
                }

                if (result == RestartHandler.ScheduleResult.COUNTDOWN_RUNNING) {
                    src.sendMessage(feature.getLocalizationHandler().getMessage("restart.schedule.blocked_by_running").forAudience(src).build());
                    return;
                }

                if (result == RestartHandler.ScheduleResult.ALREADY_SCHEDULED) {
                    ZonedDateTime existing = handler.getScheduledAt();
                    src.sendMessage(feature.getLocalizationHandler()
                            .getMessage("restart.schedule.already_scheduled")
                            .with("datetime", handler.formatDateTime(existing))
                            .forAudience(src)
                            .build());
                    return;
                }

                src.sendMessage(feature.getLocalizationHandler().getMessage("restart.schedule.time_must_be_future").forAudience(src).build());
                return;
            }
            case "cancel" -> {
                if (!src.hasPermission(CANCEL_PERMISSION)) {
                    src.sendMessage(feature.getLocalizationHandler().getMessage("general.no_permission").forAudience(src).build());
                    return;
                }

                boolean cancelled = handler.cancelScheduledRestart();
                if (cancelled) {
                    src.sendMessage(feature.getLocalizationHandler().getMessage("restart.cancel.ok").forAudience(src).build());
                } else {
                    src.sendMessage(feature.getLocalizationHandler().getMessage("restart.cancel.none").forAudience(src).build());
                }
                return;
            }
            case "status" -> {
                if (!src.hasPermission(STATUS_PERMISSION)) {
                    src.sendMessage(feature.getLocalizationHandler().getMessage("general.no_permission").forAudience(src).build());
                    return;
                }

                ZonedDateTime at = handler.getScheduledAt();
                if (at == null) {
                    src.sendMessage(feature.getLocalizationHandler().getMessage("restart.status.none").forAudience(src).build());
                } else {
                    src.sendMessage(feature.getLocalizationHandler()
                            .getMessage("restart.status.scheduled")
                            .with("datetime", handler.formatDateTime(at))
                            .forAudience(src)
                            .build());
                }
                return;
            }
        }

        src.sendMessage(feature.getLocalizationHandler().getMessage("restart.cmd_usage").forAudience(src).build());
    }

    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        String[] args = invocation.arguments();

        if (args.length == 1) {
            String p = args[0].toLowerCase(Locale.ROOT);
            return CompletableFuture.completedFuture(Stream.of("force", "schedule", "cancel", "status")
                    .filter(s -> s.startsWith(p))
                    .toList());
        }

        if (args.length == 2 && "schedule".equalsIgnoreCase(args[0])) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    private ZonedDateTime parseScheduleTarget(String[] args) {
        ZoneId zone = ZoneId.systemDefault();

        if (args.length == 2) {
            return parseSingleTokenDateTime(args[1], zone);
        }

        if (args.length == 3) {
            String a = args[1];
            String b = args[2];

            LocalTime timeA = tryParseTime(a);
            LocalTime timeB = tryParseTime(b);

            if (timeA != null && timeB == null) {
                return combineDateOrDayWithTime(b, timeA, zone);
            }
            if (timeB != null && timeA == null) {
                return combineDateOrDayWithTime(a, timeB, zone);
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

    private ZonedDateTime parseSingleTokenDateTime(String token, ZoneId zone) {
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

    private ZonedDateTime combineDateOrDayWithTime(String dateOrDayToken, LocalTime time, ZoneId zone) {
        LocalDate date = tryParseDate(dateOrDayToken);
        if (date != null) {
            return ZonedDateTime.of(date, time, zone);
        }

        DayOfWeek dow = tryParseDayOfWeek(dateOrDayToken);
        if (dow == null) return null;

        ZonedDateTime now = ZonedDateTime.now(zone);
        ZonedDateTime candidate = now.withHour(time.getHour()).withMinute(time.getMinute()).withSecond(0).withNano(0);

        int delta = (dow.getValue() - candidate.getDayOfWeek().getValue() + 7) % 7;
        candidate = candidate.plusDays(delta);

        if (!candidate.isAfter(now)) {
            candidate = candidate.plusWeeks(1);
        }

        return candidate;
    }

    private LocalDate tryParseDate(String token) {
        if (token == null) return null;

        for (DateTimeFormatter f : DATE_FORMATS) {
            try {
                return LocalDate.parse(token, f);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    private LocalTime tryParseTime(String token) {
        if (token == null) return null;

        for (DateTimeFormatter f : TIME_FORMATS) {
            try {
                return LocalTime.parse(token, f);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    private DayOfWeek tryParseDayOfWeek(String token) {
        if (token == null) return null;
        return DAY_ALIASES.get(token.toLowerCase(Locale.ROOT));
    }
}
