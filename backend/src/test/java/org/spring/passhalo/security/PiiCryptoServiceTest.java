package org.spring.passhalo.security;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PiiCryptoServiceTest {
    private static final String ENCRYPTION_KEY = Base64.getEncoder().encodeToString(new byte[]{
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1
    });
    private static final String LOOKUP_KEY = Base64.getEncoder().encodeToString(new byte[]{
            2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
            2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2
    });

    private final PiiCryptoService crypto = new PiiCryptoService(ENCRYPTION_KEY, LOOKUP_KEY);

    @Test
    void encryptsAndDecryptsUtf8Text() {
        String plaintext = "Élodie O’Connor — test@example.it";

        String encrypted = crypto.encrypt(plaintext);

        assertTrue(encrypted.startsWith("v1."));
        assertNotEquals(plaintext, encrypted);
        assertEquals(plaintext, crypto.decrypt(encrypted));
    }

    @Test
    void usesFreshNonceWhenEncryptingSameValueTwice() {
        String first = crypto.encrypt("same value");
        String second = crypto.encrypt("same value");

        assertNotEquals(first, second);
        assertEquals("same value", crypto.decrypt(first));
        assertEquals("same value", crypto.decrypt(second));
    }

    @Test
    void rejectsTamperedCiphertext() {
        String encrypted = crypto.encrypt("private data");
        String[] parts = encrypted.split("\\.");
        char replacement = parts[2].charAt(0) == 'A' ? 'B' : 'A';
        parts[2] = replacement + parts[2].substring(1);

        assertThrows(IllegalStateException.class, () -> crypto.decrypt(String.join(".", parts)));
    }

    @Test
    void rejectsCiphertextEncryptedWithAnotherKey() {
        PiiCryptoService anotherKey = new PiiCryptoService(LOOKUP_KEY, ENCRYPTION_KEY);
        String encrypted = crypto.encrypt("private data");

        assertThrows(IllegalStateException.class, () -> anotherKey.decrypt(encrypted));
    }

    @Test
    void rejectsMalformedAndUnknownPayloadVersions() {
        assertThrows(IllegalArgumentException.class, () -> crypto.decrypt("v2.payload.value"));
        assertThrows(IllegalArgumentException.class, () -> crypto.decrypt("v1.invalid"));
    }

    @Test
    void passesNullThroughEncryptionAndDecryption() {
        assertNull(crypto.encrypt(null));
        assertNull(crypto.decrypt(null));
    }

    @Test
    void normalizesEmailBeforeComputingStableLookupHash() {
        String expected = crypto.emailLookupHash("person@example.it");

        assertEquals(expected, crypto.emailLookupHash(" PERSON@EXAMPLE.IT "));
        assertTrue(expected.startsWith("v1:"));
        assertEquals(67, expected.length());
        assertNotEquals(expected, crypto.emailLookupHash("other@example.it"));
    }

    @Test
    void rejectsMissingEmailForLookupHash() {
        assertThrows(IllegalArgumentException.class, () -> crypto.emailLookupHash(null));
    }

    @Test
    void rejectsInvalidOrReusedKeysAtConstruction() {
        assertThrows(IllegalStateException.class,
                () -> new PiiCryptoService("invalid", LOOKUP_KEY));
        assertThrows(IllegalStateException.class,
                () -> new PiiCryptoService(ENCRYPTION_KEY, ENCRYPTION_KEY));
    }
}
