package com.securetransfer.controller.ui;

import com.securetransfer.service.UserService;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import java.io.IOException;
import java.util.regex.Pattern;

@Controller
public class RegistrationController extends BaseController {
    private static final Logger logger = LoggerFactory.getLogger(RegistrationController.class);
    
    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private PasswordField confirmPasswordField;
    @FXML
    private Button registerButton;
    @FXML
    private Button backButton;
    @FXML
    private Label errorLabel;
    @FXML
    private Label passwordStrengthLabel;
    @FXML
    private Label matchIndicator;
    @FXML
    private Label successLabel;

    @Autowired
    private UserService userService;

    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
        "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=])(?=\\S+$).{8,}$"
    );

    @FXML
    public void initialize() {
        // Clear any existing messages
        clearMessages();
        
        // Add listeners to clear error messages when user types
        usernameField.textProperty().addListener((obs, oldVal, newVal) -> {
            errorLabel.setVisible(false);
            errorLabel.setManaged(false);
        });
        
        passwordField.textProperty().addListener((obs, oldVal, newVal) -> {
            checkPasswordStrength();
            errorLabel.setVisible(false);
            errorLabel.setManaged(false);
        });
        
        confirmPasswordField.textProperty().addListener((obs, oldVal, newVal) -> {
            validatePasswordMatch();
            errorLabel.setVisible(false);
            errorLabel.setManaged(false);
        });

        usernameField.setOnKeyPressed(event -> {
            if (event.getCode().toString().equals("ENTER")) {
                passwordField.requestFocus();
            }
        });

        passwordField.setOnKeyPressed(event -> {
            if (event.getCode().toString().equals("ENTER")) {
                confirmPasswordField.requestFocus();
            }
        });

        confirmPasswordField.setOnKeyPressed(event -> {
            if (event.getCode().toString().equals("ENTER")) {
                handleRegister();
            }
        });
    }
    
    @FXML
    private void handleRegister() {
        clearMessages();
        
        String username = usernameField.getText();
        String password = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();
        
        // Validate input
        if (username.isEmpty()) {
            showError("Username is required");
            return;
        }
        
        if (password.isEmpty()) {
            showError("Password is required");
            return;
        }
        
        if (!password.equals(confirmPassword)) {
            showError("Passwords do not match");
            return;
        }
        
        try {
            userService.register(username, password);
            showSuccess("Registration successful! Redirecting to login...");
            loadLoginScreen();
        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
        } catch (Exception e) {
            showError("Registration failed: " + e.getMessage());
        }
    }
    
    @FXML
    private void handleBack() {
        loadLoginScreen();
    }
    
    @FXML
    private void checkPasswordStrength() {
        String password = passwordField.getText();
        if (password.isEmpty()) {
            passwordStrengthLabel.setVisible(false);
            passwordStrengthLabel.setManaged(false);
            return;
        }
        
        int strength = calculatePasswordStrength(password);
        passwordStrengthLabel.setVisible(true);
        passwordStrengthLabel.setManaged(true);
        
        if (strength < 2) {
            passwordStrengthLabel.setText("Weak password");
            passwordStrengthLabel.getStyleClass().setAll("password-strength-label", "weak");
        } else if (strength < 3) {
            passwordStrengthLabel.setText("Medium password");
            passwordStrengthLabel.getStyleClass().setAll("password-strength-label", "medium");
        } else {
            passwordStrengthLabel.setText("Strong password");
            passwordStrengthLabel.getStyleClass().setAll("password-strength-label", "strong");
        }
    }
    
    @FXML
    private void validatePasswordMatch() {
        String password = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();
        
        if (confirmPassword.isEmpty()) {
            matchIndicator.setVisible(false);
            matchIndicator.setManaged(false);
            return;
        }
        
        matchIndicator.setVisible(true);
        matchIndicator.setManaged(true);
        
        if (password.equals(confirmPassword)) {
            matchIndicator.setText("Passwords match");
            matchIndicator.getStyleClass().setAll("password-match-label", "match");
        } else {
            matchIndicator.setText("Passwords do not match");
            matchIndicator.getStyleClass().setAll("password-match-label", "no-match");
        }
    }
    
    private int calculatePasswordStrength(String password) {
        int strength = 0;
        
        // Length check
        if (password.length() >= 8) strength++;
        
        // Contains number
        if (password.matches(".*\\d.*")) strength++;
        
        // Contains special character
        if (password.matches(".*[!@#$%^&*(),.?\":{}|<>].*")) strength++;
        
        // Contains uppercase
        if (password.matches(".*[A-Z].*")) strength++;
        
        return strength;
    }
    
    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
        successLabel.setVisible(false);
        successLabel.setManaged(false);
    }
    
    private void showSuccess(String message) {
        successLabel.setText(message);
        successLabel.setVisible(true);
        successLabel.setManaged(true);
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }
    
    private void clearMessages() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        successLabel.setVisible(false);
        successLabel.setManaged(false);
    }

    @Override
    protected void loadLoginScreen() {
        try {
            logger.debug("Loading login screen");
            FXMLLoader loader = createFxmlLoader("/fxml/login.fxml");
            Parent root = loader.load();
            
            Object controller = loader.getController();
            if (controller instanceof BaseController) {
                ((BaseController) controller).setSpringContext(springContext);
            }
            
            Scene scene = new Scene(root);
            Stage stage = (Stage) registerButton.getScene().getWindow();
            stage.setScene(scene);
        } catch (Exception e) {
            logger.error("Failed to load login screen", e);
            errorLabel.setText("Failed to load login screen: " + e.getMessage());
        }
    }

    @Override
    protected void loadRegistrationScreen() {
        try {
            logger.debug("Loading registration screen");
            FXMLLoader loader = createFxmlLoader("/fxml/registration.fxml");
            Parent root = loader.load();
            
            Object controller = loader.getController();
            if (controller instanceof BaseController) {
                ((BaseController) controller).setSpringContext(springContext);
            }
            
            Scene scene = new Scene(root);
            Stage stage = (Stage) registerButton.getScene().getWindow();
            stage.setScene(scene);
        } catch (Exception e) {
            logger.error("Failed to load registration screen", e);
            errorLabel.setText("Failed to load registration screen: " + e.getMessage());
        }
    }
} 