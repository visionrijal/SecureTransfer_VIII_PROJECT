package com.securetransfer.controller.ui;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.text.Text;
import javafx.scene.layout.*;
import javafx.scene.shape.SVGPath;
import javafx.scene.Node;
import javafx.animation.*;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import com.securetransfer.util.UserSession;
import javafx.application.Platform;
import com.securetransfer.model.User;
import com.securetransfer.util.ToastNotification;

@Controller
public class MainController extends BaseController {
    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    @FXML
    private StackPane contentArea;
    @FXML
    private Button sendNavBtn;
    @FXML
    private Button receiveNavBtn;
    @FXML
    private BorderPane rootPane;

    @FXML
    public void initialize() {
        logger.info("Initializing MainController");
        // Load the welcome page by default
        Platform.runLater(this::showWelcomePage);
    }

    // ================================================
    // Navigation Logic
    // ================================================

    @FXML
    public void showWelcomePage() {
        logger.info("Showing welcome page");
        loadDynamicContent("/fxml/welcome.fxml", "welcome");
    }

    @FXML
    public void showSendFiles() {
        logger.info("Showing send files content");
        loadDynamicContent("/fxml/send-files.fxml", "send-files");
    }

    @FXML
    public void showReceiveFiles() {
        logger.info("Showing receive files content");
        loadDynamicContent("/fxml/receive-files.fxml", "receive-files");
    }

    private void loadDynamicContent(String fxmlPath, String activePage) {
        try {
            FXMLLoader loader = createFxmlLoader(fxmlPath);
            Parent content = loader.load();

            // Set spring context for the new controller
            Object controller = loader.getController();
            if (controller instanceof BaseController) {
                ((BaseController) controller).setSpringContext(springContext);
            }

            // Handle specific controller injections if needed
            if (controller instanceof SendFilesController) {
                ((SendFilesController) controller).setMainController(this);
            } else if (controller instanceof WelcomeController) {
                ((WelcomeController) controller).setMainController(this);
            }

            // Clear and swap
            contentArea.getChildren().setAll(content);
            updateNavbarActiveState(activePage);

            logger.info("Successfully loaded content: {}", fxmlPath);
        } catch (Exception e) {
            logger.error("Failed to load content from {}: {}", fxmlPath, e.getMessage());
            e.printStackTrace();
            // Show alert if stage is available, otherwise log
            Platform.runLater(() -> {
                try {
                    showErrorAlert("Navigation Error", "Failed to load requested page.");
                } catch (Exception ignored) {
                }
            });
        }
    }

    private void updateNavbarActiveState(String activePage) {
        // Clear active classes
        if (sendNavBtn != null)
            sendNavBtn.getStyleClass().remove("active");
        if (receiveNavBtn != null)
            receiveNavBtn.getStyleClass().remove("active");

        // Apply active class
        if ("send-files".equals(activePage) && sendNavBtn != null) {
            sendNavBtn.getStyleClass().add("active");
        } else if ("receive-files".equals(activePage) && receiveNavBtn != null) {
            receiveNavBtn.getStyleClass().add("active");
        }
    }

    // ================================================
    // Dialogs & Profiles
    // ================================================

    @FXML
    public void showProfile() {
        User currentUser = UserSession.getInstance().getCurrentUser();
        if (currentUser == null) {
            showErrorAlert("Not Logged In", "Please log in to view your profile.");
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("👤 User Profile");
        dialog.setHeaderText("Account Details");

        VBox content = new VBox(20);
        content.setPadding(new Insets(24));
        content.setAlignment(Pos.CENTER);

        Text username = new Text("Username: " + currentUser.getUsername());
        username.getStyleClass().add("heading-m");

        Text status = new Text("Status: " + (currentUser.isActive() ? "Active" : "Inactive"));
        status.getStyleClass().add("text-sub");

        content.getChildren().addAll(username, status);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getStyleClass().add("dialog-pane");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        dialog.showAndWait();
    }

    @FXML
    public void showUSBWizard() {
        ToastNotification.show(getCurrentStage(), "USB Wizard is coming soon!",
                ToastNotification.NotificationType.INFO, Duration.seconds(3));
    }

    @FXML
    public void showTransferHistory() {
        ToastNotification.show(getCurrentStage(), "Transfer history is coming soon!",
                ToastNotification.NotificationType.INFO, Duration.seconds(3));
    }

    @FXML
    public void logout() {
        UserSession.getInstance().clearSession();
        loadScreen("/fxml/login.fxml");
        logger.info("User logged out");
    }

    private void loadScreen(String fxmlPath) {
        try {
            FXMLLoader loader = createFxmlLoader(fxmlPath);
            Parent root = loader.load();

            Object controller = loader.getController();
            if (controller instanceof BaseController) {
                ((BaseController) controller).setSpringContext(springContext);
            }

            Scene scene = new Scene(root);
            Stage stage = getCurrentStage();
            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            logger.error("Failed to load screen {}: {}", fxmlPath, e.getMessage());
        }
    }

    private void showErrorAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.getDialogPane().getStyleClass().add("dialog-pane");
        alert.showAndWait();
    }

    @Override
    protected Stage getCurrentStage() {
        return (Stage) rootPane.getScene().getWindow();
    }

    @Override
    protected void loadLoginScreen() {
        loadScreen("/fxml/login.fxml");
    }

    @Override
    protected void loadRegistrationScreen() {
        loadScreen("/fxml/registration.fxml");
    }
}