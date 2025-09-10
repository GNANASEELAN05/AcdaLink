package com.example.acadlink;

import java.util.HashMap;
import java.util.Map;

/**
 * Simple in-memory faculty credential manager.
 *
 * - Add/remove credentials at runtime via addCredential/removeCredential.
 * - addDefaultCredentialsIfEmpty() seeds two sample accounts if none are present.
 *
 * WARNING: storing credentials as plaintext in the APK is insecure for production. Use secure auth
 *          (server / encrypted storage / Firebase Auth) for real apps.
 */
public class FacultyAuthManager {

    // key: normalized email (lowercase, trimmed), value: password (plaintext)
    private static final Map<String, String> credentials = new HashMap<>();

    private FacultyAuthManager() { /* no instances */ }

    public static synchronized void addCredential(String email, String password) {
        if (email == null || password == null) return;
        credentials.put(email.trim().toLowerCase(), password);
    }

    public static synchronized boolean removeCredential(String email) {
        if (email == null) return false;
        return credentials.remove(email.trim().toLowerCase()) != null;
    }

    public static synchronized boolean isValid(String email, String password) {
        if (email == null || password == null) return false;
        String stored = credentials.get(email.trim().toLowerCase());
        return stored != null && stored.equals(password);
    }

    public static synchronized void clearAll() {
        credentials.clear();
    }

    /**
     * Adds some sample accounts if the store is empty.
     * Modify or remove this method as you like.
     */
    public static synchronized void addDefaultCredentialsIfEmpty() {
        if (credentials.isEmpty()) {
            // Example accounts - change these to real faculty emails/passwords for testing

        }
    }

    public static synchronized Map<String, String> getAll() {
        return new HashMap<>(credentials);
    }
}
