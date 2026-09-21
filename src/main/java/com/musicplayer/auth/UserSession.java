package com.musicplayer.auth;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

public final class UserSession {

    private static int currentUserId = -1;
    private static String currentUser = null;
    private static String currentUserEmail = null;
    private static final String SESSION_FILE = System.getProperty("user.dir") + "/session.properties";

    private UserSession() {
        // Prevent object creation
    }

    // ==============================
    // CREATE SESSION
    // ==============================

    public static void createSession(int userId, String username, String email) {
        currentUserId = userId;
        currentUser = username;
        currentUserEmail = email;
    }

    // Persist session to a properties file
    public static void persistSession() {
        Properties props = new Properties();
        props.setProperty("userId", Integer.toString(currentUserId));
        props.setProperty("username", currentUser != null ? currentUser : "");
        props.setProperty("email", currentUserEmail != null ? currentUserEmail : "");
        try (FileOutputStream out = new FileOutputStream(SESSION_FILE)) {
            props.store(out, "User session");
        } catch (IOException e) {
            System.err.println("Failed to persist session: " + e.getMessage());
        }
    }

    // Load session from the properties file
    public static void loadSession() {
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(SESSION_FILE)) {
            props.load(in);
            String idStr = props.getProperty("userId", "-1");
            int id = Integer.parseInt(idStr);
            String username = props.getProperty("username", "");
            String email = props.getProperty("email", "");
            if (id != -1 && !username.isEmpty()) {
                createSession(id, username, email);
            }
        } catch (IOException e) {
            // No session file or error reading – ignore
        }
    }

    // ==============================
    // USERNAME
    // ==============================

    public static void setCurrentUser(String username) {
        currentUser = username;
    }

    public static String getCurrentUser() {
        return currentUser;
    }

    // ==============================
    // USER ID
    // ==============================

    public static void setCurrentUserId(int id) {
        currentUserId = id;
    }

    public static int getCurrentUserId() {
        return currentUserId;
    }

    // ==============================
    // EMAIL
    // ==============================

    public static void setCurrentUserEmail(String email) {
        currentUserEmail = email;
    }

    public static String getCurrentUserEmail() {
        return currentUserEmail;
    }

    // ==============================
    // LOGIN STATUS
    // ==============================

    public static boolean isLoggedIn() {
        return currentUserId != -1 && currentUser != null;
    }

    // ==============================
    // LOGOUT
    // ==============================

    public static void logout() {
        currentUserId = -1;
        currentUser = null;
        currentUserEmail = null;
    }
}