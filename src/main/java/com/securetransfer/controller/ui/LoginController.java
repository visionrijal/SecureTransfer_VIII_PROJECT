package com.securetransfer.controller.ui;

import com.securetransfer.model.User;
import com.securetransfer.service.UserService;
import com.securetransfer.util.ToastNotification;
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
import javafx.util.Duration;

import java.io.IOException;

@Controller
public class LoginController extends BaseController {
    private static final Logger logger = LoggerFactory.getLogger(LoginController.class);
    
    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Button loginButton;
    @FXML
    private Button registerButton;
    @FXML
    private Label errorLabel;
    @FXML
    private Label successLabel;
    @FXML
    private Label usernameErrorLabel;
    @FXML
    private Label passwordErrorLabel;

    @Autowired
    private UserService userService;

    @FXML
    public void initialize() {
        clearMessages();
        
        usernameField.textProperty().addListener((obs, oldVal, newVal) -> {
            usernameErrorLabel.setVisible(false);
            usernameErrorLabel.setManaged(false);
            errorLabel.setVisible(false);
            errorLabel.setManaged(false);
        });
        
        passwordField.textProperty().addListener((obs, oldVal, newVal) -> {
            passwordErrorLabel.setVisible(false);
            passwordErrorLabel.setManaged(false);
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
                handleLogin();
            }
        });
    }

    @FXML
    private void handleLogin() {
        clearMessages();
        
        String username = usernameField.getText();
        String password = passwordField.getText();
        
        if (username.isEmpty()) {
            showUsernameError("Username is required");
            return;
        }
        
        if (password.isEmpty()) {
            showPasswordError("Password is required");
            return;
        }
        
        try {
            User user = userService.authenticate(username, password);
            if (user != null) {
                User freshUser = userService.findByUsername(username).orElse(user);
                
                com.securetransfer.util.UserSession.getInstance().forceReset();
                
                com.securetransfer.util.UserSession.getInstance().setCurrentUser(freshUser);
                logger.info("User session set for: {}", freshUser.getUsername());
                showSuccess("Login successful!");
                loadMainScreenWithToast();
            } else {
                showError("Invalid username or password");
            }
        } catch (Exception e) {
            showError("Login failed: " + e.getMessage());
        }
    }

    @FXML
    private void handleRegister() {
        loadRegistrationScreen();
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }
    
    private void showSuccess(String message) {
        successLabel.setText(message);
        successLabel.setVisible(true);
        successLabel.setManaged(true);
    }
    
    private void showUsernameError(String message) {
        usernameErrorLabel.setText(message);
        usernameErrorLabel.setVisible(true);
        usernameErrorLabel.setManaged(true);
    }
    
    private void showPasswordError(String message) {
        passwordErrorLabel.setText(message);
        passwordErrorLabel.setVisible(true);
        passwordErrorLabel.setManaged(true);
    }
    
    private void clearMessages() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        successLabel.setVisible(false);
        successLabel.setManaged(false);
        usernameErrorLabel.setVisible(false);
        usernameErrorLabel.setManaged(false);
        passwordErrorLabel.setVisible(false);
        passwordErrorLabel.setManaged(false);
    }

    private void showToast(String message, ToastNotification.NotificationType type) {
        ToastNotification.show((Stage) loginButton.getScene().getWindow(), message, type, Duration.seconds(3), 70);
    }

    private void loadMainScreenWithToast() {
        try {
            logger.debug("Loading main screen with toast");
            FXMLLoader loader = createFxmlLoader("/fxml/main.fxml");
            Parent root = loader.load();
            
            Object controller = loader.getController();
            if (controller instanceof BaseController) {
                ((BaseController) controller).setSpringContext(springContext);
            }
            
            Scene scene = new Scene(root);
            Stage stage = (Stage) loginButton.getScene().getWindow();
            stage.setScene(scene);
            ToastNotification.show(stage, "Login successful!", ToastNotification.NotificationType.SUCCESS, Duration.seconds(3), (-60));
        } catch (Exception e) {
            logger.error("Failed to load main screen", e);
            errorLabel.setText("Failed to load main screen: " + e.getMessage());
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
            Stage stage = (Stage) loginButton.getScene().getWindow();
            stage.setScene(scene);
        } catch (Exception e) {
            logger.error("Failed to load login screen", e);
            errorLabel.setText("Failed to load login screen: " + e.getMessage());
        }
    }
} 