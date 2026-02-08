package com.securetransfer.service;

import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.io.File;
import java.io.IOException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Service interface for handling encryption and password hashing operations.
 */
public interface EncryptionService {
    /**
     * Hashes a password using a secure algorithm.
     * @param password The plain text password to hash
     * @return The hashed password
     */
    String hashPassword(String password);

    /**
     * Verifies if a plain text password matches a hashed password.
     * @param rawPassword The plain text password to verify
     * @param hashedPassword The hashed password to compare against
     * @return true if the passwords match, false otherwise
     */
    boolean verifyPassword(String rawPassword, String hashedPassword);

    /**
     * Encrypts data using AES encryption.
     * @param data The data to encrypt
     * @return The encrypted data as a Base64 encoded string
     */
    String encrypt(String data);

    /**
     * Decrypts previously encrypted data.
     * @param encryptedData The encrypted data as a Base64 encoded string
     * @return The decrypted data
     */
    String decrypt(String encryptedData);

    /**
     * Encrypts a file using AES with the provided key and IV. Reports progress via callback (0.0-1.0).
     * Accepts a cancellation supplier to abort if needed.
     */
    void encryptFile(File inputFile, File outputFile, SecretKey aesKey, IvParameterSpec iv, Consumer<Double> progressCallback, Supplier<Boolean> isCancelled) throws IOException;

    /**
     * Decrypts a file using AES with the provided key and IV. Reports progress via callback (0.0-1.0).
     * Accepts a cancellation supplier to abort if needed.
     */
    void decryptFile(File inputFile, File outputFile, SecretKey aesKey, IvParameterSpec iv, Consumer<Double> progressCallback, Supplier<Boolean> isCancelled) throws IOException;

    /**
     * Encrypts the AES key and IV using the provided RSA public key (for secure transfer).
     * Returns the encrypted key+IV as a byte array.
     */
    byte[] encryptKeyAndIvWithRSA(PublicKey publicKey, SecretKey aesKey, IvParameterSpec iv) throws Exception;

    /**
     * Decrypts the AES key and IV using the provided RSA private key.
     * Returns a KeyAndIv DTO (Lombok record).
     */
    KeyAndIv decryptKeyAndIvWithRSA(PrivateKey privateKey, byte[] encryptedKeyAndIv) throws Exception;

    /**
     * Generates a new RSA key pair (for the device).
     */
    KeyPair generateRSAKeyPair();

    /**
     * Gets the device's RSA public key.
     */
    PublicKey getPublicKey();

    /**
     * Gets the device's RSA private key.
     */
    PrivateKey getPrivateKey();

    /**
     * DTO for holding AES key and IV (Lombok record).
     */
    record KeyAndIv(SecretKey key, IvParameterSpec iv) {}
} 