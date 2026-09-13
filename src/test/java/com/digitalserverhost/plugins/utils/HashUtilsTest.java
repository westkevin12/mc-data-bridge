package com.digitalserverhost.plugins.utils;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class HashUtilsTest {

    private static final String TEST_NAME = "TestPlayer";
    private static final UUID TEST_UUID = UUID.fromString("12345678-1234-1234-1234-123456789abc");
    private static final String TEST_SEED = "super-secret-test-seed-123";

    @Test
    public void testHmacSHA256Generation() {
        String hash = HashUtils.generateIdentityHash(TEST_NAME, TEST_UUID, TEST_SEED);
        assertNotNull(hash);
        assertEquals(64, hash.length()); // SHA-256 / HMAC-SHA256 hex string length is 64 chars
    }

    @Test
    public void testCaseInsensitivity() {
        String hashLower = HashUtils.generateIdentityHash(TEST_NAME.toLowerCase(), TEST_UUID, TEST_SEED);
        String hashUpper = HashUtils.generateIdentityHash(TEST_NAME.toUpperCase(), TEST_UUID, TEST_SEED);
        assertEquals(hashLower, hashUpper);
    }

    @Test
    public void testDualVerificationNewHmac() {
        String hmacHash = HashUtils.generateIdentityHash(TEST_NAME, TEST_UUID, TEST_SEED);
        assertTrue(HashUtils.verifyIdentityHash(hmacHash, TEST_NAME, TEST_UUID, TEST_SEED));
    }

    @Test
    public void testDualVerificationLegacySHA256Fallback() {
        String legacyHash = HashUtils.generateLegacyIdentityHash(TEST_NAME, TEST_UUID, TEST_SEED);
        assertTrue(HashUtils.verifyIdentityHash(legacyHash, TEST_NAME, TEST_UUID, TEST_SEED));
    }

    @Test
    public void testVerificationMismatchFails() {
        String invalidHash = "0000000000000000000000000000000000000000000000000000000000000000";
        assertFalse(HashUtils.verifyIdentityHash(invalidHash, TEST_NAME, TEST_UUID, TEST_SEED));
    }

    @Test
    public void testNullSafety() {
        assertNull(HashUtils.generateIdentityHash(null, TEST_UUID, TEST_SEED));
        assertNull(HashUtils.generateIdentityHash(TEST_NAME, null, TEST_SEED));
        assertFalse(HashUtils.verifyIdentityHash(null, TEST_NAME, TEST_UUID, TEST_SEED));
    }
}
