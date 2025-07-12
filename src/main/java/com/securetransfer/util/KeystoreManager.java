package com.securetransfer.util;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Properties;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.security.auth.x500.X500Principal;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import java.util.Date;
import java.math.BigInteger;
import java.nio.file.attribute.PosixFilePermissions;

public class KeystoreManager {
    private static final String APP_NAME = "SecureTransfer";
    private static final String KEYSTORE_FILENAME = "keystore.p12";
    private static final String CONFIG_FILENAME = "keystore.properties";
    private static final String KEY_ALIAS = "securetransfer";
    private static final String KEYSTORE_TYPE = "PKCS12";
    private static final String CERT_DN = "CN=localhost, OU=User, O=SecureTransfer, L=City, S=State, C=US";
    private static final char[] DEFAULT_PASSWORD_CHARS = "changeit".toCharArray(); // fallback only

    private final Path appDataDir;
    private final Path keystorePath;
    private final Path configPath;

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public KeystoreManager() {
        this.appDataDir = getAppDataDir();
        this.keystorePath = appDataDir.resolve(KEYSTORE_FILENAME);
        this.configPath = appDataDir.resolve(CONFIG_FILENAME);
    }

    public Path getKeystorePath() { return keystorePath; }
    public Path getConfigPath() { return configPath; }

    public String getKeystorePassword() throws IOException {
        Properties props = loadConfig();
        String pwd = props.getProperty("keystore.password");
        if (pwd == null || pwd.isEmpty()) throw new IOException("Keystore password not found in config");
        return pwd;
    }

    public KeyStore loadOrCreateKeystore() throws Exception {
        if (Files.notExists(appDataDir)) Files.createDirectories(appDataDir);
        setUserOnlyPermissions(appDataDir);
        if (Files.exists(keystorePath) && Files.exists(configPath)) {
            // Load existing keystore
            KeyStore ks = KeyStore.getInstance(KEYSTORE_TYPE);
            try (InputStream in = Files.newInputStream(keystorePath)) {
                ks.load(in, getKeystorePassword().toCharArray());
            }
            return ks;
        } else {
            // Generate new keystore and password
            String password = generateRandomPassword(32);
            KeyStore ks = generateKeystore(password);
            try (OutputStream out = Files.newOutputStream(keystorePath, StandardOpenOption.CREATE_NEW)) {
                ks.store(out, password.toCharArray());
            }
            setUserOnlyPermissions(keystorePath);
            // Save password to config
            Properties props = new Properties();
            props.setProperty("keystore.password", password);
            try (OutputStream out = Files.newOutputStream(configPath, StandardOpenOption.CREATE_NEW)) {
                props.store(out, "Keystore config");
            }
            setUserOnlyPermissions(configPath);
            return ks;
        }
    }

    private KeyStore generateKeystore(String password) throws Exception {
        KeyStore ks = KeyStore.getInstance(KEYSTORE_TYPE);
        ks.load(null, null);
        // Generate key pair
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();
        // Generate self-signed cert
        X509Certificate cert = generateSelfSignedCertificate(keyPair);
        ks.setKeyEntry(KEY_ALIAS, keyPair.getPrivate(), password.toCharArray(), new Certificate[]{cert});
        return ks;
    }

    private X509Certificate generateSelfSignedCertificate(KeyPair keyPair) throws Exception {
        long now = System.currentTimeMillis();
        Date notBefore = new Date(now);
        Date notAfter = new Date(now + 3650L * 24 * 60 * 60 * 1000); // 10 years
        X500Name dnName = new X500Name(CERT_DN);
        BigInteger certSerialNumber = BigInteger.valueOf(now);
        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                dnName,
                certSerialNumber,
                notBefore,
                notAfter,
                dnName,
                keyPair.getPublic()
        );
        ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(keyPair.getPrivate());
        return new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(certBuilder.build(contentSigner));
    }

    private String generateRandomPassword(int length) {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private Properties loadConfig() throws IOException {
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(configPath)) {
            props.load(in);
        }
        return props;
    }

    private Path getAppDataDir() {
        String os = System.getProperty("os.name").toLowerCase();
        String userHome = System.getProperty("user.home");
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData != null) return Paths.get(appData, APP_NAME);
            else return Paths.get(userHome, APP_NAME);
        } else if (os.contains("mac")) {
            return Paths.get(userHome, "Library", "Application Support", APP_NAME);
        } else {
            return Paths.get(userHome, ".securetransfer");
        }
    }

    private void setUserOnlyPermissions(Path path) throws IOException {
        try {
            if (Files.isDirectory(path)) {
                Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwx------"));
            } else {
                Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
            }
        } catch (UnsupportedOperationException e) {
            // Windows: ignore, handled by OS
        }
    }
} 