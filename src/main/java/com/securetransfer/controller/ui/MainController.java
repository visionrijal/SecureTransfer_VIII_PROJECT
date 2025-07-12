package com.securetransfer.controller.ui;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.text.Text;
import javafx.scene.layout.*;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.Circle;
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
import com.securetransfer.controller.ui.ReceiveFilesController;
import javafx.scene.layout.Priority;

@Controller
public class MainController extends BaseController {
    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    @FXML private ScrollPane mainScrollPane;
    @FXML private VBox heroSection;
    @FXML private VBox featuresSection;
    @FXML private VBox securitySection;
    @FXML private VBox pricingSection;
    @FXML private VBox contentArea;
    @FXML private VBox sendFilesContent;

    @FXML
    public void initialize() {
        // Set smooth scrolling for the main scroll pane
        if (mainScrollPane != null) {
            mainScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            mainScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            mainScrollPane.setFitToWidth(true);
        }
        
        logger.info("SecureTransfer main page initialized successfully");
    }

    // ================================================
    // Navigation Actions
    // ================================================
    
    @FXML
    public void scrollToFeatures() {
        smoothScrollTo(featuresSection);
    }

    @FXML
    public void scrollToSecurity() {
        smoothScrollTo(securitySection);
    }

    @FXML
    public void scrollToPricing() {}

