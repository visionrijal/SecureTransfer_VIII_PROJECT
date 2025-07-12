package com.securetransfer.controller.ui;

import com.securetransfer.model.entity.ReceiverTransfer;
import com.securetransfer.service.TransferService;
import com.securetransfer.service.WebSocketService;
import com.securetransfer.service.WebSocketService.TransferSession;
import com.securetransfer.util.ToastNotification;
import com.securetransfer.util.UserSession;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.text.Text;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.geometry.Pos;
import javafx.stage.Modality;
import javafx.scene.Scene;
import javafx.geometry.Insets;

import java.io.File;
import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;

@Controller
public class ReceiveFilesController extends BaseController implements Initializable {
    private static final Logger logger = LoggerFactory.getLogger(ReceiveFilesController.class);

    @FXML private TextField codeTextField;
    @FXML private Button connectButton;
    @FXML private VBox transferStatusSection;
    @FXML private Label statusLabel;
    @FXML private ProgressBar transferProgressBar;
    @FXML private Button cancelTransferButton;
    @FXML private TableView<ReceiverTransfer> receivedFilesTable;
    @FXML private TableColumn<ReceiverTransfer, String> fileNameColumn;
    @FXML private TableColumn<ReceiverTransfer, Long> fileSizeColumn;
    @FXML private TableColumn<ReceiverTransfer, String> senderColumn;
    @FXML private TableColumn<ReceiverTransfer, String> receivedTimeColumn;
    @FXML private TableColumn<ReceiverTransfer, String> statusColumn;
    @FXML private TableColumn<ReceiverTransfer, Void> actionsColumn;
    @FXML private Button refreshButton;
    @FXML private VBox noFilesMessage;
    @FXML private VBox connectionStatusBox;
    @FXML private VBox codePopup;
    @FXML private Text transferCodeText;

    private ProgressIndicator connectingIndicator;
    private Label connectingLabel;
    private Stage loaderStage;
    private Stage downloadProgressStage;
    private ProgressBar downloadProgressBar;
    private Label downloadFileNameLabel;
    private Label downloadFileSizeLabel;
    private Label downloadSpeedLabel;
    private Label downloadPercentLabel;
    private Button cancelDownloadButton;
    private long lastBytesTransferred = 0;
    private long lastUpdateTime = 0;

    @Autowired
    private TransferService transferService;
    
    @Autowired
    private WebSocketService webSocketService;

    private ObservableList<ReceiverTransfer> receivedFilesList = FXCollections.observableArrayList();
    private String currentTransferCode;
    private String currentSessionId;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.info("ReceiveFilesController initialized");
        setupTable();
        setupCodeValidation();
        loadTransferHistory();
        
        // Hide received files section by default
        receivedFilesTable.setVisible(false);
        receivedFilesTable.setManaged(false);
        noFilesMessage.setVisible(false);
        noFilesMessage.setManaged(false);

