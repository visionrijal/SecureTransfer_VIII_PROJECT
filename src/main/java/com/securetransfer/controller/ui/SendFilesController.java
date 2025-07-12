package com.securetransfer.controller.ui;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.scene.shape.SVGPath;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.animation.ScaleTransition;
import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.animation.ParallelTransition;
import javafx.util.Duration;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import com.securetransfer.util.ToastNotification;
import com.securetransfer.util.UserSession;
import com.securetransfer.service.EncryptionService;
import com.securetransfer.service.TransferService;
import org.springframework.beans.factory.annotation.Autowired;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javafx.concurrent.Task;
import java.security.SecureRandom;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.geometry.Pos;
import javafx.stage.WindowEvent;
import javafx.geometry.Insets;
import javafx.stage.Modality;
import javafx.scene.Scene;
import javafx.scene.layout.Priority;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javafx.scene.text.TextAlignment;

@Controller
public class SendFilesController extends BaseController implements Initializable {
    
    private static final Logger logger = LoggerFactory.getLogger(SendFilesController.class);
    
    private static final int MAX_FILES = 10;
    private static final long MAX_TOTAL_SIZE = 500 * 1024 * 1024; 
    private static final long MAX_FILE_SIZE = 500 * 1024 * 1024; 
    
    @FXML private VBox selectedFilesList;
    @FXML private Text fileCountText;
    @FXML private Text totalSizeText;
    @FXML private VBox selectedFilesContainer;
    @FXML private VBox fileDropZone;
    @FXML private Button selectFilesBtn;
    @FXML private Button clearAllBtn;
    @FXML private Button directTransferBtn;
    
    private List<File> selectedFiles = new ArrayList<>();
    private long totalSize = 0;
    private MainController mainController;
    
    @Autowired
    private EncryptionService encryptionService;
    
    @Autowired
    private TransferService transferService;

    private List<File> encryptedFiles = new ArrayList<>();
    
    private String currentTransferCode;
    private boolean waitingForReceiver = false;
    private String currentSessionId;
    
    private Stage transferStage;
    private ProgressBar encryptionProgressBar;
    private Button transferButton;
    private Label popupStatusLabel;
    private Label codeLabel;
    private Button cancelButton;
    private Task<Void> encryptionTask;
    
    private enum PopupState { ENCRYPTING, READY_TO_TRANSFER }
    private PopupState popupState;
    
    private HBox buttonRow;
    
    private Label popupIcon;
    private Label popupHeading;
    private Label popupNote;
    private Region popupDivider;
    
    // Remove FXML codePopup and transferCodeText for modal popups
    
    private boolean encryptionCompleted = false;
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.info("SendFilesController initialized");
        updateFileDisplay();
        setupAnimations();
        setupButtonHoverEffects();
    }
    
    private void setupAnimations() {
        selectedFilesContainer.setOpacity(0);
        selectedFilesContainer.setVisible(false);
        selectedFilesContainer.setManaged(false);
        selectedFilesContainer.setCacheHint(javafx.scene.CacheHint.SPEED);
        fileDropZone.setCacheHint(javafx.scene.CacheHint.SPEED);
        selectedFilesList.setCacheHint(javafx.scene.CacheHint.SPEED);
    }
    
    private void setupButtonHoverEffects() {
        // Add hover effects to buttons
        setupButtonHoverEffect(selectFilesBtn);
        setupButtonHoverEffect(clearAllBtn);
        setupButtonHoverEffect(directTransferBtn);
    }
    
    private void setupButtonHoverEffect(Button button) {
        if (button == null) return;
        
        button.setOnMouseEntered(e -> {
            ScaleTransition scaleTransition = new ScaleTransition(Duration.millis(150), button);
            scaleTransition.setToX(1.05);
            scaleTransition.setToY(1.05);
            scaleTransition.play();
        });
        
        button.setOnMouseExited(e -> {
            ScaleTransition scaleTransition = new ScaleTransition(Duration.millis(150), button);
            scaleTransition.setToX(1.0);
            scaleTransition.setToY(1.0);
            scaleTransition.play();
        });
    }
    
    public void setMainController(MainController mainController) {
        this.mainController = mainController;
        logger.info("MainController reference set in SendFilesController");
    }
    
    @FXML
    public void selectFiles() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Files to Send");
        
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("All Files", "*.*"),
            new FileChooser.ExtensionFilter("Documents", "*.pdf", "*.doc", "*.docx", "*.txt", "*.rtf"),
            new FileChooser.ExtensionFilter("Images", "*.jpg", "*.jpeg", "*.png", "*.gif", "*.bmp", "*.tiff"),
            new FileChooser.ExtensionFilter("Audio", "*.mp3", "*.wav", "*.flac", "*.aac", "*.ogg"),
            new FileChooser.ExtensionFilter("Video", "*.mp4", "*.avi", "*.mov", "*.wmv", "*.flv", "*.mkv"),
            new FileChooser.ExtensionFilter("Archives", "*.zip", "*.rar", "*.7z", "*.tar", "*.gz"),
            new FileChooser.ExtensionFilter("Spreadsheets", "*.xls", "*.xlsx", "*.csv", "*.ods"),
            new FileChooser.ExtensionFilter("Presentations", "*.ppt", "*.pptx", "*.odp")
        );
        animateButton(selectFilesBtn);
        
        List<File> files = fileChooser.showOpenMultipleDialog(getStage());
        if (files != null) {
            addFiles(files);
        }
    }

@FXML
public void handleDragOver(javafx.scene.input.DragEvent event) {
    if (event.getDragboard().hasFiles()) {
        event.acceptTransferModes(javafx.scene.input.TransferMode.COPY);
        if (!fileDropZone.getStyleClass().contains("drag-over")) {
            fileDropZone.getStyleClass().add("drag-over");
        }
    }
    event.consume();
}

