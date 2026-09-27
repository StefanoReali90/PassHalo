package org.spring.passhalo.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Application-level protection for booking personal data.
 * Encryption and lookup keys must be independently generated and stored outside the database.
 */
@Component
public class PiiCryptoService {
    private static final String ENCRYPTION_ALGORITHM = "AES/GCM/NoPadding";
    private static final String LOOKUP_ALGORITHM = "HmacSHA256";
    private static final String VERSION = "v1";
    private static final int AES_KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final SecretKeySpec encryptionKey;
    private final SecretKeySpec lookupKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public PiiCryptoService(
            @Value("${app.security.pii.encryption-key}") String encryptionKeyBase64,
            @Value("${app.security.pii.lookup-key}") String lookupKeyBase64) {
        byte[] encryptionKeyBytes = decodeKey(encryptionKeyBase64, "encryption");
        byte[] lookupKeyBytes = decodeKey(lookupKeyBase64, "lookup");
        if (MessageDigest.isEqual(encryptionKeyBytes, lookupKeyBytes)) {
            throw new IllegalStateException("Booking PII encryption and lookup keys must be different");
        }
        this.encryptionKey = new SecretKeySpec(encryptionKeyBytes, "AES");
        this.lookupKey = new SecretKeySpec(lookupKeyBytes, LOOKUP_ALGORITHM);
    }

    /** Returns a versioned Base64 payload containing a fresh nonce and authenticated ciphertext. */
    public String encrypt(String plainText) {
        if (plainText == null) {
            return null;
        }
        byte[] nonce = new byte[NONCE_BYTES];
        secureRandom.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            Base64.Encoder encoder = Base64.getEncoder();
            return VERSION + "." + encoder.encodeToString(nonce) + "." + encoder.encodeToString(ciphertext);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to encrypt booking data", exception);
        }
    }

    /** Rejects unknown versions, malformed payloads, and ciphertext that fails GCM authentication. */
    public String decrypt(String encryptedValue) {
        if (encryptedValue == null) {
            return null;
        }
        String[] parts = encryptedValue.split("\\.", -1);
        if (parts.length != 3 || !VERSION.equals(parts[0])) {
            throw new IllegalArgumentException("Unsupported or malformed encrypted booking data");
        }
        try {
            byte[] nonce = Base64.getDecoder().decode(parts[1]);
            byte[] ciphertext = Base64.getDecoder().decode(parts[2]);
            if (nonce.length != NONCE_BYTES || ciphertext.length < GCM_TAG_BITS / Byte.SIZE) {
                throw new IllegalArgumentException("Malformed encrypted booking data");
            }
            Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_BITS, nonce));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Unable to decrypt booking data", exception);
        }
    }

    /** Creates a stable keyed lookup value; never use this value as an encryption key. */
    public String emailLookupHash(String email) {
        if (email == null) {
            throw new IllegalArgumentException("Email is required for lookup hashing");
        }
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        try {
            Mac mac = Mac.getInstance(LOOKUP_ALGORITHM);
            mac.init(lookupKey);
            byte[] digest = mac.doFinal(normalizedEmail.getBytes(StandardCharsets.UTF_8));
            return VERSION + ":" + HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to create booking email lookup value", exception);
        }
    }

    private static byte[] decodeKey(String encodedKey, String purpose) {
        final byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(encodedKey);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Booking PII " + purpose + " key must be valid Base64", exception);
        }
        if (keyBytes.length != AES_KEY_BYTES) {
            throw new IllegalStateException("Booking PII " + purpose + " key must decode to 32 bytes");
        }
        return keyBytes;
    }
}
