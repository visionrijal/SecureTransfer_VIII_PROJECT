package com.securetransfer.service.impl;

import com.securetransfer.service.EncryptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Service
public class EncryptionServiceImpl implements EncryptionService {
    private static final Logger logger = LoggerFactory.getLogger(EncryptionServiceImpl.class);
    private static final String ALGORITHM = "AES";
    private static final int KEY_SIZE = 256;
    private static final int KEY_ROTATION_INTERVAL = 24 * 60 * 60 * 1000; // 24 hours

    private final BCryptPasswordEncoder passwordEncoder;
    private final AtomicReference<byte[]> currentKey;
    private final ConcurrentHashMap<String, byte[]> previousKeys;
    private final SecureRandom secureRandom;

    public EncryptionServiceImpl(@Value("${app.encryption.key}") String initialKey) {
        this.passwordEncoder = new BCryptPasswordEncoder();
        this.currentKey = new AtomicReference<>();
        this.previousKeys = new ConcurrentHashMap<>();
        this.secureRandom = new SecureRandom();
        
        try {
            // Initialize with the provided key
            byte[] decodedKey = Base64.getDecoder().decode(initialKey.trim());
            if (decodedKey.length != 32) {
                throw new IllegalArgumentException("Initial encryption key must be 32 bytes for AES-256");
            }
            this.currentKey.set(decodedKey);
            logger.info("Encryption service initialized with provided key");
        } catch (Exception e) {
            logger.error("Failed to initialize encryption service with provided key", e);
            throw new IllegalArgumentException("Invalid encryption key format: " + e.getMessage());
        }
    }

    @Override
    public String hashPassword(String password) {
        if (password == null || password.isEmpty()) {
            logger.warn("Attempted to hash null or empty password");
            throw new IllegalArgumentException("Password cannot be null or empty");
        }
        return passwordEncoder.encode(password);
    }

    @Override
    public boolean verifyPassword(String rawPassword, String hashedPassword) {
        if (rawPassword == null || hashedPassword == null) {
            logger.warn("Attempted to verify password with null values");
            return false;
        }
        return passwordEncoder.matches(rawPassword, hashedPassword);
    }