@FXML
public void handleDragDropped(javafx.scene.input.DragEvent event) {
    javafx.scene.input.Dragboard db = event.getDragboard();
    boolean success = false;
    
    if (db.hasFiles()) {
        List<File> files = db.getFiles();
        addFiles(files);
        success = true;
    }
    
    event.setDropCompleted(success);
    fileDropZone.getStyleClass().remove("drag-over");
    event.consume();
}

@FXML
public void handleDragExited(javafx.scene.input.DragEvent event) {
    fileDropZone.getStyleClass().remove("drag-over");
    event.consume();
}
    
    private void animateDragOver(javafx.scene.Node node, boolean entering) {
        ScaleTransition scaleTransition = new ScaleTransition(Duration.millis(200), node);
        if (entering) {
            scaleTransition.setToX(1.05);
            scaleTransition.setToY(1.05);
        } else {
            scaleTransition.setToX(1.0);
            scaleTransition.setToY(1.0);
        }
        scaleTransition.play();
    }
    
    private void animateButton(Button button) {
        ScaleTransition scaleTransition = new ScaleTransition(Duration.millis(100), button);
        scaleTransition.setToX(0.95);
        scaleTransition.setToY(0.95);
        scaleTransition.setOnFinished(e -> {
            ScaleTransition scaleBack = new ScaleTransition(Duration.millis(100), button);
            scaleBack.setToX(1.0);
            scaleBack.setToY(1.0);
            scaleBack.play();
        });
        scaleTransition.play();
    }
    
    private void addFiles(List<File> files) {
        List<File> validFiles = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int availableSlots = MAX_FILES - selectedFiles.size();
        
        for (File file : files) {
            if (validFiles.size() >= availableSlots) {
                errors.add(file.getName() + ": Maximum " + MAX_FILES + " files allowed");
                continue;
            }
            String errorMessage = validateFile(file, validFiles);
            if (errorMessage == null) {
                validFiles.add(file);
            } else {
                errors.add(file.getName() + ": " + errorMessage);
            }
        }
        for (File file : validFiles) {
            selectedFiles.add(file);
            totalSize += file.length();
            logger.info("Added file: {} ({} bytes)", file.getName(), file.length());
        }
        if (!errors.isEmpty()) {
            if (errors.size() == 1) {
                showToast(errors.get(0), ToastNotification.NotificationType.ERROR);
            } else {
                showToast(errors.size() + " files could not be added. Max: " + MAX_FILES + " files, " + formatFileSize(MAX_TOTAL_SIZE) + " total.", ToastNotification.NotificationType.ERROR);
        }
        }
        if (!validFiles.isEmpty()) {
            updateFileDisplay();
            animateFileListAppearance();
        }
    }
    
    private String validateFile(File file, List<File> batch) {
        if (selectedFiles.size() + batch.size() >= MAX_FILES) {
            return "Maximum " + MAX_FILES + " files allowed";
        }
        if (file.length() > MAX_FILE_SIZE) {
            return "File too large (max 500MB)";
        }
        if (totalSize + batch.stream().mapToLong(File::length).sum() + file.length() > MAX_TOTAL_SIZE) {
            return "Would exceed 500MB total limit";
        }
        if (selectedFiles.stream().anyMatch(f -> f.getName().equals(file.getName())) ||
            batch.stream().anyMatch(f -> f.getName().equals(file.getName()))) {
            return "File already selected";
        }
        if (!file.canRead()) {
            return "Cannot read file";
        }
        return null;
    }
    
    private void showMultipleFileErrors(List<String> errors) {
        StringBuilder content = new StringBuilder();
        content.append("The following files could not be added:\n\n");
        
        for (String error : errors) {
            content.append("• ").append(error).append("\n");
        }
        
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("File Selection Issues");
        alert.setHeaderText(errors.size() + " file(s) could not be added");
        alert.setContentText(content.toString());
        
        alert.getDialogPane().getStyleClass().add("dialog-pane");
        alert.setResizable(true);
        alert.getDialogPane().setPrefSize(480, 320);
        
        alert.showAndWait();
    }
    
    private void animateFileListAppearance() {
        if (!selectedFilesContainer.isVisible()) {
            selectedFilesContainer.setVisible(true);
            selectedFilesContainer.setManaged(true);
            TranslateTransition slideTransition = new TranslateTransition(Duration.millis(300), selectedFilesContainer);
            slideTransition.setFromY(30);
            slideTransition.setToY(0);
            
            FadeTransition fadeTransition = new FadeTransition(Duration.millis(300), selectedFilesContainer);
            fadeTransition.setFromValue(0);
            fadeTransition.setToValue(1);
            
            ParallelTransition parallelTransition = new ParallelTransition(slideTransition, fadeTransition);
            parallelTransition.play();
        }
    }
    
    private void updateFileDisplay() {
        selectedFilesList.setCacheHint(javafx.scene.CacheHint.SPEED);
        selectedFilesList.getChildren().clear();
        if (selectedFiles.isEmpty()) {
            animateFileListDisappearance();
        } else {
            if (!selectedFilesContainer.isVisible()) {
                selectedFilesContainer.setVisible(true);
                selectedFilesContainer.setManaged(true);
                selectedFilesContainer.setOpacity(1);
            }
            for (File file : selectedFiles) {
                HBox fileItem = createFileItem(file);
                fileItem.setOpacity(1);
                selectedFilesList.getChildren().add(fileItem);
            }
        }
        fileCountText.setText(String.format("(%d/%d)", selectedFiles.size(), MAX_FILES));
        totalSizeText.setText(String.format("Total: %s", formatFileSize(totalSize)));
        updateProgressColors();
        ScrollPane scrollPane = findScrollPane(selectedFilesList);
        if (scrollPane != null) {
            scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        }
        selectedFilesList.setCacheHint(javafx.scene.CacheHint.DEFAULT);
    }
    
    private void animateFileListDisappearance() {
        if (selectedFilesContainer.isVisible()) {
            FadeTransition fadeTransition = new FadeTransition(Duration.millis(200), selectedFilesContainer);
            fadeTransition.setFromValue(1);
            fadeTransition.setToValue(0);
            fadeTransition.setOnFinished(e -> {
                selectedFilesContainer.setVisible(false);
                selectedFilesContainer.setManaged(false);
            });
            fadeTransition.play();
        }
    }
    
    private void animateFileItemAppearance(HBox fileItem) {
        fileItem.setOpacity(0);
        FadeTransition fadeTransition = new FadeTransition(Duration.millis(150), fileItem);
        fadeTransition.setFromValue(0);
        fadeTransition.setToValue(1);
        fileItem.setCacheHint(javafx.scene.CacheHint.SPEED);
        fadeTransition.setOnFinished(e -> fileItem.setCacheHint(javafx.scene.CacheHint.DEFAULT));
        fadeTransition.play();
    }
    
