package nl.hauntedmc.proxyfeatures.features.restart.command;

import com.velocitypowered.api.command.CommandSource;
import nl.hauntedmc.proxyfeatures.api.command.FeatureCommand;
import nl.hauntedmc.proxyfeatures.features.restart.Restart;
import nl.hauntedmc.proxyfeatures.features.restart.internal.RestartHandler;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class ProxyRestartCommand implements FeatureCommand {

    private static final String BASE_PERMISSION = "proxyfeatures.feature.restart.command.proxyrestart";
    private static final String FORCE_PERMISSION = "proxyfeatures.feature.restart.command.proxyrestart.force";
    private static final String SCHEDULE_PERMISSION = "proxyfeatures.feature.restart.command.proxyrestart.schedule";
    private static final String CANCEL_PERMISSION = "proxyfeatures.feature.restart.command.proxyrestart.cancel";
    private static final String STATUS_PERMISSION = "proxyfeatures.feature.restart.command.proxyrestart.status";

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
        return RestartScheduleParser.parse(args, zone, ZonedDateTime.now(zone));
    }
}
