package com.securetransfer.util;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class EncryptionKeyGenerator {
    public static String generateEncryptionKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256); // Using 256-bit key
            SecretKey secretKey = keyGen.generateKey();
            return Base64.getEncoder().encodeToString(secretKey.getEncoded());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to generate encryption key", e);
        }
    }

    public static void main(String[] args) {
        System.out.println("Generated Encryption Key: " + generateEncryptionKey());
    }
} 