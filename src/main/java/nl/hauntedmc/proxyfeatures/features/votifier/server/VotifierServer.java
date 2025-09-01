package nl.hauntedmc.proxyfeatures.features.votifier.server;

import nl.hauntedmc.proxyfeatures.features.votifier.model.Vote;
import nl.hauntedmc.proxyfeatures.internal.FeatureLogger;

import javax.crypto.Cipher;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.util.Objects;
import java.util.concurrent.*;

public final class VotifierServer {

    public interface VoteHandler { void onVote(Vote vote); }

    private final String host;
    private final int port;
    private final int readTimeoutMillis;
    private final int backlog;
    private final int maxPacketBytes;

    private final PrivateKey privateKey;
    private final VoteHandler handler;
    private final FeatureLogger logger;

    private final int rsaBlockBytes;

    private volatile boolean running;
    private ServerSocket server;
    private ExecutorService pool;

    public VotifierServer(String host,
                          int port,
                          int readTimeoutMillis,
                          int backlog,
                          int maxPacketBytes,
                          PrivateKey privateKey,
                          VoteHandler handler,
                          FeatureLogger logger) {
        this.host = Objects.requireNonNull(host);
        this.port = port;
        this.readTimeoutMillis = Math.max(1000, readTimeoutMillis);
        this.backlog = Math.max(10, backlog);
        this.maxPacketBytes = Math.max(256, maxPacketBytes);
        this.privateKey = Objects.requireNonNull(privateKey);
        this.handler = Objects.requireNonNull(handler);
        this.logger = Objects.requireNonNull(logger);

        int keyBytes = -1;
        try {
            if (privateKey instanceof RSAPrivateKey rsa) {
                keyBytes = (rsa.getModulus().bitLength() + 7) / 8;
            }
        } catch (Throwable ignored) {}
        if (keyBytes <= 0) {
            keyBytes = 256;
            logger.warn("Unable to introspect RSA modulus; defaulting block size to " + keyBytes + " bytes.");
        }
        this.rsaBlockBytes = keyBytes;
    }

