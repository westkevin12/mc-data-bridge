package com.digitalserverhost.plugins.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

public class HashUtils {

    /**
     * Generates a SHA-256 hash of a name and UUID combination.
     * The name is normalized to lowercase to ensure consistency across case changes.
     *
     * @param name The player's name.
     * @param uuid The player's UUID.
     * @return A hexadecimal representation of the SHA-256 hash.
     */
    public static String generateIdentityHash(String name, UUID uuid) {
        if (name == null || uuid == null) {
            return null;
        }

        String input = name.toLowerCase() + ":" + uuid.toString();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(encodedHash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