    @FXML
    public void showDemo() {
        Dialog<ButtonType> dialog = createStyledDialog(
            "🎬 SecureTransfer Demo",
            "Experience the power of secure file transfers!\n\n" +
            "• Lightning-fast transfers\n" +
            "• Military-grade encryption\n" +
            "• Offline capability\n" +
            "• USB wizard integration\n\n" +
            "Ready to see it in action?",
            "primary"
        );
        
        ButtonType startDemo = new ButtonType("Start Demo", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType("Maybe Later", ButtonBar.ButtonData.CANCEL_CLOSE);
        
        dialog.getDialogPane().getButtonTypes().setAll(startDemo, cancel);
        dialog.showAndWait();
        
        logger.info("Demo dialog shown");
    }

    // ================================================
    // Primary Actions
    // ================================================
    
    @FXML
    public void showSendFiles() {
        logger.info("Showing send files content");
        showSendFilesContent();
    }
    
    @FXML
    public void showWelcomePage() {
        logger.info("Returning to welcome page");
        showWelcomeContent();
    }

    @FXML
    public void showReceiveFiles() {
        logger.info("Showing receive files content");
        showReceiveFilesContent();
    }

    @FXML
    public void showUSBWizard() {
        Dialog<ButtonType> dialog = createStyledDialog(
            "🔌 USB Transfer Wizard",
            "Transfer files to/from USB devices:\n\n" +
            "• Automatic device detection\n" +
            "• Safe ejection handling\n" +
            "• Progress monitoring\n" +
            "• Transfer verification\n\n" +
            "Connect your USB device to get started.",
            "primary"
        );
        
        ButtonType startWizard = new ButtonType("Start Wizard", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        
        dialog.getDialogPane().getButtonTypes().setAll(startWizard, cancel);
        
        dialog.showAndWait().ifPresent(result -> {
            logger.info("USB wizard initiated");
        });
    }

    @FXML
    public void showTransferHistory() {
        Dialog<ButtonType> dialog = createStyledDialog(
            "📋 Transfer History",
            "View and manage your file transfers:\n\n" +
            "• Recent transfers\n" +
            "• Transfer status\n" +
            "• File details\n" +
            "• Security logs\n\n" +
            "All transfer data is encrypted and private.",
            "primary"
        );
        
        ButtonType viewHistory = new ButtonType("View History", ButtonBar.ButtonData.OK_DONE);
        ButtonType exportLogs = new ButtonType("Export Logs", ButtonBar.ButtonData.OTHER);
        ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        
        dialog.getDialogPane().getButtonTypes().setAll(viewHistory, exportLogs, cancel);
        
        dialog.showAndWait().ifPresent(result -> {
            logger.info("Transfer history accessed");
        });
    }

    @FXML
    public void showProfile() {
        User currentUser = UserSession.getInstance().getCurrentUser();
        
        logger.debug("Profile requested - Current user in session: {}", 
                    currentUser != null ? currentUser.getUsername() : "null");
        
        if (currentUser == null) {
            logger.warn("Profile dialog requested but no user is logged in");
            createStyledAlert(
                Alert.AlertType.WARNING,
                "No User Logged In",
                "User profile not available.",
                "Please log in to view your profile."
            ).showAndWait();
            return;
        }

        String username = currentUser.getUsername();
        String memberSince = currentUser.getCreatedAt().toLocalDate().toString();
        String status = currentUser.isActive() ? "Active" : "Inactive";
        
        // Format last login time
        String lastLoginText = "Never";
        if (currentUser.getLastLogin() != null) {
            lastLoginText = currentUser.getLastLogin().format(java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm"));
        }
        
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("👤 User Profile");
        dialog.setHeaderText("Welcome to SecureTransfer");
        
        VBox dialogContent = new VBox(20);
        dialogContent.setAlignment(Pos.TOP_CENTER);
        dialogContent.setPadding(new Insets(24));
        dialogContent.setMaxWidth(500);
        dialogContent.setMinWidth(450);
        
        VBox profilePictureSection = new VBox(8);
        profilePictureSection.setAlignment(Pos.CENTER);
        
        SVGPath profilePicture = new SVGPath();
        profilePicture.setContent("M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z");
        profilePicture.setStyle("-fx-fill: linear-gradient(to bottom right, #667eea, #764ba2); -fx-scale-x: 3; -fx-scale-y: 3;");
        
        StackPane profileContainer = new StackPane();
        profileContainer.setPrefSize(80, 80);
        profileContainer.setMaxSize(80, 80);
        profileContainer.setStyle("-fx-background-color: #f3f4f6; s-fx-background-radius: 40; -fx-border-radius: 40; -fx-border-color: #e5e7eb; -fx-border-width: 2;");
        profileContainer.getChildren().add(profilePicture);
        
        Text usernameText = new Text(username);
        usernameText.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-fill: #111827; -fx-font-family: 'Segoe UI';");
        
        HBox statusBox = new HBox(6);
        statusBox.setAlignment(Pos.CENTER);
        
        Circle statusDot = new Circle(4);
        statusDot.setFill(status.equals("Active") ? javafx.scene.paint.Color.valueOf("#10b981") : javafx.scene.paint.Color.valueOf("#6b7280"));
        
        Text statusText = new Text(status);
        statusText.setStyle("-fx-font-size: 12px; -fx-fill: #6b7280; -fx-font-family: 'Segoe UI';");
        
        statusBox.getChildren().addAll(statusDot, statusText);
        
        profilePictureSection.getChildren().addAll(profileContainer, usernameText, statusBox);
        
        VBox infoSection = new VBox(12);
        infoSection.setAlignment(Pos.CENTER_LEFT);
        infoSection.setPadding(new Insets(16, 0, 0, 0));
        
        Text infoTitle = new Text("📋 Account Information");
        infoTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-fill: #374151; -fx-font-family: 'Segoe UI';");
        
        VBox infoItems = new VBox(8);
        
        // Create info items with icons
        HBox memberSinceBox = createInfoItem("📅 Member Since", memberSince);
        HBox lastLoginBox = createInfoItem("🕒 Last Login", lastLoginText);
        HBox accountTypeBox = createInfoItem("🔐 Account Type", "Premium");
        
        infoItems.getChildren().addAll(memberSinceBox, lastLoginBox, accountTypeBox);
        
        infoSection.getChildren().addAll(infoTitle, infoItems);
        
        dialogContent.getChildren().addAll(profilePictureSection, infoSection);
        dialog.getDialogPane().setContent(dialogContent);
        dialog.getDialogPane().getStyleClass().add("dialog-pane");
        ButtonType close = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().add(close);
        Platform.runLater(() -> {
            dialog.getDialogPane().lookupAll(".button").forEach(node -> {
                if (node instanceof Button) {
                    Button button = (Button) node;
                    if (button.getText().equals("Close")) {
                        button.getStyleClass().addAll("dialog-button", "cancel");
                    }
                }
            });
        });
        
        dialog.showAndWait();
        
        logger.info("Profile dialog accessed for user: " + username);
    }
    
    private HBox createInfoItem(String label, String value) {
        HBox item = new HBox(8);
        item.setAlignment(Pos.CENTER_LEFT);
        
        Text labelText = new Text(label);
        labelText.setStyle("-fx-font-size: 12px; -fx-fill: #6b7280; -fx-font-family: 'Segoe UI';");
        
        Text valueText = new Text(value);
        valueText.setStyle("-fx-font-size: 12px; -fx-fill: #111827; -fx-font-weight: 500; -fx-font-family: 'Segoe UI';");
        
        item.getChildren().addAll(labelText, valueText);
        return item;
    }
    


    @FXML
    public void showAbout() {
        Dialog<ButtonType> dialog = createStyledDialog(
            "ℹ️ About SecureTransfer",
            "This is a collaborative group project for 8th semester\n" +
            "by the DokoData team.\n\n" +
            "👥 TEAM MEMBERS:\n" +
            "• Pradip Dhungana\n" +
            "• Vision Rijal\n" +
            "• Priyanka Kumari\n\n" +
            "🔧 TECHNICAL DETAILS:\n" +
            "• Secure peer-to-peer file transfer\n" +
            "• TLS and SSL encryption\n" +
            "• AES IV encryption\n" +
            "• RSA encryption\n" +
            "• USB encryption capabilities\n\n" +
            "🎯 PROJECT GOAL:\n" +
            "To provide a secure, efficient, and user-friendly\n" +
            "file transfer solution with enterprise-grade security.\n\n" +
            "🛡️ Enterprise Security Details:\n\n" +
            "🔒 ENCRYPTION\n" +
            "• 256-bit AES encryption at rest\n" +
            "• TLS 1.3 for data in transit\n" +
            "• End-to-end encryption option\n\n" +
            "🏛️ COMPLIANCE\n" +
            "• SOC 2 Type II certified\n" +
            "• GDPR & CCPA compliant\n" +
            "• HIPAA ready for healthcare\n\n" +
            "🔍 MONITORING\n" +
            "• Real-time threat detection\n" +
            "• Comprehensive audit logs\n" +
            "• Advanced access controls",
            "primary"
        );
        
        ButtonType close = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().add(close);
        
        dialog.showAndWait();
        
        logger.info("User viewed about information");
    }

    
    private void smoothScrollTo(VBox targetSection) {
        if (mainScrollPane == null || targetSection == null) {
            logger.warn("Cannot scroll: mainScrollPane or targetSection is null");
            return;
        }
        
        try {
            // Calculate the target scroll position
            double targetY = targetSection.getLayoutY() / 
                           (mainScrollPane.getContent().getBoundsInLocal().getHeight() - 
                            mainScrollPane.getViewportBounds().getHeight());
            
            // Create smooth scroll animation
            Timeline timeline = new Timeline();
            KeyValue keyValue = new KeyValue(mainScrollPane.vvalueProperty(), 
                                           Math.max(0, Math.min(1, targetY)), 
                                           Interpolator.EASE_BOTH);
            KeyFrame keyFrame = new KeyFrame(Duration.millis(800), keyValue);
            timeline.getKeyFrames().add(keyFrame);
            timeline.play();
            
        } catch (Exception e) {
            logger.error("Error during smooth scroll", e);
            // Fallback to immediate scroll
            mainScrollPane.setVvalue(0.5);
        }
    }
    
// Add this method to your MainController class to replace the existing createStyledDialog method

private Dialog<ButtonType> createStyledDialog(String title, String content, String type) {
    Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(title);
    dialog.setHeaderText(title); // Set header text for better styling
        
    // Create custom content with better styling
    VBox dialogContent = new VBox(16);
        dialogContent.setAlignment(Pos.CENTER_LEFT);
    dialogContent.setPadding(new Insets(24));
    dialogContent.setMaxWidth(500);
    dialogContent.setMinWidth(450);
        
    // Create styled text with better formatting
        Text contentText = new Text(content);
    contentText.setStyle("-fx-font-size: 14px; -fx-fill: #374151; -fx-line-spacing: 1.4; -fx-font-family: 'Segoe UI';");
    contentText.setWrappingWidth(450);
        
        dialogContent.getChildren().add(contentText);
        dialog.getDialogPane().setContent(dialogContent);
        
    // Apply enhanced styling
    dialog.getDialogPane().getStyleClass().add("dialog-pane");
    
    // Style buttons after they're added
    Platform.runLater(() -> {
        dialog.getDialogPane().lookupAll(".button").forEach(node -> {
            if (node instanceof Button) {
                Button button = (Button) node;
                ButtonType buttonType = (ButtonType) button.getProperties().get("javafx.scene.control.ButtonType");
                
                // Style based on button type
                if (buttonType != null) {
                    if (buttonType.getButtonData() == ButtonBar.ButtonData.OK_DONE) {
                        button.getStyleClass().add("dialog-button");
                    } else if (buttonType.getButtonData() == ButtonBar.ButtonData.CANCEL_CLOSE) {
                        button.getStyleClass().addAll("dialog-button", "cancel");
                    } else {
                        button.getStyleClass().addAll("dialog-button", "secondary");
                    }
                }
            }
        });
    });
        
        return dialog;
}

// Also add this helper method for consistent alert styling
private Alert createStyledAlert(Alert.AlertType alertType, String title, String header, String content) {
    Alert alert = new Alert(alertType);
    alert.setTitle(title);
    alert.setHeaderText(header);
    alert.setContentText(content);
    
    // Apply custom styling
    alert.getDialogPane().getStyleClass().add("dialog-pane");
    
    // Style buttons
    Platform.runLater(() -> {
        alert.getDialogPane().lookupAll(".button").forEach(node -> {
            if (node instanceof Button) {
                Button button = (Button) node;
                if (button.getText().equals("OK")) {
                    button.getStyleClass().add("dialog-button");
                } else {
                    button.getStyleClass().addAll("dialog-button", "secondary");
                }
            }
        });
    });
    
    return alert;
}

@FXML
public void logout() {
    Alert alert = createStyledAlert(
        Alert.AlertType.CONFIRMATION,
        "Logout",
        "Are you sure you want to logout?",
        "You will be logged out of SecureTransfer."
    );
    
    alert.showAndWait().ifPresent(result -> {
        if (result == ButtonType.OK) {
            logger.info("User logged out");
            UserSession.getInstance().clearSession(); // Clear the session
            
            // Get the current stage before changing scenes
            Stage currentStage = (Stage) mainScrollPane.getScene().getWindow();
            
            // Load login screen
            try {
                FXMLLoader loader = createFxmlLoader("/fxml/login.fxml");
                Parent root = loader.load();
                Object controller = loader.getController();
                if (controller instanceof BaseController) {
                    ((BaseController) controller).setSpringContext(springContext);
                }
                Scene scene = new Scene(root);
                currentStage.setScene(scene);
                
                // Show logout success toast on the login screen
                ToastNotification.show(currentStage, "Logged out successfully!", 
                    ToastNotification.NotificationType.SUCCESS, Duration.seconds(3));
            } catch (Exception e) {
                logger.error("Failed to load login screen", e);
            }
        }
    });
}

    @Override
    protected Stage getCurrentStage() {
        return (Stage) mainScrollPane.getScene().getWindow();
    }

    @Override
    protected void loadLoginScreen() {
        try {
            FXMLLoader loader = createFxmlLoader("/fxml/login.fxml");
            Parent root = loader.load();
            Object controller = loader.getController();
            if (controller instanceof BaseController) {
                ((BaseController) controller).setSpringContext(springContext);
            }
            Stage stage = (Stage) mainScrollPane.getScene().getWindow();
            Scene scene = new Scene(root);
            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            logger.error("Failed to load login screen", e);
        }
    }

    @Override
    protected void loadRegistrationScreen() {
        // This method is not used on the main page
        // Registration should only be available on the initial login screen
        logger.warn("Registration screen requested from main page - this should not happen");
    }
    
    // ================================================
    // Content Navigation Methods
    // ================================================
    
    private void showWelcomeContent() {
        contentArea.getChildren().clear();
        contentArea.getChildren().add(mainScrollPane);
        VBox.setVgrow(mainScrollPane, Priority.ALWAYS);
        sendFilesContent.setVisible(false);
        updateNavbarActiveState("welcome");
    }
    
    private void showSendFilesContent() {
        contentArea.getChildren().clear();
        contentArea.getChildren().add(sendFilesContent);
        VBox.setVgrow(sendFilesContent, Priority.ALWAYS);
        sendFilesContent.setVisible(true);
        loadSendFilesContent();
        updateNavbarActiveState("send-files");
    }
    
    private void loadSendFilesContent() {
        try {
            // Use Spring-aware loader
            FXMLLoader loader = createFxmlLoader("/fxml/send-files.fxml");
            Parent sendFilesRoot = loader.load();

            // Clear existing content and add new content
            sendFilesContent.getChildren().clear();
            sendFilesContent.getChildren().add(sendFilesRoot);

            // Get the controller and set up any necessary references
            SendFilesController controller = loader.getController();
            if (controller != null) {
                controller.setMainController(this);
                if (controller instanceof BaseController) {
                    ((BaseController) controller).setSpringContext(springContext);
                }
            }

            logger.info("Send files content loaded successfully");
        } catch (Exception e) {
            logger.error("Failed to load send files content", e);
            createStyledAlert(
                Alert.AlertType.ERROR,
                "Error",
                "Failed to load send files page",
                "Please try again or contact support."
            ).showAndWait();
        }
    }
    
    private void showReceiveFilesContent() {
        try {
            // Use Spring-aware loader
            FXMLLoader loader = createFxmlLoader("/fxml/receive-files.fxml");
            Parent receiveFilesRoot = loader.load();

            // Clear existing content and add new content
            contentArea.getChildren().clear();
            contentArea.getChildren().add(receiveFilesRoot);
            VBox.setVgrow(receiveFilesRoot, Priority.ALWAYS);
            
            // Hide send files content
            sendFilesContent.setVisible(false);

            // Get the controller and set up any necessary references
            ReceiveFilesController controller = loader.getController();
            if (controller != null) {
                if (controller instanceof BaseController) {
                    ((BaseController) controller).setSpringContext(springContext);
                }
            }

            updateNavbarActiveState("receive-files");
            logger.info("Receive files content loaded successfully");
        } catch (Exception e) {
            logger.error("Failed to load receive files content", e);
            createStyledAlert(
                Alert.AlertType.ERROR,
                "Error",
                "Failed to load receive files page",
                "Please try again or contact support."
            ).showAndWait();
        }
    }
    
    private void updateNavbarActiveState(String activePage) {
        // Get all nav buttons and remove active class
        Scene scene = mainScrollPane.getScene();
        if (scene != null) {
            // Find the navbar and get all nav-link buttons
            Node navbar = scene.lookup(".navbar");
            if (navbar != null) {
                // Remove active class from all nav links
                navbar.lookupAll(".nav-link").forEach(node -> {
                    if (node instanceof Button) {
                        Button button = (Button) node;
                        button.getStyleClass().remove("active");
                    }
                });
                
                // Add active class to the current page button
                switch (activePage) {
                    case "send-files":
                        navbar.lookupAll(".nav-link").forEach(node -> {
                            if (node instanceof Button) {
                                Button button = (Button) node;
                                if ("Send Files".equals(button.getText())) {
                                    button.getStyleClass().add("active");
                                }
                            }
                        });
                        break;
                    case "receive-files":
                        navbar.lookupAll(".nav-link").forEach(node -> {
                            if (node instanceof Button) {
                                Button button = (Button) node;
                                if ("Receive Files".equals(button.getText())) {
                                    button.getStyleClass().add("active");
                                }
                            }
                        });
                        break;
                    case "welcome":
                    default:
                        // Welcome page - no specific nav item is active
                        break;
                }
            }
        }
        
        logger.debug("Active page changed to: {}", activePage);
    }
}