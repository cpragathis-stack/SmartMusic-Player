package com.musicplayer.auth;

import com.musicplayer.database.DatabaseManager;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import java.time.LocalDateTime;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import java.io.IOException;

@SuppressWarnings({"SqlResolve", "SqlNoDataSourceInspection", "SqlDialectInspection"})
public class RegisterController {

    @FXML private TextField usernameField;
    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label errorLabel;

    public RegisterController() {
        // Explicit constructor for JavaFX FXML Loader
    }

    @FXML
    public void initialize() {
        if (errorLabel != null) {
            errorLabel.setText("");
        }
    }

    @FXML
    public void handleRegister() {
        if (usernameField == null || passwordField == null || confirmPasswordField == null) return;

        String username = usernameField.getText().trim();
        String email = (emailField != null && !emailField.getText().trim().isEmpty())
                ? emailField.getText().trim()
                : username + "@musicplayer.local";
        String password = passwordField.getText().trim();
        String confirm = confirmPasswordField.getText().trim();

        if (errorLabel != null) {
            errorLabel.setStyle("-fx-text-fill: #EF4444;");
        }

        if (username.isEmpty() || password.isEmpty() || confirm.isEmpty()) {
            if (errorLabel != null) {
                errorLabel.setText("Please fill in all required fields.");
            }
            return;
        }
        if (username.length() < 3) {
            if (errorLabel != null) {
                errorLabel.setText("Username must be at least 3 characters.");
            }
            return;
        }
        if (password.length() < 3) {
            if (errorLabel != null) {
                errorLabel.setText("Password must be at least 3 characters.");
            }
            return;
        }
        if (!password.equals(confirm)) {
            if (errorLabel != null) {
                errorLabel.setText("Passwords do not match.");
            }
            return;
        }

        String createdAt = LocalDateTime.now().toString();
        boolean success = DatabaseManager.getInstance().registerUser(username, email, password, createdAt);

        if (success) {
            System.out.println("User registered successfully: " + username);
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainShell.fxml"));
                Parent root = loader.load();
                if (usernameField.getScene() != null) {
                    Stage stage = (Stage) usernameField.getScene().getWindow();
                    Scene scene = new Scene(root, 1280, 820);
                    stage.setScene(scene);
                    stage.setTitle("Music Player");
                    stage.setResizable(true);
                    stage.setMinWidth(1080);
                    stage.setMinHeight(700);
                    stage.centerOnScreen();
                }
            } catch (IOException e) {
                System.err.println("Error opening main shell: " + e.getMessage());
                if (errorLabel != null) {
                    errorLabel.setText("Account created! Go to login: " + e.getMessage());
                }
            }
        } else {
            if (errorLabel != null) {
                errorLabel.setText("This Username or Email is already registered. Click 'Sign in' below to log in!");
            }
        }
    }

    @FXML
    public void goToLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Login.fxml"));
            Parent root = loader.load();
            if (usernameField != null && usernameField.getScene() != null) {
                Stage stage = (Stage) usernameField.getScene().getWindow();
                stage.setScene(new Scene(root, 940, 620));
                stage.setTitle("Music Player - Login");
                stage.setResizable(true);
                stage.centerOnScreen();
            }
        } catch (IOException e) {
            System.err.println("Error navigating to login: " + e.getMessage());
            if (errorLabel != null) {
                errorLabel.setText("Error loading login page: " + e.getMessage());
            }
        }
    }

    @FXML
    public void handleBackToLogin() {
        goToLogin();
    }
}