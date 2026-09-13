package com.digitalserverhost.plugins.utils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

public class HashUtils {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /**
     * Generates a keyed identity hash. Uses HmacSHA256 when a secret seed is provided,
     * or legacy salted SHA-256 if seed is empty or null.
     *
     * @param name The player's name.
     * @param uuid The player's UUID.
     * @param seed Secret server seed.
     * @return Hexadecimal string representation of the hash.
     */
    public static String generateIdentityHash(String name, UUID uuid, String seed) {
        if (name == null || uuid == null) {
            return null;
        }

        String normalizedName = name.toLowerCase();

        if (seed != null && !seed.isEmpty()) {
            try {
                String message = normalizedName + ":" + uuid.toString();
                SecretKeySpec keySpec = new SecretKeySpec(seed.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
                Mac mac = Mac.getInstance(HMAC_ALGORITHM);
                mac.init(keySpec);
                byte[] rawHmac = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
                return bytesToHex(rawHmac);
            } catch (NoSuchAlgorithmException | InvalidKeyException e) {
                // Fallback to SHA-256 on unexpected HMAC initialization failure
            }
        }

        return generateLegacyIdentityHash(normalizedName, uuid, seed);
    }

    public static String generateIdentityHash(String name, UUID uuid) {
        return generateIdentityHash(name, uuid, null);
    }

    /**
     * Legacy concatenated SHA-256 identity hash computation for backward compatibility.
     */
    public static String generateLegacyIdentityHash(String normalizedName, UUID uuid, String seed) {
        if (normalizedName == null || uuid == null) {
            return null;
        }
        String input = normalizedName.toLowerCase() + ":" + uuid.toString() + (seed != null ? ":" + seed : "");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(encodedHash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

    /**
     * Verifies stored identity hash against current HmacSHA256 and legacy SHA-256 formats.
     */
    public static boolean verifyIdentityHash(String storedHash, String name, UUID uuid, String seed) {
        if (storedHash == null || name == null || uuid == null) {
            return false;
        }
        String currentHmac = generateIdentityHash(name, uuid, seed);
        if (storedHash.equalsIgnoreCase(currentHmac)) {
            return true;
        }
        String legacyHash = generateLegacyIdentityHash(name.toLowerCase(), uuid, seed);
        return storedHash.equalsIgnoreCase(legacyHash);
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
