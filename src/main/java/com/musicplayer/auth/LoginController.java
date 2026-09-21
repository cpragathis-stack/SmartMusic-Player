package com.musicplayer.auth;

import com.musicplayer.database.DatabaseManager;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;

public class LoginController {

    @FXML private TextField usernameField;
    @FXML private Label errorLabel;

    public LoginController() {
        // Explicit default constructor
    }

    @FXML
    public void initialize() {
        if (errorLabel != null) {
            errorLabel.setText("");
        }
    }

    @FXML
    public void handleLogin() {
        String username = usernameField == null ? "" : usernameField.getText().trim();
        if (username.isBlank()) {
            showError("Enter a username to continue.");
            return;
        }
        int userId = DatabaseManager.getInstance().loginOrCreateOfflineUser(username, "");
        if (userId < 0) {
            showError("Unable to start offline mode for this username.");
            return;
        }
        startSession(userId, username);
        openHome();
    }

    @FXML
    public void handleGuestLogin() {
        int userId = DatabaseManager.getInstance().loginOrCreateOfflineUser("Guest", "");
        if (userId < 0) {
            showError("Unable to start guest mode.");
            return;
        }
        startSession(userId, "Guest");
        openHome();
    }

    private void startSession(int userId, String username) {
        UserSession.createSession(userId, username, "");
        UserSession.persistSession();
    }

    private void showError(String message) {
        if (errorLabel != null) {
            errorLabel.setText(message);
        }
    }

    private void openHome() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainShell.fxml"));
            Parent root = loader.load();
            if (usernameField != null && usernameField.getScene() != null) {
                Stage stage = (Stage) usernameField.getScene().getWindow();
                Scene scene = new Scene(root, 1280, 820);
                stage.setScene(scene);
                stage.setTitle(
                        "SmartBeats - Intelligent Offline Music Player with Mood-Based Recommendation"
                );
                stage.setResizable(true);
                stage.setMinWidth(1080);
                stage.setMinHeight(700);
                stage.centerOnScreen();
            }
        } catch (IOException e) {
            System.err.println("Error opening main shell: " + e.getMessage());
            if (errorLabel != null) {
                errorLabel.setText("Error loading player: " + e.getMessage());
            }
        }
    }
}