    public synchronized void start() throws IOException {
        if (running) return;

        this.server = new ServerSocket();
        this.server.setReuseAddress(true);
        this.server.bind(new InetSocketAddress(host, port), backlog);

        this.pool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "votifier-client");
            t.setDaemon(true);
            return t;
        });

        running = true;

        pool.execute(() -> {
            logger.info("Listening on " + host + ":" + port + " (expect " + rsaBlockBytes + "B RSA blocks)");
            while (running) {
                try {
                    Socket s = server.accept();
                    pool.execute(() -> handleClient(s));
                } catch (SocketException se) {
                    if (running) logger.warn("Socket exception: " + safeMsg(se));
                } catch (Throwable t) {
                    if (running) logger.warn("Accept error: " + safeMsg(t) + "\n" + stackTrace(t));
                }
            }
            logger.info("Accept loop terminated.");
        });
    }

    public synchronized void stop() {
        running = false;
        if (server != null && !server.isClosed()) {
            try { server.close(); } catch (IOException ignored) {}
        }
        if (pool != null) {
            pool.shutdownNow();
            try { pool.awaitTermination(2, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        }
    }

    public boolean isRunning() { return running; }
    public String getHost() { return host; }
    public int getPort() { return port; }

    private void handleClient(Socket s) {
        SocketAddress remote = s.getRemoteSocketAddress();
        String ip = "-";
        try {
            // Use small step timeout and enforce our own overall deadline.
            final int stepTimeoutMs = Math.min(300, Math.max(100, readTimeoutMillis / 10));
            s.setSoTimeout(stepTimeoutMs);

            InetAddress addr = s.getInetAddress();
            ip = addr != null ? addr.getHostAddress() : "-";

            // 1) Send a tiny banner/hello – some senders wait for any server output before writing.
            try {
                OutputStream os = s.getOutputStream();
                os.write("VOTIFIER 1\n".getBytes(StandardCharsets.US_ASCII));
                os.flush();
            } catch (Throwable ignored) {
                // Non-fatal; continue reading even if banner can't be sent
            }

            // 2) Read exactly one RSA block, tolerating slow/bursty senders until global deadline.
            byte[] enc = readExactWithDeadline(s.getInputStream(), rsaBlockBytes, maxPacketBytes, readTimeoutMillis);
            if (enc.length != rsaBlockBytes) {
                logger.warn("Invalid block size from " + ip + ": " + enc.length + "B (expected " + rsaBlockBytes + "B)");
                return;
            }

            byte[] plain = decrypt(enc, privateKey);
            String msg = new String(plain, StandardCharsets.US_ASCII).trim();

            // Expected format: "VOTE\nservice\nusername\naddress\ntimestamp\n"
            String[] parts = msg.split("\n");
            if (parts.length < 5 || !parts[0].equals("VOTE")) {
                logger.warn("Invalid payload from " + ip + ": " + shorten(msg));
                return;
            }

            String service = sanitize(parts[1]);
            String user    = sanitize(parts[2]);
            String addrStr = sanitize(parts[3]);
            String tsStr   = sanitize(parts[4]);
            long ts;
            try { ts = Long.parseLong(tsStr); }
            catch (NumberFormatException nfe) { ts = System.currentTimeMillis(); }

            handler.onVote(new Vote(service, user, addrStr, ts));
        } catch (EOFException eof) {
            logger.warn("Incomplete RSA block from " + safeRemote(remote) + ": " + safeMsg(eof));
        } catch (SocketTimeoutException ste) {
            logger.warn("Read timeout from " + safeRemote(remote));
        } catch (GeneralSecurityException gse) {
            logger.warn("Decryption error from " + safeRemote(remote) + ": " + safeMsg(gse));
        } catch (Throwable t) {
            logger.warn("Error handling " + safeRemote(remote) + ": " + safeMsg(t) + "\n" + stackTrace(t));
        } finally {
            safeClose(s);
        }
    }

    private byte[] decrypt(byte[] enc, PrivateKey key) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.DECRYPT_MODE, key);
        return cipher.doFinal(enc);
    }

    /**
     * Reads exactly expectedBytes, using a short per-read timeout and a global deadline equal
     * to totalTimeoutMs. Avoids waiting for EOF and tolerates slow/bursty senders.
     */
    private byte[] readExactWithDeadline(InputStream in, int expectedBytes, int cap, int totalTimeoutMs) throws IOException {
        if (expectedBytes <= 0) throw new IOException("Expected bytes must be > 0");
        if (expectedBytes > cap) throw new IOException("Expected block (" + expectedBytes + "B) exceeds cap " + cap + "B");

        byte[] out = new byte[expectedBytes];
        int off = 0;

        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(totalTimeoutMs);
        byte[] buf = new byte[Math.min(1024, expectedBytes)];

        while (off < expectedBytes) {
            int want = Math.min(buf.length, expectedBytes - off);
            int r = in.read(buf, 0, want);
            if (r > 0) {
                System.arraycopy(buf, 0, out, off, r);
                off += r;
            } else if (r == 0) {
                // Shouldn't happen on InputStream; just check deadline.
            } else if (r == -1) {
                break; // early EOF
            }

            if (off < expectedBytes && System.nanoTime() > deadline) {
                throw new SocketTimeoutException("Global deadline exceeded while reading RSA block (got " + off + "B of " + expectedBytes + "B)");
            }
        }

        if (off != expectedBytes) {
            throw new EOFException("Incomplete RSA block: got " + off + "B, expected " + expectedBytes + "B");
        }

        // Best-effort drain of any trailing garbage/newlines without blocking
        try {
            int avail = Math.min(in.available(), Math.max(0, cap - expectedBytes));
            if (avail > 0) in.skipNBytes(avail);
        } catch (Throwable ignored) {}

        return out;
    }

    private void safeClose(Socket s) {
        try { s.close(); } catch (Throwable ignored) {}
    }

    private String sanitize(String s) {
        if (s == null) return "-";
        String t = s.trim();
        if (t.length() > 128) t = t.substring(0, 128);
        return t;
    }

    private String shorten(String s) {
        if (s == null) return "(null)";
        return s.length() <= 160 ? s : s.substring(0, 160) + "...";
    }

    /* =========================  Helpers  ========================= */

    private static String safeRemote(SocketAddress remote) {
        return remote == null ? "(unknown)" : remote.toString();
    }

    private static String safeMsg(Throwable t) {
        return (t == null || t.getMessage() == null) ? String.valueOf(t) : t.getMessage();
    }

    private static String stackTrace(Throwable t) {
        try {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            return sw.toString();
        } catch (Throwable ignored) {
            return "(no stack trace)";
        }
    }
}
