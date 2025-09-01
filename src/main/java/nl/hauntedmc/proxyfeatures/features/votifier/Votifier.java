package nl.hauntedmc.proxyfeatures.features.votifier;

import nl.hauntedmc.commonlib.config.ConfigMap;
import nl.hauntedmc.commonlib.localization.MessageMap;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.features.VelocityBaseFeature;
import nl.hauntedmc.proxyfeatures.features.votifier.command.VotifierCommand;
import nl.hauntedmc.proxyfeatures.features.votifier.meta.Meta;
import nl.hauntedmc.proxyfeatures.features.votifier.model.Vote;
import nl.hauntedmc.proxyfeatures.features.votifier.server.VotifierServer;
import nl.hauntedmc.proxyfeatures.features.votifier.util.RSAUtil;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.*;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;

public class Votifier extends VelocityBaseFeature<Meta> {

    private VotifierServer server;

    public Votifier(ProxyFeatures plugin) {
        super(plugin, new Meta());
    }

    /* =========================  Defaults  ========================= */

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap cfg = new ConfigMap();

        // Core
        cfg.put("enabled", true);

        // Network
        cfg.put("host", "0.0.0.0");
        cfg.put("port", 8199);
        cfg.put("readTimeoutMillis", 5000);
        cfg.put("backlog", 50);

        // Security / files — always under <dataDir>/local/<file_name>
        cfg.put("generateKeys", true);
        cfg.put("keyBits", 2048);
        cfg.put("publicKeyFile", "public.key");   // PEM (X.509)
        cfg.put("privateKeyFile", "private.key"); // PEM (PKCS#8)

        // Allow/Deny as comma-separated strings
        cfg.put("allowlist", "");
        cfg.put("denylist", "");

        // Safety
        cfg.put("maxPacketBytes", 8192);

        return cfg;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap m = new MessageMap();
        m.add("votifier.usage",   "[Votifier] Gebruik: /votifier <status");
        m.add("votifier.status",
                "[Votifier] Status={status}, Host={host}, Port={port}, Timeout={timeout}ms, KeyBits={keybits}");

        return m;
    }

    /* =========================  Lifecycle  ========================= */

    @Override
    public void initialize() {
        getLifecycleManager().getCommandManager().registerFeatureCommand(new VotifierCommand(this));
        startOrReloadServer();
    }

    @Override
    public void disable() {
        stopServer();
    }

    /* =========================  Control  ========================= */

    public synchronized void startOrReloadServer() {
        stopServer();

        // REQUIRED: direct casts from getSetting()
        String host = (String) getConfigHandler().getSetting("host");
        int port = (int) getConfigHandler().getSetting("port");
        int timeoutMs = (int) getConfigHandler().getSetting("readTimeoutMillis");
        int backlog = (int) getConfigHandler().getSetting("backlog");
        int maxPacket = (int) getConfigHandler().getSetting("maxPacketBytes");
        boolean generateKeys = (boolean) getConfigHandler().getSetting("generateKeys");
        int keyBits = (int) getConfigHandler().getSetting("keyBits");
        String publicKeyFile = (String) getConfigHandler().getSetting("publicKeyFile");
        String privateKeyFile = (String) getConfigHandler().getSetting("privateKeyFile");

        // File locations are always under <dataDir>/local/<file_name>
        Path baseDir = getPlugin().getDataDirectory().resolve("local");
        Path pubPath = baseDir.resolve(publicKeyFile);
        Path privPath = baseDir.resolve(privateKeyFile);

        try {
            // Host sanity
            try {
                InetAddress.getByName(host);
            } catch (UnknownHostException uhe) {
                getLogger().error("Invalid host '" + host + "'");
                return;
            }

            // Keys
            if (!Files.exists(baseDir)) Files.createDirectories(baseDir);
            if (generateKeys && (!Files.exists(pubPath) || !Files.exists(privPath))) {
                RSAUtil.generateAndSaveKeyPair(pubPath, privPath, keyBits);
                getLogger().info("Generated RSA keypair in " + baseDir.toAbsolutePath());
            }

            PrivateKey privateKey = RSAUtil.readPrivateKeyPem(privPath)
                    .orElseThrow(() -> new GeneralSecurityException("Missing/invalid private key: " + privPath));

            server = new VotifierServer(
                    host,
                    port,
                    timeoutMs,
                    backlog,
                    maxPacket,
                    privateKey,
                    this::handleVote,
                    getLogger()
            );
            server.start();
        } catch (FileSystemException fse) {
            getLogger().error("File access error\n" + stackTrace(fse));
        } catch (GeneralSecurityException gse) {
            getLogger().error("Key error\n" + stackTrace(gse));
        } catch (IOException ioe) {
            getLogger().error("Bind/start error\n" + stackTrace(ioe));
        } catch (Throwable t) {
            getLogger().error("Unexpected error during start\n" + stackTrace(t));
        }
    }

    public synchronized void stopServer() {
        if (server != null) {
            try {
                server.stop();
            } catch (Throwable t) {
                getLogger().warn("Error while stopping server\n" + stackTrace(t));
            } finally {
                server = null;
            }
        }
    }

    private void handleVote(Vote vote) {
        getLogger().info(
                "Vote service=" + vote.serviceName()
                        + " user=" + vote.username()
                        + " ip=" + vote.address()
                        + " ts=" + vote.timestamp()
        );
    }

    /* =========================  Introspection for command  ========================= */

    public boolean isRunning() { return server != null && server.isRunning(); }

    public String currentHost() {
        if (server != null) return server.getHost();
        return (String) getConfigHandler().getSetting("host");
    }

    public int currentPort() {
        if (server != null) return server.getPort();
        return (int) getConfigHandler().getSetting("port");
    }

    public int currentTimeoutMs() {
        return (int) getConfigHandler().getSetting("readTimeoutMillis");
    }

    public int currentKeyBits() {
        return (int) getConfigHandler().getSetting("keyBits");
    }

    /* =========================  Helpers  ========================= */

    private static String stackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