        connectingIndicator = new ProgressIndicator();
        connectingIndicator.setVisible(false);
        connectingLabel = new Label("Connecting...");
        connectingLabel.setVisible(false);
        connectionStatusBox.getChildren().addAll(connectingIndicator, connectingLabel);
    }

    private void setupTable() {
        // Configure table columns
        fileNameColumn.setCellValueFactory(data -> 
            new javafx.beans.property.SimpleStringProperty(data.getValue().getFileName()));
        
        fileSizeColumn.setCellValueFactory(data -> 
            new javafx.beans.property.SimpleObjectProperty<>(data.getValue().getFileSize()));
        
        senderColumn.setCellValueFactory(data -> 
            new javafx.beans.property.SimpleStringProperty(data.getValue().getSenderUsername() != null ? 
                data.getValue().getSenderUsername() : "Unknown"));
        
        receivedTimeColumn.setCellValueFactory(data -> 
            new javafx.beans.property.SimpleStringProperty(
                data.getValue().getReceivedTime().format(DateTimeFormatter.ofPattern("MMM dd, HH:mm"))));
        
        statusColumn.setCellValueFactory(data -> 
            new javafx.beans.property.SimpleStringProperty(data.getValue().getTransferStatus().toString()));
        
        // Setup actions column
        actionsColumn.setCellFactory(param -> new TableCell<>() {
            private final Button saveButton = new Button("Save");
            private final Button openButton = new Button("Open");
            private final HBox buttonBox = new HBox(8, saveButton, openButton);

            {
                saveButton.getStyleClass().add("save-btn");
                openButton.getStyleClass().add("open-btn");
                
                saveButton.setOnAction(e -> {
                    ReceiverTransfer transfer = getTableView().getItems().get(getIndex());
                    saveFile(transfer);
                });
                
                openButton.setOnAction(e -> {
                    ReceiverTransfer transfer = getTableView().getItems().get(getIndex());
                    openFile(transfer);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    ReceiverTransfer transfer = getTableView().getItems().get(getIndex());
                    saveButton.setVisible(transfer.getTransferStatus() == ReceiverTransfer.TransferStatus.RECEIVED);
                    openButton.setVisible(transfer.getTransferStatus() == ReceiverTransfer.TransferStatus.SAVED);
                    setGraphic(buttonBox);
                }
            }
        });

        // Set table data
        receivedFilesTable.setItems(receivedFilesList);
        
        // Show/hide no files message based on table content
        receivedFilesList.addListener((javafx.collections.ListChangeListener<ReceiverTransfer>) change -> {
            updateReceivedFilesSection();
        });
    }

    private void setupCodeValidation() {
        // Add text change listener to validate code format
        codeTextField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && newValue.matches("\\d{0,6}")) {
                connectButton.setDisable(newValue.length() != 6);
            } else {
                // Remove non-digit characters
                codeTextField.setText(newValue.replaceAll("[^\\d]", ""));
            }
        });
    }

    private void showConnectingLoader(String message) {
        if (loaderStage != null && loaderStage.isShowing()) return;
        loaderStage = new Stage();
        loaderStage.initModality(Modality.APPLICATION_MODAL);
        loaderStage.setTitle("Connecting");
        loaderStage.setResizable(false);
        loaderStage.setAlwaysOnTop(true);
        VBox content = new VBox(24);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(40, 40, 40, 40));
        content.getStyleClass().add("loader-popup-pane");
        content.setMinWidth(400);
        content.setMinHeight(220);

        // Custom animated loader (CSS-based bouncing dots)
        HBox loader = new HBox(8);
        loader.setAlignment(Pos.CENTER);
        for (int i = 0; i < 3; i++) {
            Region dot = new Region();
            dot.getStyleClass().add("loader-dot");
            loader.getChildren().add(dot);
        }

        Label label = new Label(message);
        label.setFont(Font.font("Inter", FontWeight.BOLD, 18));
        label.setTextFill(Color.web("#374151"));
        label.setAlignment(Pos.CENTER);
        label.setWrapText(true);
        label.setMaxWidth(320);

        content.getChildren().addAll(loader, label);
        Scene scene = new Scene(content);
        scene.getStylesheets().add(getClass().getResource("/styles/receive-files.css").toExternalForm());
        loaderStage.setScene(scene);
        loaderStage.show();
    }

    private void closeConnectingLoader() {
        if (loaderStage != null) {
            loaderStage.close();
            loaderStage = null;
        }
    }

    @FXML
    private void onConnectButtonClicked() {
        String code = codeTextField.getText().trim();
        if (code.isEmpty()) {
            showToast("Please enter a transfer code.", ToastNotification.NotificationType.ERROR);
            return;
        }
        
        currentTransferCode = code;
        showConnectingLoader("Connecting to sender…");
        
        // Run connection in background
        CompletableFuture.runAsync(() -> {
            transferService.connectToTransfer(code, UserSession.getInstance().getCurrentUser() != null ? 
                    UserSession.getInstance().getCurrentUser().getUsername() : "receiver")
                .thenAccept(session -> {
                    Platform.runLater(() -> {
                        // Hide loader, show success UI
                        closeConnectingLoader();
                        if (session != null) {
                            // Show connection success toast and UI
                            showToast("Successfully connected to sender!", ToastNotification.NotificationType.SUCCESS);
                            statusLabel.setText("Connected to sender");
                            transferStatusSection.setVisible(true);
                            
                            // Show file information and progress UI if file information is available
                            if (session.getFileName() != null && session.getFileSize() > 0) {
                                showDownloadProgressPopup(session.getFileName(), session.getFileSize());
                                // Begin waiting for transfer to start
                                waitForTransferStart();
                            } else {
                                // No file info yet, show waiting status
                                statusLabel.setText("Connected. Waiting for file information...");
                                // Set up a checker for when file info becomes available
                                checkForFileInfo();
                            }
                        } else {
                            showToast("Connection established but no session details available.", 
                                ToastNotification.NotificationType.WARNING);
                            statusLabel.setText("Connected. Waiting for file information...");
                            transferStatusSection.setVisible(true);
                        }
                    });
                })
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        closeConnectingLoader();
                        showToast("Failed to connect: " + ex.getMessage(), ToastNotification.NotificationType.ERROR);
                        resetConnectionUI();
                    });
                    return null;
                });
        });
    }
    
    private void checkForFileInfo() {
        // Check for file info every 2 seconds
        final javafx.animation.Timeline[] fileInfoChecker = new javafx.animation.Timeline[1];
        fileInfoChecker[0] = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(javafx.util.Duration.seconds(2), e -> {
                // Get active sessions from transfer service
                List<TransferSession> activeSessions = transferService.getActiveSessions();
                
                // Find the current session
                Optional<TransferSession> currentSession = activeSessions.stream()
                    .filter(session -> session.getTransferCode().equals(currentTransferCode))
                    .findFirst();
                
                if (currentSession.isPresent() && 
                    currentSession.get().getFileName() != null && 
                    currentSession.get().getFileSize() > 0) {
                    
                    // File info available, show download UI
                    TransferSession session = currentSession.get();
                    showDownloadProgressPopup(session.getFileName(), session.getFileSize());
                    fileInfoChecker[0].stop();
                    waitForTransferStart();
                }
            })
        );
        fileInfoChecker[0].setCycleCount(javafx.animation.Timeline.INDEFINITE);
        fileInfoChecker[0].play();
    }

    private void waitForTransferStart() {
        // Check for transfer start every 2 seconds
        final javafx.animation.Timeline[] transferChecker = new javafx.animation.Timeline[1];
        transferChecker[0] = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(javafx.util.Duration.seconds(2), e -> {
                if (transferService.isTransferActive(currentTransferCode)) {
                    // Transfer started, show progress
                    showTransferProgress();
                    transferChecker[0].stop();
                }
            })
        );
        transferChecker[0].setCycleCount(javafx.animation.Timeline.INDEFINITE);
        transferChecker[0].play();
    }

    private void showTransferProgress() {
        statusLabel.setText("Receiving files...");
        transferProgressBar.setVisible(true);
        transferProgressBar.setProgress(0.0);
        
        // Register for real progress updates from the transfer service
        webSocketService.registerProgressCallback(currentTransferCode, progress -> {
            Platform.runLater(() -> {
                transferProgressBar.setProgress(progress.getProgress());
                statusLabel.setText(String.format("Receiving files... %.1f%%", progress.getProgress() * 100));
            });
        });
        
        // Register for completion callback
        webSocketService.registerCompletionCallback(currentTransferCode, complete -> {
            Platform.runLater(() -> {
                if (complete.isSuccess()) {
                    statusLabel.setText("Files received successfully!");
                    transferProgressBar.setProgress(1.0);
                    showToast("Files received successfully!", ToastNotification.NotificationType.SUCCESS);
                    
                    // Close status section after 3 seconds
                    javafx.animation.Timeline closeTimeline = new javafx.animation.Timeline(
                        new javafx.animation.KeyFrame(javafx.util.Duration.seconds(3), ev -> {
                            resetConnectionUI();
                            loadTransferHistory();
                        })
                    );
                    closeTimeline.play();
                } else {
                    statusLabel.setText("Transfer failed: " + complete.getErrorMessage());
                    showToast("Transfer failed: " + complete.getErrorMessage(), ToastNotification.NotificationType.ERROR);
                    resetConnectionUI();
                }
            });
        });
    }

    @FXML
    public void cancelTransfer() {
        if (currentTransferCode != null) {
            transferService.cancelTransfer(currentTransferCode);
            showToast("Transfer cancelled", ToastNotification.NotificationType.INFO);
        }
        resetConnectionUI();
    }

    private void resetConnectionUI() {
        transferStatusSection.setVisible(false);
        transferProgressBar.setVisible(false);
        cancelTransferButton.setVisible(false);
        connectButton.setDisable(false);
        codeTextField.clear();
        currentTransferCode = null;
        currentSessionId = null;
    }

    @FXML
    public void refreshHistory() {
        loadTransferHistory();
        showToast("History refreshed", ToastNotification.NotificationType.INFO);
    }

    private void loadTransferHistory() {
        try {
            List<ReceiverTransfer> transfers = transferService.getReceiverTransferHistory(currentSessionId);
            receivedFilesList.clear();
            receivedFilesList.addAll(transfers);
            updateReceivedFilesSection();
        } catch (Exception e) {
            logger.error("Error loading transfer history", e);
            showToast("Error loading history: " + e.getMessage(), ToastNotification.NotificationType.ERROR);
        }
    }

    private void updateReceivedFilesSection() {
        if (receivedFilesList.isEmpty()) {
            receivedFilesTable.setVisible(false);
            receivedFilesTable.setManaged(false);
            noFilesMessage.setVisible(true);
            noFilesMessage.setManaged(true);
        } else {
            receivedFilesTable.setVisible(true);
            receivedFilesTable.setManaged(true);
            noFilesMessage.setVisible(false);
            noFilesMessage.setManaged(false);
        }
    }

    private void saveFile(ReceiverTransfer transfer) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Choose directory to save file");
        directoryChooser.setInitialDirectory(new File(System.getProperty("user.home")));
        
        File selectedDirectory = directoryChooser.showDialog(getStage());
        if (selectedDirectory != null) {
            try {
                // Save the actual file data using the transfer service
                File targetFile = transferService.saveReceivedFile(
                    currentTransferCode, 
                    transfer.getFileName(), 
                    transfer.getFileData(), 
                    selectedDirectory
                ).get(); // Wait for completion
                
                // Update transfer status
                transfer.setTransferStatus(ReceiverTransfer.TransferStatus.SAVED);
                transfer.setSavedTime(java.time.LocalDateTime.now());
                transfer.setFilePath(targetFile.getAbsolutePath());
                transfer.setAutoSaved(true);
                
                // Refresh the table
                receivedFilesTable.refresh();
                
                showToast("File saved successfully to: " + targetFile.getAbsolutePath(), ToastNotification.NotificationType.SUCCESS);
                
            } catch (Exception e) {
                logger.error("Error saving file", e);
                showToast("Error saving file: " + e.getMessage(), ToastNotification.NotificationType.ERROR);
            }
        }
    }

    private void openFile(ReceiverTransfer transfer) {
        if (transfer.getFilePath() != null) {
            try {
                File file = new File(transfer.getFilePath());
                if (file.exists()) {
                    java.awt.Desktop.getDesktop().open(file);
                } else {
                    showToast("File not found: " + transfer.getFilePath(), ToastNotification.NotificationType.ERROR);
                }
            } catch (Exception e) {
                logger.error("Error opening file", e);
                showToast("Error opening file: " + e.getMessage(), ToastNotification.NotificationType.ERROR);
            }
        } else {
            showToast("File path not available", ToastNotification.NotificationType.ERROR);
        }
    }

    private void showToast(String message, ToastNotification.NotificationType type) {
        Stage stage = (Stage) codeTextField.getScene().getWindow();
        ToastNotification.show(stage, message, type, Duration.seconds(3), 70);
    }

    private Stage getStage() {
        return (Stage) codeTextField.getScene().getWindow();
    }

    @Override
    protected Stage getCurrentStage() {
        return getStage();
    }

    @FXML
    private void cancelCodePopup() {
        if (codePopup != null) {
            codePopup.setVisible(false);
            codePopup.setManaged(false);
        }
    }

    // Call this method when you want to show the code popup (e.g., after code is validated/received)
    private void showCodePopup(String code) {
        if (codePopup != null && transferCodeText != null) {
            transferCodeText.setText(code);
            codePopup.setVisible(true);
            codePopup.setManaged(true);
        }
    }

    private void showDownloadProgress() {
        // TODO: Implement the download progress UI (custom popup or section)
        // For now, show a placeholder
        showToast("Connected! Downloading files…", ToastNotification.NotificationType.SUCCESS);
    }

    private void showDownloadProgressPopup(String fileName, long fileSize) {
        if (downloadProgressStage != null && downloadProgressStage.isShowing()) return;
        downloadProgressStage = new Stage();
        downloadProgressStage.initModality(Modality.APPLICATION_MODAL);
        downloadProgressStage.setTitle("Downloading File");
        VBox content = new VBox(24);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(36, 36, 36, 36));
        content.getStyleClass().add("download-popup-pane");
        content.setMinWidth(520);
        content.setMinHeight(320);
        downloadFileNameLabel = new Label(fileName);
        downloadFileNameLabel.getStyleClass().add("download-filename");
        downloadFileSizeLabel = new Label(formatFileSize(fileSize));
        downloadFileSizeLabel.getStyleClass().add("download-filesize");
        downloadPercentLabel = new Label("0%");
        downloadPercentLabel.getStyleClass().add("download-percent");
        downloadSpeedLabel = new Label("");
        downloadSpeedLabel.getStyleClass().add("download-speed");
        downloadProgressBar = new ProgressBar(0);
        downloadProgressBar.setPrefWidth(400);
        downloadProgressBar.getStyleClass().add("download-progress-bar");
        cancelDownloadButton = new Button("Cancel");
        cancelDownloadButton.getStyleClass().add("popup-btn-red");
        cancelDownloadButton.setMinWidth(120);
        cancelDownloadButton.setFont(Font.font("Inter", FontWeight.BOLD, 15));
        cancelDownloadButton.setOnAction(e -> handleCancelDownload());
        VBox infoBox = new VBox(8, downloadFileNameLabel, downloadFileSizeLabel, downloadPercentLabel, downloadSpeedLabel);
        infoBox.setAlignment(Pos.CENTER);
        content.getChildren().addAll(infoBox, downloadProgressBar, cancelDownloadButton);
        Scene scene = new Scene(content);
        scene.getStylesheets().add(getClass().getResource("/styles/receive-files.css").toExternalForm());
        downloadProgressStage.setScene(scene);
        downloadProgressStage.setOnCloseRequest(event -> {
            event.consume();
            handleCancelDownload();
        });
        downloadProgressStage.show();
    }

    private void updateDownloadProgress(long bytesTransferred, long totalBytes, long startTime) {
        double percent = totalBytes > 0 ? (double) bytesTransferred / totalBytes : 0;
        downloadProgressBar.setProgress(percent);
        downloadPercentLabel.setText(String.format("%.1f%%", percent * 100));
        downloadFileSizeLabel.setText(formatFileSize(bytesTransferred) + " / " + formatFileSize(totalBytes));
        long now = System.currentTimeMillis();
        if (lastUpdateTime > 0 && now > lastUpdateTime) {
            long bytesDelta = bytesTransferred - lastBytesTransferred;
            double seconds = (now - lastUpdateTime) / 1000.0;
            double speed = bytesDelta / seconds;
            downloadSpeedLabel.setText(formatSpeed(speed));
        }
        lastBytesTransferred = bytesTransferred;
        lastUpdateTime = now;
    }

    private void closeDownloadProgressPopup() {
        if (downloadProgressStage != null) {
            downloadProgressStage.close();
            downloadProgressStage = null;
        }
    }

    private void handleCancelDownload() {
        // TODO: Implement actual cancel logic with transferService
        closeDownloadProgressPopup();
        showToast("Download cancelled.", ToastNotification.NotificationType.INFO);
    }

    private String formatSpeed(double bytesPerSecond) {
        if (bytesPerSecond < 1024) return String.format("%.0f B/s", bytesPerSecond);
        if (bytesPerSecond < 1024 * 1024) return String.format("%.1f KB/s", bytesPerSecond / 1024.0);
        return String.format("%.1f MB/s", bytesPerSecond / (1024.0 * 1024.0));
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
} 