private void updateProgressColors() {
    double sizePercentage = (double) totalSize / MAX_TOTAL_SIZE;
    double filePercentage = (double) selectedFiles.size() / MAX_FILES;
    
    totalSizeText.getStyleClass().removeAll("status-text", "status-text.warning", "status-text.error");
    fileCountText.getStyleClass().removeAll("file-count", "file-count.warning", "file-count.error");
    
    if (sizePercentage > 0.9) {
        totalSizeText.getStyleClass().addAll("status-text", "error");
    } else if (sizePercentage > 0.75) {
        totalSizeText.getStyleClass().addAll("status-text", "warning");
    } else {
        totalSizeText.getStyleClass().add("status-text");
    }
    
    if (filePercentage > 0.8) {
        fileCountText.getStyleClass().addAll("file-count", "warning");
    } else {
        fileCountText.getStyleClass().add("file-count");
    }
}

private HBox createFileItem(File file) {
    HBox fileItem = new HBox(12);
    fileItem.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
    fileItem.getStyleClass().add("file-item");
    
    String iconPath = getFileIconPath(file.getName());
    SVGPath iconSvg = new SVGPath();
    iconSvg.setContent(iconPath);
    iconSvg.getStyleClass().add("file-icon");
    
    VBox fileInfo = new VBox(2);
    fileInfo.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
    fileInfo.getStyleClass().add("file-info");
    HBox.setHgrow(fileInfo, javafx.scene.layout.Priority.ALWAYS);
    
    Text fileName = new Text(file.getName());
    fileName.getStyleClass().add("file-name");
    
    Text fileSize = new Text(formatFileSize(file.length()));
    fileSize.getStyleClass().add("file-size");
    
    fileInfo.getChildren().addAll(fileName, fileSize);
    
    VBox removeBtnContainer = new VBox();
    removeBtnContainer.setAlignment(javafx.geometry.Pos.CENTER);
    
    Button removeBtn = new Button("✕");
    removeBtn.getStyleClass().add("remove-file-btn");
    removeBtn.setOnAction(e -> {
        animateButton(removeBtn);
        removeFile(file);
    });
    
    removeBtnContainer.getChildren().add(removeBtn);
    
    fileItem.getChildren().addAll(iconSvg, fileInfo, removeBtnContainer);
    
    return fileItem;
    }
    
