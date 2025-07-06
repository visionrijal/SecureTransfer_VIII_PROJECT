package com.securetransfer.service.impl;

import com.securetransfer.service.EncryptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

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
} 