package com.securetransfer.controller.ui;

import com.securetransfer.model.User;
import com.securetransfer.service.AuthenticationService;
import com.securetransfer.util.ToastNotification;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DashboardController {
    @FXML private Label usernameLabel;
    @FXML private Button logoutButton;
    @FXML private Button sendFileButton;
    @FXML private Button receiveFileButton;
    @FXML private TableView<?> queueTable;
    @FXML private Label statusLabel;

    @Autowired
    private AuthenticationService authenticationService;

    private User currentUser;

    public void setCurrentUser(User user) {
        this.currentUser = user;
        usernameLabel.setText("Welcome, " + user.getUsername());
    }

    @FXML
    public void initialize() {
        setupQueueTable();
    }

    private void setupQueueTable() {
        // TODO: Initialize queue table with data
    }

    @FXML
    private void handleLogout() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            Parent root = loader.load();
            Scene scene = new Scene(root);
            Stage stage = (Stage) logoutButton.getScene().getWindow();
            stage.setScene(scene);
        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("Error during logout");
        }
    }

    @FXML
    private void handleSendFile() {
        // TODO: Implement file sending dialog
        statusLabel.setText("Send file functionality coming soon");
    }

    @FXML
    private void handleReceiveFile() {
        // TODO: Implement file receiving dialog
        statusLabel.setText("Receive file functionality coming soon");
    }

    public void showLoginSuccessToast() {
        ToastNotification.show((Stage) usernameLabel.getScene().getWindow(), "Login successful!", ToastNotification.NotificationType.SUCCESS, Duration.seconds(3));
    }
} 