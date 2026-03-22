package nl.hauntedmc.proxyfeatures.features.votifier.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Optional;

public final class RSAUtil {
    private RSAUtil() {
    }

    /**
     * Generates a new RSA keypair and writes PEM files (public: X.509, private: PKCS#8).
     */
    public static void generateAndSaveKeyPair(Path publicPem, Path privatePem, int bits)
            throws GeneralSecurityException, IOException {
        if (bits < 2048) throw new GeneralSecurityException("Key bits too small: " + bits);

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(bits, new SecureRandom());
        KeyPair kp = kpg.generateKeyPair();

        // Public (X.509 SubjectPublicKeyInfo)
        X509EncodedKeySpec pubSpec = new X509EncodedKeySpec(kp.getPublic().getEncoded());
        String pubPem = toPem("PUBLIC KEY", pubSpec.getEncoded());
        if (publicPem.getParent() != null) Files.createDirectories(publicPem.getParent());
        Files.writeString(publicPem, pubPem);

        // Private (PKCS#8)
        PKCS8EncodedKeySpec privSpec = new PKCS8EncodedKeySpec(kp.getPrivate().getEncoded());
        String privPem = toPem("PRIVATE KEY", privSpec.getEncoded());
        if (privatePem.getParent() != null) Files.createDirectories(privatePem.getParent());
        Files.writeString(privatePem, privPem);
    }

    /**
     * Reads a PKCS#8 PEM private key and returns it.
     */
    public static Optional<PrivateKey> readPrivateKeyPem(Path pemPkcs8) {
        try {
            if (!Files.exists(pemPkcs8)) return Optional.empty();
            String pem = Files.readString(pemPkcs8);
            String b64 = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] der = Base64.getDecoder().decode(b64);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(der);
            return Optional.of(KeyFactory.getInstance("RSA").generatePrivate(spec));
        } catch (IOException | GeneralSecurityException | IllegalArgumentException t) {
            return Optional.empty();
        }
    }

    public static String toPem(String type, byte[] der) {
        String base64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(der);
        return "-----BEGIN " + type + "-----\n" + base64 + "\n-----END " + type + "-----\n";
    }
}