private String getFileIconPath(String fileName) {
    String extension = getFileExtension(fileName).toLowerCase();
    
    return switch (extension) {
        case "pdf" -> "M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm4 18H6V4h7v5h5v11zm-8-8h6v1H10v-1zm0 2h6v1H10v-1zm0 2h4v1H10v-1z";
        case "doc", "docx" -> "M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm4 18H6V4h7v5h5v11zm-7-6h4l-2-3-2 3zm1 1v3h2v-3h-2z";
        case "xls", "xlsx" -> "M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm4 18H6V4h7v5h5v11zm-8-8h2v2H10v-2zm0 3h2v2H10v-2zm3-3h2v2h-2v-2zm0 3h2v2h-2v-2z";
        case "ppt", "pptx" -> "M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm4 18H6V4h7v5h5v11zm-8-8h4v1H10v-1zm0 2h6v1H10v-1zm0 2h2v1H10v-1z";
        case "jpg", "jpeg", "png", "gif", "bmp", "tiff", "webp", "svg" -> "M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2zm-2 0H5V5h14v14zm-7-7.5L9 15l-2.5-3.5L4 17h16l-4.5-6L12 11.5z";
        case "mp4", "avi", "mov", "wmv", "flv", "mkv", "webm", "m4v" -> "M17 10.5V7c0-.55-.45-1-1-1H4c-.55 0-1 .45-1 1v10c0 .55.45 1 1 1h12c.55 0 1-.45 1-1v-3.5l4 4v-11l-4 4zm-7 1.5l4 2.5-4 2.5V12z";
        case "mp3", "wav", "flac", "aac", "ogg", "wma", "m4a" -> "M12 3v10.55c-.59-.34-1.27-.55-2-.55-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4V7h4V3h-6zm0 12c0 1.1-.9 2-2 2s-2-.9-2-2 .9-2 2-2 2 .9 2 2z";
        case "zip", "rar", "7z", "tar", "gz", "bz2", "xz" -> "M20 6h-8l-2-2H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2zm-1 10h-3v-1h3v1zm0-2h-3v-1h3v1zm0-2h-3v-1h3v1z";
        case "txt", "rtf", "log", "md", "readme" -> "M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm4 18H6V4h7v5h5v11zm-8-8h6v1H10v-1zm0 2h6v1H10v-1zm0 2h4v1H10v-1z";
        case "java", "js", "py", "cpp", "c", "html", "css", "php", "rb", "go", "rs", "kt", "swift", "ts", "jsx", "tsx", "vue", "scala", "sh", "bat", "ps1" -> "M9.4 16.6L4.8 12l4.6-4.6L8 6l-6 6 6 6 1.4-1.4zm5.2 0l4.6-4.6-4.6-4.6L16 6l6 6-6 6-1.4-1.4z";
        case "json", "xml", "yml", "yaml", "toml", "ini", "cfg", "conf" -> "M12 3C7.58 3 4 4.79 4 7v10c0 2.21 3.58 4 8 4s8-1.79 8-4V7c0-2.21-3.58-4-8-4zm6 14c0 .5-2.69 2-6 2s-6-1.5-6-2v-2.23c1.61.78 3.72 1.23 6 1.23s4.39-.45 6-1.23V17zm0-4c0 .5-2.69 2-6 2s-6-1.5-6-2v-2.23c1.61.78 3.72 1.23 6 1.23s4.39-.45 6-1.23V13zm0-4c0 .5-2.69 2-6 2s-6-1.5-6-2v-2.23c1.61.78 3.72 1.23 6 1.23s4.39-.45 6-1.23V9zm-6-4c3.31 0 6 1.5 6 2s-2.69 2-6 2-6-1.5-6-2 2.69-2 6-2z";
        case "csv", "ods", "tsv" -> "M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm4 18H6V4h7v5h5v11zm-8-8h2v2H10v-2zm0 3h2v2H10v-2zm3-3h2v2h-2v-2zm0 3h2v2h-2v-2zm3-3h2v2h-2v-2zm0 3h2v2h-2v-2z";
        case "odp" -> "M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm4 18H6V4h7v5h5v11zm-8-8h4v1H10v-1zm0 2h6v1H10v-1zm0 2h2v1H10v-1z";
        case "ttf", "otf", "woff", "woff2", "eot" -> "M9 4v3h5v12h3V7h5V4H9zm-6 8h3v7h2v-7h3V9H3v3z";
        case "exe", "msi", "dmg", "pkg", "deb", "rpm", "app" -> "M19.43 12.98c.04-.32.07-.64.07-.98s-.03-.66-.07-.98l2.11-1.65c.19-.15.24-.42.12-.64l-2-3.46c-.12-.22-.39-.3-.61-.22l-2.49 1c-.52-.4-1.08-.73-1.69-.98l-.38-2.65C14.46 2.18 14.25 2 14 2h-4c-.25 0-.46.18-.49.42l-.38 2.65c-.61.25-1.17.59-1.69.98l-2.49-1c-.23-.09-.49 0-.61.22l-2 3.46c-.13.22-.07.49.12.64l2.11 1.65c-.04.32-.07.65-.07.98s.03.66.07.98l-2.11 1.65c-.19.15-.24.42-.12.64l2 3.46c.12.22.39.3.61.22l2.49-1c.52.4 1.08.73 1.69.98l.38 2.65c.03.24.24.42.49.42h4c.25 0 .46-.18.49-.42l.38-2.65c.61-.25 1.17-.59 1.69-.98l2.49 1c.23.09.49 0 .61-.22l2-3.46c.12-.22.07-.49-.12-.64l-2.11-1.65zM12 15.5c-1.93 0-3.5-1.57-3.5-3.5s1.57-3.5 3.5-3.5 3.5 1.57 3.5 3.5-1.57 3.5-3.5 3.5z";
        default -> "M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm4 18H6V4h7v5h5v11z";
    };
}
    
    private String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(lastDot + 1) : "";
    }
    
    private void removeFile(File file) {
        HBox fileItemToRemove = null;
        for (javafx.scene.Node node : selectedFilesList.getChildren()) {
            if (node instanceof HBox) {
                HBox fileItem = (HBox) node;
                VBox fileInfo = (VBox) fileItem.getChildren().get(1);
                Text fileName = (Text) fileInfo.getChildren().get(0);
                if (fileName.getText().equals(file.getName())) {
                    fileItemToRemove = fileItem;
                    break;
                }
            }
        }
        
        if (fileItemToRemove != null) {
            animateFileItemRemoval(fileItemToRemove, () -> {
                showToast("Removed: " + file.getName(), ToastNotification.NotificationType.INFO);
                selectedFiles.remove(file);
                totalSize -= file.length();
                updateFileDisplay();
                logger.info("Removed file: {}", file.getName());
            });
        }
    }
    
    private void animateFileItemRemoval(HBox fileItem, Runnable onComplete) {
        FadeTransition fadeTransition = new FadeTransition(Duration.millis(150), fileItem);
        fadeTransition.setFromValue(1);
        fadeTransition.setToValue(0);
        fileItem.setCacheHint(javafx.scene.CacheHint.SPEED);
        fadeTransition.setOnFinished(e -> {
            fileItem.setCacheHint(javafx.scene.CacheHint.DEFAULT);
            onComplete.run();
        });
        fadeTransition.play();
    }
    
    @FXML
    public void clearAllFiles() {
        if (selectedFiles.isEmpty()) return;
        animateButton(clearAllBtn);
        int fileCount = selectedFiles.size();
        showToast("Cleared " + fileCount + " file" + (fileCount > 1 ? "s" : ""), ToastNotification.NotificationType.INFO);
                animateFileListDisappearance();
                Timeline timeline = new Timeline(
                    new KeyFrame(Duration.millis(250), e -> {
                        selectedFiles.clear();
                        totalSize = 0;
                        updateFileDisplay();
                        logger.info("Cleared all selected files");
                    })
                );
                timeline.play();
    }
    
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
    
    private void showAlert(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        
        alert.getDialogPane().getStyleClass().add("dialog-pane");
        
        alert.showAndWait();
    }
    
    // --- POPUP LOGIC REFACTOR ---
    // Remove any FXML-based popup remnants (codePopup, transferCodeText, etc.)
    // All popups are built in Java using VBox/HBox and styled with your CSS
    
    @FXML
    public void startDirectTransfer() {
        if (selectedFiles.isEmpty()) {
            showAlert("No Files Selected", "Please select files to transfer.", "");
            return;
        }
        animateButton(directTransferBtn);
        disableFileUI(true);
        showEncryptionPopup();
        startEncryptionForTransfer();
    }
    
    private void showEncryptionPopup() {
        transferStage = new Stage();
        transferStage.initModality(Modality.APPLICATION_MODAL);
        transferStage.setTitle("Direct Transfer");
        transferStage.setResizable(true);
        transferStage.setAlwaysOnTop(true);
        VBox content = new VBox(20);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(32, 32, 32, 32));
        content.getStyleClass().add("transfer-popup-pane");
        content.setMinWidth(900);
        content.setMinHeight(600);

        Label icon = new Label("\uD83D\uDD12"); // Lock icon
        icon.getStyleClass().add("popup-icon");
        icon.setFont(Font.font("Segoe UI Emoji", 48));
        icon.setAlignment(Pos.CENTER);

        Label status = new Label("Encrypting files...");
        status.getStyleClass().add("label");
        status.setFont(Font.font("Inter", FontWeight.BOLD, 18));
        status.setTextFill(Color.web("#333"));
        status.setWrapText(true);
        status.setMaxWidth(700);
        status.setAlignment(Pos.CENTER);

        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(600);
        progressBar.getStyleClass().add("transfer-progress-bar");

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("popup-btn-red");
        cancelBtn.setMinWidth(160);
        cancelBtn.setFont(Font.font("Inter", FontWeight.BOLD, 15));
        cancelBtn.setOnAction(e -> handlePopupCancel());

        HBox buttonRow = new HBox(20, cancelBtn);
        buttonRow.setAlignment(Pos.CENTER);

        content.getChildren().addAll(icon, status, progressBar, buttonRow);
        Scene scene = new Scene(content);
        scene.getStylesheets().add(getClass().getResource("/styles/send-files.css").toExternalForm());
        transferStage.setScene(scene);
        transferStage.setMinWidth(900);
        transferStage.setMinHeight(600);
        transferStage.setOnCloseRequest(event -> {
            event.consume();
            handlePopupCancel();
        });
        transferStage.show();
        popupState = PopupState.ENCRYPTING;
        // Save references for later updates
        this.popupStatusLabel = status;
        this.encryptionProgressBar = progressBar;
        this.cancelButton = cancelBtn;
        this.buttonRow = buttonRow;
    }

    private void startEncryptionForTransfer() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256);
            SecretKey aesKey = keyGen.generateKey();
            byte[] ivBytes = new byte[16];
            new java.security.SecureRandom().nextBytes(ivBytes);
            IvParameterSpec iv = new IvParameterSpec(ivBytes);
            encryptionTask = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    encryptedFiles.clear();
                    int total = selectedFiles.size();
                    for (int i = 0; i < total; i++) {
                        final int fileIndex = i;
                        File inputFile = selectedFiles.get(i);
                        File outputFile = new File(inputFile.getParent(), inputFile.getName() + ".enc");
                            encryptionService.encryptFile(inputFile, outputFile, aesKey, iv, percent -> {
                                double progress = (fileIndex + percent) / total;
                            Platform.runLater(() -> {
                                if (encryptionProgressBar.progressProperty().isBound()) {
                                    encryptionProgressBar.progressProperty().unbind();
                                }
                                encryptionProgressBar.setProgress(progress);
                            });
                        }, () -> encryptionTask.isCancelled());
                        encryptedFiles.add(outputFile);
                    }
                    return null;
                }
            };
            encryptionProgressBar.progressProperty().bind(encryptionTask.progressProperty());
            encryptionTask.setOnSucceeded(e -> {
                popupState = PopupState.READY_TO_TRANSFER;
                encryptionProgressBar.progressProperty().unbind();
                encryptionProgressBar.setProgress(1.0);
                // Generate and show code immediately after encryption
                currentTransferCode = generateTransferCode();
                showCodeDetailsInPopup(currentTransferCode);
                // Start waiting for receiver in background
                registerForReceiverConnection();
            });
            encryptionTask.setOnFailed(e -> {
                encryptionProgressBar.progressProperty().unbind();
                showToast("Encryption failed: " + encryptionTask.getException().getMessage(), ToastNotification.NotificationType.ERROR);
                if (transferStage != null) transferStage.close();
                disableFileUI(false);
            });
            new Thread(encryptionTask).start();
        } catch (Exception ex) {
            showToast("Encryption setup failed: " + ex.getMessage(), ToastNotification.NotificationType.ERROR);
            if (transferStage != null) transferStage.close();
            disableFileUI(false);
        }
    }

    private void showTransferCodeInPopup() {
        currentTransferCode = generateTransferCode();
        String fileName = encryptedFiles.size() == 1 ? encryptedFiles.get(0).getName() : "MultipleFiles.zip";
        long fileSize = encryptedFiles.size() == 1 ? encryptedFiles.get(0).length() : encryptedFiles.stream().mapToLong(File::length).sum();
        transferService.initiateTransfer(currentTransferCode, selectedFiles, UserSession.getInstance().getCurrentUser().getUsername(), fileName, fileSize)
            .thenAccept(session -> {
                Platform.runLater(() -> {
                    currentSessionId = session.getSender().getSessionId();
                    showCodeDetailsInPopup(currentTransferCode);
                    registerForReceiverConnection();
                });
            })
            .exceptionally(throwable -> {
                Platform.runLater(() -> {
                    showToast("Failed to initialize transfer: " + throwable.getMessage(), ToastNotification.NotificationType.ERROR);
                    if (transferStage != null) transferStage.close();
                    disableFileUI(false);
                });
                return null;
            });
    }
    
    private void showCodeDetailsInPopup(String transferCode) {
            VBox content = new VBox(20);
            content.setAlignment(Pos.CENTER);
            content.setPadding(new Insets(32, 32, 32, 32));
        content.getStyleClass().add("transfer-popup-pane");
        content.setMinWidth(900);
        content.setMinHeight(600);
            Label icon = new Label("\uD83D\uDCE4"); // 📤
            icon.setFont(Font.font("Segoe UI Emoji", 48));
            icon.setAlignment(Pos.CENTER);
            Label heading = new Label("Share This Code");
            heading.setFont(Font.font("Inter", FontWeight.BOLD, 24));
            heading.setTextFill(Color.web("#059669"));
            heading.setAlignment(Pos.CENTER);
            heading.setMaxWidth(700);
            heading.setWrapText(true);
            Label status = new Label("Share this code with the receiver and wait for them to connect.\nKeep this window open until the transfer is complete.");
            status.setFont(Font.font("Inter", 16));
            status.setTextFill(Color.web("#374151"));
            status.setAlignment(Pos.CENTER);
            status.setMaxWidth(700);
            status.setWrapText(true);
            Label code = new Label(transferCode);
            code.getStyleClass().add("code-label");
            code.setFont(Font.font("Consolas", FontWeight.BOLD, 36));
            code.setTextFill(Color.web("#059669"));
            code.setAlignment(Pos.CENTER);
            code.setWrapText(true);
            code.setMaxWidth(700);
            code.setStyle("-fx-background-color: #f0fdf4; -fx-border-color: #059669; -fx-border-width: 3; -fx-border-radius: 12; -fx-background-radius: 12; -fx-padding: 20 40; -fx-font-size: 36px;");
            Region divider = new Region();
            divider.setMinWidth(700);
            divider.setPrefHeight(2);
            divider.setStyle("-fx-background-color: #e5e7eb;");
            Label note = new Label("Do not close this window until the transfer is complete.\nThe code is valid for 30 minutes.");
            note.setFont(Font.font("Inter", 14));
            note.setTextFill(Color.web("#6b7280"));
            note.setAlignment(Pos.CENTER);
            note.setWrapText(true);
            note.setMaxWidth(700);
        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("popup-btn-red");
        cancelBtn.setMinWidth(160);
        cancelBtn.setFont(Font.font("Inter", FontWeight.BOLD, 15));
        cancelBtn.setOnAction(e -> handlePopupCancel());
        HBox buttonRow = new HBox(16, cancelBtn);
            buttonRow.setAlignment(Pos.CENTER);
            content.getChildren().addAll(icon, heading, status, code, note, divider, buttonRow);
            transferStage.getScene().setRoot(content);
    }
    
    private void registerForReceiverConnection() {
        // Check for receiver connection every 2 seconds, with timeout and error handling
        final Timeline[] connectionChecker = new Timeline[1];
        final int[] elapsedSeconds = {0};
        final int timeoutSeconds = 180; // 3 minutes
        connectionChecker[0] = new Timeline(
            new KeyFrame(Duration.seconds(2), e -> {
                elapsedSeconds[0] += 2;
                if (transferService.isTransferActive(currentTransferCode)) {
                    showReceiverConnectionDialog();
                    connectionChecker[0].stop();
                } else if (elapsedSeconds[0] >= timeoutSeconds) {
                    showToast("No receiver connected within 3 minutes. Please try again.", ToastNotification.NotificationType.ERROR);
                    if (transferStage != null) transferStage.close();
                    disableFileUI(false);
                    connectionChecker[0].stop();
                }
            })
        );
        connectionChecker[0].setCycleCount(Timeline.INDEFINITE);
        connectionChecker[0].play();
    }
    
    private void showReceiverConnectionDialog() {
        Platform.runLater(() -> {
            Alert approvalAlert = new Alert(Alert.AlertType.CONFIRMATION);
            approvalAlert.setTitle("Receiver Connected");
            approvalAlert.setHeaderText("A receiver has connected with your transfer code.");
            approvalAlert.setContentText("Do you want to proceed with the file transfer?");
            approvalAlert.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
            
            approvalAlert.showAndWait().ifPresent(result -> {
                if (result == ButtonType.YES) {
                    startActualFileTransfer();
                } else {
                    transferService.cancelTransfer(currentTransferCode);
                    showToast("Transfer cancelled.", ToastNotification.NotificationType.INFO);
                    if (transferStage != null) transferStage.close();
                    disableFileUI(false);
                }
            });
        });
    }
    
    private void startActualFileTransfer() {
        popupStatusLabel.setText("Transferring files...");
        // transferButton.setVisible(false); // This line is removed as per the new_code
        buttonRow.getChildren().setAll(cancelButton);
        
        transferService.startFileTransfer(currentTransferCode, 
            progress -> {
                Platform.runLater(() -> {
                    if (encryptionProgressBar.progressProperty().isBound()) {
                        encryptionProgressBar.progressProperty().unbind();
                    }
                    encryptionProgressBar.setProgress(progress.getProgress());
                    popupStatusLabel.setText(String.format("Transferring: %.1f%%", progress.getProgress() * 100));
                });
            },
            complete -> {
                Platform.runLater(() -> {
                    if (encryptionProgressBar.progressProperty().isBound()) {
                        encryptionProgressBar.progressProperty().unbind();
                    }
                    if (complete.isSuccess()) {
                        popupStatusLabel.setText("Transfer completed successfully!");
                        encryptionProgressBar.setProgress(1.0);
                        showToast("Files transferred successfully!", ToastNotification.NotificationType.SUCCESS);
                        
                        // Close popup after 2 seconds
                        new Timeline(new KeyFrame(Duration.seconds(2), e -> {
                            if (transferStage != null) transferStage.close();
                            disableFileUI(false);
                        })).play();
                    } else {
                        popupStatusLabel.setText("Transfer failed: " + complete.getErrorMessage());
                        showToast("Transfer failed: " + complete.getErrorMessage(), ToastNotification.NotificationType.ERROR);
                        cancelButton.setText("Close");
                    }
                });
            }
        ).exceptionally(throwable -> {
            Platform.runLater(() -> {
                showToast("Transfer failed: " + throwable.getMessage(), ToastNotification.NotificationType.ERROR);
                if (transferStage != null) transferStage.close();
                disableFileUI(false);
            });
            return null;
        });
    }

    private void disableFileUI(boolean disable) {
        selectFilesBtn.setDisable(disable);
        clearAllBtn.setDisable(disable);
        directTransferBtn.setDisable(disable);
        selectedFilesList.setDisable(disable);
        fileDropZone.setDisable(disable);
    }
    
    private String generateTransferCode() {
        SecureRandom random = new SecureRandom();
        int code = 100000 + random.nextInt(900000); // 6-digit
        return String.valueOf(code);
    }

    // This method should be called when a receiver connects with the code
    private void onReceiverConnectionRequest(String receiverInfo) {
        Platform.runLater(() -> {
            Alert approvalAlert = new Alert(Alert.AlertType.CONFIRMATION);
            approvalAlert.setTitle("File Transfer Request");
            approvalAlert.setHeaderText("Device " + receiverInfo + " requests your files.");
            approvalAlert.setContentText("Do you want to send the files?");
            approvalAlert.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
            approvalAlert.showAndWait().ifPresent(result -> {
                if (result == ButtonType.OK) {
                    // Start file transfer logic
                    transferService.startFileTransfer(currentTransferCode, progress -> {
                        // Optionally update UI with progress
                        logger.info("Transfer progress: {}%", progress.getProgress() * 100);
                    }, complete -> {
                        Platform.runLater(() -> {
                            if (complete.isSuccess()) {
                                showToast("Transfer complete!", ToastNotification.NotificationType.SUCCESS);
                            } else {
                                showToast("Transfer failed: " + complete.getErrorMessage(), ToastNotification.NotificationType.ERROR);
                            }
                        });
                    });
                    showTransferProgress("Transferring Files", "Sending files to receiver...");
                } else {
                    // Reject connection logic (could notify receiver via WebSocket or update status)
                    showToast("Transfer cancelled.", ToastNotification.NotificationType.INFO);
                    // TODO: Optionally notify receiver of rejection
                }
            });
        });
    }
    

    private void showSuccessDialogWithLink(String link) {
        Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
        successAlert.setTitle("Secure Link Generated");
        successAlert.setHeaderText("Your secure link is ready!");
        successAlert.setContentText(link);
        successAlert.getDialogPane().getStyleClass().add("dialog-pane");
        successAlert.showAndWait();
    }
    
    private void showTransferProgress(String title, String message) {
        Alert progressAlert = new Alert(Alert.AlertType.INFORMATION);
        progressAlert.setTitle(title);
        progressAlert.setHeaderText("Transfer in Progress");
        progressAlert.setContentText(message);
        
        progressAlert.getDialogPane().getStyleClass().add("dialog-pane");
        
        // Add progress indicator
        ProgressIndicator progressIndicator = new ProgressIndicator();
        progressIndicator.getStyleClass().add("progress-indicator");
        progressAlert.getDialogPane().setExpandableContent(progressIndicator);
        progressAlert.getDialogPane().setExpanded(true);
        
        // Remove OK button for now
        progressAlert.getButtonTypes().clear();
        
        progressAlert.show();
    }
    
    private void simulateTransferProgress() {
        // This is a placeholder for actual transfer implementation
        // In a real implementation, this would handle actual file transfer
        Timeline timeline = new Timeline(
            new KeyFrame(Duration.seconds(2), e -> {
                // Close progress dialog and show success
                Stage stage = (Stage) Stage.getWindows().stream()
                    .filter(Window::isShowing)
                    .filter(w -> w instanceof Stage)
                    .findFirst()
                    .orElse(null);
                
                if (stage != null) {
                    stage.close();
                }
                
                showSuccessDialog();
            })
        );
        timeline.play();
    }
    
    private void showSuccessDialog() {
        Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
        successAlert.setTitle("Transfer Complete");
        successAlert.setHeaderText("Files transferred successfully!");
        successAlert.setContentText("All " + selectedFiles.size() + " files have been processed.");
        
        successAlert.getDialogPane().getStyleClass().add("dialog-pane");
        
        successAlert.showAndWait();
    }
    
    private Stage getStage() {
        return (Stage) selectedFilesList.getScene().getWindow();
    }
    
    @Override
    protected Stage getCurrentStage() {
        return getStage();
    }
    
    private void showToast(String message, ToastNotification.NotificationType type) {
        Stage stage = (Stage) selectedFilesList.getScene().getWindow();
        ToastNotification.show(stage, message, type, Duration.seconds(3), 70);
    }
    
    private ScrollPane findScrollPane(javafx.scene.Node node) {
        javafx.scene.Node parent = node.getParent();
        while (parent != null) {
            if (parent instanceof ScrollPane) {
                return (ScrollPane) parent;
            }
            parent = parent.getParent();
        }
        return null;
    }
    
    private void encryptSelectedFiles() {
        if (selectedFiles.isEmpty()) return;
        try {
            // Generate AES key and IV for this session
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256);
            SecretKey aesKey = keyGen.generateKey();
            byte[] ivBytes = new byte[16];
            new java.security.SecureRandom().nextBytes(ivBytes);
            IvParameterSpec iv = new IvParameterSpec(ivBytes);

            // Prepare progress dialog
            Alert progressAlert = new Alert(Alert.AlertType.INFORMATION);
            progressAlert.setTitle("Encrypting Files");
            progressAlert.setHeaderText("Encryption in Progress");
            ProgressBar progressBar = new ProgressBar(0);
            progressBar.setPrefWidth(300);
            progressAlert.getDialogPane().setContent(progressBar);
            progressAlert.getDialogPane().getButtonTypes().clear();
            progressAlert.show();

            Task<Void> encryptionTask = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    encryptedFiles.clear();
                    int total = selectedFiles.size();
                    for (int i = 0; i < total; i++) {
                        final int fileIndex = i;
                        File inputFile = selectedFiles.get(i);
                        File outputFile = new File(inputFile.getParent(), inputFile.getName() + ".enc");
                        encryptionService.encryptFile(inputFile, outputFile, aesKey, iv, percent -> {
                            updateProgress(fileIndex + percent, total);
                        }, () -> false);
                        encryptedFiles.add(outputFile);
                    }
                    return null;
                }
            };

            progressBar.progressProperty().bind(encryptionTask.progressProperty());

            encryptionTask.setOnSucceeded(e -> {
                progressAlert.setHeaderText("Encryption Complete");
                progressAlert.setContentText("All files encrypted. Ready to transfer.");
                progressBar.progressProperty().unbind();
                progressBar.setProgress(1.0);
                // Optionally close dialog after short delay
                new Timeline(new KeyFrame(Duration.seconds(1.5), ev -> progressAlert.close())).play();
                showToast("Encryption complete. Ready to transfer.", ToastNotification.NotificationType.SUCCESS);
            });
            encryptionTask.setOnFailed(e -> {
                progressAlert.close();
                showToast("Encryption failed: " + encryptionTask.getException().getMessage(), ToastNotification.NotificationType.ERROR);
            });

            new Thread(encryptionTask).start();
        } catch (Exception ex) {
            showToast("Encryption setup failed: " + ex.getMessage(), ToastNotification.NotificationType.ERROR);
        }
    }

    private void showReadyToTransferFiles() {
        VBox content = new VBox(20);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(32, 32, 32, 32));
        content.getStyleClass().add("transfer-popup-pane");
        content.setMinWidth(900);
        content.setMinHeight(600);

        Label icon = new Label("\uD83D\uDCE6"); // 📦
        icon.getStyleClass().add("popup-icon");
        icon.setFont(Font.font("Segoe UI Emoji", 48));
        icon.setAlignment(Pos.CENTER);

        Label heading = new Label("Ready to Transfer");
        heading.setFont(Font.font("Inter", FontWeight.BOLD, 24));
        heading.setTextFill(Color.web("#6366f1"));
        heading.setAlignment(Pos.CENTER);
        heading.setMaxWidth(700);
        heading.setWrapText(true);

        Label status = new Label("The following files are encrypted and are now ready to transfer.");
        status.setFont(Font.font("Inter", 16));
        status.setTextFill(Color.web("#374151"));
        status.setAlignment(Pos.CENTER);
        status.setMaxWidth(700);
        status.setWrapText(true);

        VBox fileList = new VBox(8);
        fileList.getStyleClass().add("file-list-section");
        fileList.setAlignment(Pos.CENTER_LEFT);
        fileList.setPadding(new Insets(12, 0, 12, 0));
        fileList.setMaxWidth(700);
        fileList.setMaxHeight(200);
        for (File f : selectedFiles) {
            HBox fileRow = new HBox(12);
            fileRow.getStyleClass().add("file-list-item");
            fileRow.setAlignment(Pos.CENTER_LEFT);
            fileRow.setPadding(new Insets(4, 8, 4, 8));
            Label fileIcon = new Label("\uD83D\uDCC4"); // 📄
            fileIcon.setFont(Font.font("Segoe UI Emoji", 18));
            fileIcon.setMinWidth(24);
            Label fileLabel = new Label(f.getName());
            fileLabel.setFont(Font.font("Inter", 14));
            fileLabel.setTextFill(Color.web("#374151"));
            fileLabel.setWrapText(true);
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Label fileSize = new Label(formatFileSize(f.length()));
            fileSize.setFont(Font.font("Inter", FontWeight.NORMAL, 13));
            fileSize.setTextFill(Color.web("#64748b"));
            fileSize.setMinWidth(80);
            fileRow.getChildren().addAll(fileIcon, fileLabel, spacer, fileSize);
            fileList.getChildren().add(fileRow);
        }
        ScrollPane scrollPane = null;
        if (selectedFiles.size() > 5) {
            scrollPane = new ScrollPane(fileList);
            scrollPane.setFitToWidth(true);
            scrollPane.setFitToHeight(true);
            scrollPane.setMaxHeight(200);
            scrollPane.setPrefHeight(200);
            scrollPane.getStyleClass().add("file-list-scroll");
        }
        Region divider = new Region();
        divider.getStyleClass().add("popup-divider");
        divider.setMinWidth(700);
        divider.setPrefHeight(2);
        divider.setStyle("-fx-background-color: #e5e7eb;");
        Label note = new Label("Your files are encrypted with AES-256 and ready for secure transfer.");
        note.getStyleClass().add("popup-note");
        note.setFont(Font.font("Inter", 14));
        note.setTextFill(Color.web("#6b7280"));
        note.setAlignment(Pos.CENTER);
        note.setWrapText(true);
        note.setMaxWidth(700);
        Button transferBtn = new Button("Transfer");
        transferBtn.getStyleClass().add("popup-btn-green");
        transferBtn.setMinWidth(160);
        transferBtn.setFont(Font.font("Inter", FontWeight.BOLD, 15));
        transferBtn.setOnAction(e -> showTransferCodeInPopup());
        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("popup-btn-red");
        cancelBtn.setMinWidth(160);
        cancelBtn.setFont(Font.font("Inter", FontWeight.BOLD, 15));
        cancelBtn.setOnAction(e -> handlePopupCancel());
        HBox buttonRow = new HBox(20, transferBtn, cancelBtn);
        buttonRow.setAlignment(Pos.CENTER);
        if (scrollPane != null) {
            content.getChildren().addAll(icon, heading, status, scrollPane, note, divider, buttonRow);
        } else {
            content.getChildren().addAll(icon, heading, status, fileList, note, divider, buttonRow);
        }
        transferStage.getScene().setRoot(content);
    }

    private void handlePopupCancel() {
        Platform.runLater(() -> {
            if (transferStage != null) {
                transferStage.close();
                transferStage = null;
            }
            disableFileUI(false);
            if (popupState == PopupState.ENCRYPTING) {
                if (encryptionTask != null && encryptionTask.isRunning()) {
                    logger.info("Cancelling encryption task...");
                    encryptionTask.cancel();
                }
                showToast("Encryption cancelled.", ToastNotification.NotificationType.INFO);
            } else {
                showToast("Transfer cancelled.", ToastNotification.NotificationType.INFO);
        }
        });
    }
}

