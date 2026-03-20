package nl.hauntedmc.proxyfeatures.features.votifier.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;

import static org.junit.jupiter.api.Assertions.*;

class RSAUtilTest {

    @TempDir
    Path tempDir;

    @Test
    void generateAndReadPrivateKeyPemRoundTripWorks() throws Exception {
        Path publicPem = tempDir.resolve("public.pem");
        Path privatePem = tempDir.resolve("private.pem");

        RSAUtil.generateAndSaveKeyPair(publicPem, privatePem, 2048);

        assertTrue(Files.exists(publicPem));
        assertTrue(Files.exists(privatePem));
        assertTrue(Files.readString(publicPem).contains("BEGIN PUBLIC KEY"));
        assertTrue(Files.readString(privatePem).contains("BEGIN PRIVATE KEY"));
        assertTrue(RSAUtil.readPrivateKeyPem(privatePem).isPresent());
    }

    @Test
    void generateRejectsTooSmallKeySizes() {
        Path publicPem = tempDir.resolve("pub.pem");
        Path privatePem = tempDir.resolve("priv.pem");

        assertThrows(GeneralSecurityException.class,
                () -> RSAUtil.generateAndSaveKeyPair(publicPem, privatePem, 1024));
    }

    @Test
    void readPrivateKeyPemHandlesMissingAndInvalidFiles() throws Exception {
        Path missing = tempDir.resolve("missing.pem");
        assertTrue(RSAUtil.readPrivateKeyPem(missing).isEmpty());

        Path invalid = tempDir.resolve("invalid.pem");
        Files.writeString(invalid, "not-a-pem");
        assertTrue(RSAUtil.readPrivateKeyPem(invalid).isEmpty());
    }

    @Test
    void toPemProducesExpectedBoundaries() {
        String pem = RSAUtil.toPem("TEST KEY", new byte[]{1, 2, 3});
        assertTrue(pem.startsWith("-----BEGIN TEST KEY-----"));
        assertTrue(pem.contains("AQID"));
        assertTrue(pem.endsWith("-----END TEST KEY-----\n"));
    }
}
