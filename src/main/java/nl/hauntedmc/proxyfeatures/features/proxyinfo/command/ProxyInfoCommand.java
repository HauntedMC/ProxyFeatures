package nl.hauntedmc.proxyfeatures.features.proxyinfo.command;

import com.sun.management.OperatingSystemMXBean;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import nl.hauntedmc.proxyfeatures.api.command.FeatureCommand;
import nl.hauntedmc.proxyfeatures.features.proxyinfo.ProxyInfo;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ProxyInfoCommand implements FeatureCommand {
    private final ProxyInfo feature;
    private final ProxyServer proxy;
    private final OperatingSystemMXBean osBean;

    public ProxyInfoCommand(ProxyInfo feature) {
        this.feature = feature;
        this.proxy = feature.getPlugin().getProxy();
        this.osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
    }


    public String getName() {
        return "proxyinfo";
    }


    public String[] getAliases() {
        return new String[]{""};
    }


    public boolean hasPermission(Invocation invocation) {
        return invocation.source()
                .hasPermission("proxyfeatures.feature.proxyinfo.command.proxyinfo");
    }


    public void execute(Invocation invocation) {
        CommandSource src = invocation.source();
        if (invocation.arguments().length > 0) {
            src.sendMessage(feature.getLocalizationHandler()
                    .getMessage("proxyinfo.cmd_usage")
                    .forAudience(src)
                    .build());
            return;
        }

        // Header
        src.sendMessage(feature.getLocalizationHandler()
                .getMessage("proxyinfo.cmd_header")
                .forAudience(src)
                .build());

        // Versions
        sendEntry(src, "Velocity Version", proxy.getVersion().getVersion());
        sendEntry(src, "Java Version", System.getProperty("java.version"));

        // Uptime
        RuntimeMXBean rtBean = ManagementFactory.getRuntimeMXBean();
        Duration up = Duration.ofMillis(rtBean.getUptime());

        sendEntry(src, "Uptime",
                "%02d:%02d:%02d".formatted(
                        up.toHours(),
                        up.toMinutesPart(),
                        up.toSecondsPart()));
        // Network info
        InetSocketAddress bound = proxy.getBoundAddress();
        sendEntry(src, "Bound Address", bound.getHostString() + ":" + bound.getPort());

        // Server counts
        Collection<RegisteredServer> allServers = proxy.getAllServers();
        sendEntry(src, "Total Servers", String.valueOf(allServers.size()));

        // Connected clients
        sendEntry(src, "Connected Clients", String.valueOf(proxy.getAllPlayers().size()));

        // Memory usage
        Runtime rt = Runtime.getRuntime();
        long usedMB = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long maxMB = rt.maxMemory() / (1024 * 1024);
        sendEntry(src, "Memory Usage", String.format("%d MB / %d MB", usedMB, maxMB));

        // CPU load
        double processLoad = osBean.getProcessCpuLoad() * 100;
        double systemLoad = osBean.getCpuLoad() * 100;
        sendEntry(src, "Process CPU Load", String.format("%.2f%%", processLoad));
        sendEntry(src, "System CPU Load", String.format("%.2f%%", systemLoad));
    }


    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    private void sendEntry(CommandSource src, String setting, String value) {
        src.sendMessage(feature.getLocalizationHandler()
                .getMessage("proxyinfo.cmd_entry")
                .with("setting", setting)
                .with("value", value)
                .forAudience(src)
                .build());
    }
}
