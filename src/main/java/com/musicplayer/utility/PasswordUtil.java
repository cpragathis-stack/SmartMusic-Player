package com.musicplayer.utility;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Local password hashing for the fully offline app.
 *
 * Passwords are never stored in plain text. Each password is salted with a
 * random 16-byte salt and hashed with SHA-256. The stored value has the form
 * {@code <salt-hex>:<hash-hex>}. Legacy rows that were saved in plain text
 * (before hashing was introduced) are still accepted during login and then
 * transparently upgraded to a hashed value.
 */
public final class PasswordUtil {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int SALT_BYTES = 16;

    private PasswordUtil() {
    }

    /** Salted SHA-256 hash of {@code password}. */
    public static String hash(String password) {
        String safe = password == null ? "" : password;
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        return encode(salt, digest(safe, salt));
    }

    /**
     * Verifies a password against a stored value. Accepts both the hashed
     * {@code salt:hash} format and legacy plain-text values.
     */
    public static boolean verify(String password, String stored) {
        if (stored == null || stored.isBlank()) {
            return password == null || password.isEmpty();
        }
        String safe = password == null ? "" : password;
        int separator = stored.indexOf(':');
        if (separator > 0) {
            try {
                byte[] salt = HexFormat.of().parseHex(stored.substring(0, separator));
                String expected = stored.substring(separator + 1);
                return expected.equalsIgnoreCase(HexFormat.of().formatHex(digest(safe, salt)));
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
        return safe.equals(stored);
    }

    /** True when the stored value is in the salted {@code salt:hash} form. */
    public static boolean isHashed(String stored) {
        return stored != null && stored.indexOf(':') > 0;
    }

    private static byte[] digest(String password, byte[] salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            return md.digest(password.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String encode(byte[] salt, byte[] hash) {
        return HexFormat.of().formatHex(salt) + ":" + HexFormat.of().formatHex(hash);
    }
}