    @Override
    public String encrypt(String data) {
        try {
            SecretKeySpec secretKey = new SecretKeySpec(currentKey.get(), ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encryptedBytes = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
            String encryptedData = Base64.getEncoder().encodeToString(encryptedBytes);
            logger.debug("Data encrypted successfully");
            return encryptedData;
        } catch (Exception e) {
            logger.error("Error encrypting data", e);
            throw new RuntimeException("Error encrypting data: " + e.getMessage(), e);
        }
    }

    @Override
    public String decrypt(String encryptedData) {
        try {
            // Try current key first
            try {
                return decryptWithKey(encryptedData, currentKey.get());
            } catch (Exception e) {
                // If current key fails, try previous keys
                for (byte[] key : previousKeys.values()) {
                    try {
                        return decryptWithKey(encryptedData, key);
                    } catch (Exception ignored) {
                        // Continue trying other keys
                    }
                }
                throw e; // If all keys fail, throw the original exception
            }
        } catch (Exception e) {
            logger.error("Error decrypting data", e);
            throw new RuntimeException("Error decrypting data: " + e.getMessage(), e);
        }
    }

    private String decryptWithKey(String encryptedData, byte[] key) throws Exception {
        SecretKeySpec secretKey = new SecretKeySpec(key, ALGORITHM);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedData));
        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }

    @Scheduled(fixedRate = KEY_ROTATION_INTERVAL)
    public void rotateKey() {
        try {
            // Generate new key
            byte[] newKey = new byte[32];
            secureRandom.nextBytes(newKey);

            // Store current key in previous keys
            String keyId = Base64.getEncoder().encodeToString(currentKey.get());
            previousKeys.put(keyId, currentKey.get());

            // Update current key
            currentKey.set(newKey);

            // Clean up old keys (keep only last 2)
            if (previousKeys.size() > 2) {
                previousKeys.keySet().stream()
                    .limit(previousKeys.size() - 2)
                    .forEach(previousKeys::remove);
            }

            logger.info("Encryption key rotated successfully");
        } catch (Exception e) {
            logger.error("Failed to rotate encryption key", e);
            throw new RuntimeException("Failed to rotate encryption key", e);
        }
    }

    // --- File-based AES encryption with IV and progress reporting ---
    @Override
    public void encryptFile(File inputFile, File outputFile, SecretKey aesKey, IvParameterSpec iv, Consumer<Double> progressCallback, Supplier<Boolean> isCancelled) throws IOException {
        try (FileInputStream fis = new FileInputStream(inputFile);
             FileOutputStream fos = new FileOutputStream(outputFile)) {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, iv);
            long total = inputFile.length();
            long processed = 0;
            byte[] buffer = new byte[8192];
            int read;
            logger.info("[ENCRYPTFILE] Running on thread: {}", Thread.currentThread().getName());
            logger.info("[ENCRYPTFILE] Input: {} ({} bytes) Output: {}", inputFile.getAbsolutePath(), total, outputFile.getAbsolutePath());
            if (progressCallback != null) { logger.info("[ENCRYPTFILE] Progress callback: 0.0"); progressCallback.accept(0.0); }
            while ((read = fis.read(buffer)) != -1) {
                logger.info("[ENCRYPTFILE] Read {} bytes", read);
                if (isCancelled != null && isCancelled.get()) {
                    logger.info("[ENCRYPTFILE] Cancelled during read loop");
                    break;
                }
                byte[] encryptedChunk = cipher.update(buffer, 0, read);
                if (encryptedChunk != null) {
                    fos.write(encryptedChunk);
                    fos.flush();
                    logger.info("[ENCRYPTFILE] Wrote {} encrypted bytes", encryptedChunk.length);
                }
                processed += read;
                if (progressCallback != null && total > 0) {
                    double prog = Math.min(1.0, (double) processed / total);
                    logger.info("[ENCRYPTFILE] Progress callback: {}", prog);
                    progressCallback.accept(prog);
                }
            }
            // Write any final bytes
            byte[] finalBytes = cipher.doFinal();
            if (finalBytes != null) {
                fos.write(finalBytes);
                fos.flush();
                logger.info("[ENCRYPTFILE] Wrote final {} encrypted bytes", finalBytes.length);
            }
            if (progressCallback != null) { logger.info("[ENCRYPTFILE] Progress callback: 1.0"); progressCallback.accept(1.0); }
            logger.info("[ENCRYPTFILE] Finished successfully");
        } catch (Exception e) {
            logger.error("[ENCRYPTFILE] Exception: {}", e.getMessage(), e);
            throw new IOException("Error encrypting file: " + e.getMessage(), e);
        }
    }

    @Override
    public void decryptFile(File inputFile, File outputFile, SecretKey aesKey, IvParameterSpec iv, Consumer<Double> progressCallback, Supplier<Boolean> isCancelled) throws IOException {
        try (FileInputStream fis = new FileInputStream(inputFile);
             FileOutputStream fos = new FileOutputStream(outputFile)) {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, aesKey, iv);
            long total = inputFile.length();
            long processed = 0;
            byte[] buffer = new byte[8192];
            int read;
            logger.info("[DECRYPTFILE] Running on thread: {}", Thread.currentThread().getName());
            logger.info("[DECRYPTFILE] Input: {} ({} bytes) Output: {}", inputFile.getAbsolutePath(), total, outputFile.getAbsolutePath());
            if (progressCallback != null) { logger.info("[DECRYPTFILE] Progress callback: 0.0"); progressCallback.accept(0.0); }
            while ((read = fis.read(buffer)) != -1) {
                logger.info("[DECRYPTFILE] Read {} bytes", read);
                if (isCancelled != null && isCancelled.get()) {
                    logger.info("[DECRYPTFILE] Cancelled during read loop");
                    break;
                }
                byte[] decryptedChunk = cipher.update(buffer, 0, read);
                if (decryptedChunk != null) {
                    fos.write(decryptedChunk);
                    fos.flush();
                    logger.info("[DECRYPTFILE] Wrote {} decrypted bytes", decryptedChunk.length);
                }
                processed += read;
                if (progressCallback != null && total > 0) {
                    double prog = Math.min(1.0, (double) processed / total);
                    logger.info("[DECRYPTFILE] Progress callback: {}", prog);
                    progressCallback.accept(prog);
                }
            }
            // Write any final bytes
            byte[] finalBytes = cipher.doFinal();
            if (finalBytes != null) {
                fos.write(finalBytes);
                fos.flush();
                logger.info("[DECRYPTFILE] Wrote final {} decrypted bytes", finalBytes.length);
            }
            if (progressCallback != null) { logger.info("[DECRYPTFILE] Progress callback: 1.0"); progressCallback.accept(1.0); }
            logger.info("[DECRYPTFILE] Finished successfully");
        } catch (Exception e) {
            logger.error("[DECRYPTFILE] Exception: {}", e.getMessage(), e);
            throw new IOException("Error decrypting file: " + e.getMessage(), e);
        }
    }

    // --- RSA key/IV encryption (key wrapping) ---
    @Override
    public byte[] encryptKeyAndIvWithRSA(PublicKey publicKey, SecretKey aesKey, IvParameterSpec iv) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(aesKey.getEncoded());
        baos.write(iv.getIV());
        return cipher.doFinal(baos.toByteArray());
    }

    @Override
    public KeyAndIv decryptKeyAndIvWithRSA(PrivateKey privateKey, byte[] encryptedKeyAndIv) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        byte[] decoded = cipher.doFinal(encryptedKeyAndIv);
        byte[] keyBytes = new byte[32];
        byte[] ivBytes = new byte[16];
        System.arraycopy(decoded, 0, keyBytes, 0, 32);
        System.arraycopy(decoded, 32, ivBytes, 0, 16);
        SecretKey aesKey = new javax.crypto.spec.SecretKeySpec(keyBytes, "AES");
        IvParameterSpec iv = new IvParameterSpec(ivBytes);
        return new KeyAndIv(aesKey, iv);
    }

    // --- RSA key pair management ---
    private static final String KEY_DIR = System.getProperty("user.home") + File.separator + ".securetransfer";
    private static final String PUB_KEY_FILE = KEY_DIR + File.separator + "rsa_public.key";
    private static final String PRIV_KEY_FILE = KEY_DIR + File.separator + "rsa_private.key";
    private KeyPair rsaKeyPair;

    @Override
    public KeyPair generateRSAKeyPair() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(4096);
            KeyPair keyPair = keyGen.generateKeyPair();
            // Save to disk
            File dir = new File(KEY_DIR);
            if (!dir.exists()) dir.mkdirs();
            try (FileOutputStream pubOut = new FileOutputStream(PUB_KEY_FILE);
                 FileOutputStream privOut = new FileOutputStream(PRIV_KEY_FILE)) {
                pubOut.write(keyPair.getPublic().getEncoded());
                privOut.write(keyPair.getPrivate().getEncoded());
            }
            this.rsaKeyPair = keyPair;
            return keyPair;
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate RSA key pair", e);
        }
    }

    @Override
    public PublicKey getPublicKey() {
        ensureRSAKeyPairLoaded();
        return rsaKeyPair.getPublic();
    }

    @Override
    public PrivateKey getPrivateKey() {
        ensureRSAKeyPairLoaded();
        return rsaKeyPair.getPrivate();
    }

    private void ensureRSAKeyPairLoaded() {
        if (rsaKeyPair != null) return;
        try {
            File pubFile = new File(PUB_KEY_FILE);
            File privFile = new File(PRIV_KEY_FILE);
            if (!pubFile.exists() || !privFile.exists()) {
                rsaKeyPair = generateRSAKeyPair();
                return;
            }
            byte[] pubBytes = java.nio.file.Files.readAllBytes(pubFile.toPath());
            byte[] privBytes = java.nio.file.Files.readAllBytes(privFile.toPath());
            KeyFactory kf = KeyFactory.getInstance("RSA");
            PublicKey pub = kf.generatePublic(new X509EncodedKeySpec(pubBytes));
            PrivateKey priv = kf.generatePrivate(new PKCS8EncodedKeySpec(privBytes));
            rsaKeyPair = new KeyPair(pub, priv);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load RSA key pair", e);
        }
    }
} 