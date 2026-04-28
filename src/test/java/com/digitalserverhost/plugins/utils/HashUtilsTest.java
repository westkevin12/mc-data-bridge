package com.digitalserverhost.plugins.utils;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

public class HashUtilsTest {

    @Test
    public void testGenerateIdentityHash() {
        UUID uuid = UUID.randomUUID();
        String name = "Notch";
        
        String hash1 = HashUtils.generateIdentityHash(name, uuid);
        String hash2 = HashUtils.generateIdentityHash(name.toLowerCase(), uuid);
        String hash3 = HashUtils.generateIdentityHash(name.toUpperCase(), uuid);
        
        assertNotNull(hash1);
        assertEquals(64, hash1.length(), "Hash should be 64 characters (SHA-256 hex)");
        assertEquals(hash1, hash2, "Hash should be case-insensitive for the name");
        assertEquals(hash1, hash3, "Hash should be case-insensitive for the name");
    }

    @Test
    public void testDifferentIdentitiesHaveDifferentHashes() {
        UUID uuid1 = UUID.randomUUID();
        UUID uuid2 = UUID.randomUUID();
        String name = "Notch";
        
        String hash1 = HashUtils.generateIdentityHash(name, uuid1);
        String hash2 = HashUtils.generateIdentityHash(name, uuid2);
        
        assertNotEquals(hash1, hash2, "Different UUIDs should have different hashes even with same name");
    }

    @Test
    public void testNullHandling() {
        assertNull(HashUtils.generateIdentityHash(null, UUID.randomUUID()));
        assertNull(HashUtils.generateIdentityHash("Notch", null));
    }
}
