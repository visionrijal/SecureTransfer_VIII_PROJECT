package com.securetransfer.controller.ui;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.scene.shape.SVGPath;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
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
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.geometry.Pos;
import javafx.geometry.Insets;
import javafx.stage.Modality;
import javafx.scene.Scene;
import javafx.scene.layout.Priority;

@Controller
public class SendFilesController extends BaseController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(SendFilesController.class);

    private static final int MAX_FILES = 10;
    private static final long MAX_TOTAL_SIZE = 500 * 1024 * 1024;
    private static final long MAX_FILE_SIZE = 500 * 1024 * 1024;

    @FXML
    private VBox selectedFilesList;
    @FXML
    private Text fileCountText;
    @FXML
    private Text totalSizeText;
    @FXML
    private VBox selectedFilesContainer;
    @FXML
    private VBox fileDropZone;
    @FXML
    private Button selectFilesBtn;
    @FXML
    private Button clearAllBtn;
    @FXML
    private Button directTransferBtn;

    private List<File> selectedFiles = new ArrayList<>();
    private long totalSize = 0;

    @Autowired
    private EncryptionService encryptionService;

    @Autowired
    private TransferService transferService;

    private List<File> encryptedFiles = new ArrayList<>();

    private String currentTransferCode;
    private String currentSessionId;
    private long transferStartTime;

    private Stage transferStage;
    private ProgressBar encryptionProgressBar;
    private Task<Void> encryptionTask;

    private enum PopupState {
        ENCRYPTING, READY_TO_TRANSFER
    }

    private PopupState popupState;
    private HBox buttonRow;
    private Label popupStatusLabel;
    private Button cancelButton;

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
        if (button == null)
            return;

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
        logger.info("MainController reference set in SendFilesController (stub)");
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
                new FileChooser.ExtensionFilter("Presentations", "*.ppt", "*.pptx", "*.odp"));
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
                showToast(errors.size() + " files could not be added. Max: " + MAX_FILES + " files, "
                        + formatFileSize(MAX_TOTAL_SIZE) + " total.", ToastNotification.NotificationType.ERROR);
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
            case "pdf" ->
                "M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm4 18H6V4h7v5h5v11zm-8-8h6v1H10v-1zm0 2h6v1H10v-1zm0 2h4v1H10v-1z";
            case "doc", "docx" ->
                "M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm4 18H6V4h7v5h5v11zm-7-6h4l-2-3-2 3zm1 1v3h2v-3h-2z";
            case "xls", "xlsx" ->
                "M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm4 18H6V4h7v5h5v11zm-8-8h2v2H10v-2zm0 3h2v2H10v-2zm3-3h2v2h-2v-2zm0 3h2v2h-2v-2z";
            case "ppt", "pptx" ->
                "M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm4 18H6V4h7v5h5v11zm-8-8h4v1H10v-1zm0 2h6v1H10v-1zm0 2h2v1H10v-1z";
            case "jpg", "jpeg", "png", "gif", "bmp", "tiff", "webp", "svg" ->
                "M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2zm-2 0H5V5h14v14zm-7-7.5L9 15l-2.5-3.5L4 17h16l-4.5-6L12 11.5z";
            case "mp4", "avi", "mov", "wmv", "flv", "mkv", "webm", "m4v" ->
                "M17 10.5V7c0-.55-.45-1-1-1H4c-.55 0-1 .45-1 1v10c0 .55.45 1 1 1h12c.55 0 1-.45 1-1v-3.5l4 4v-11l-4 4zm-7 1.5l4 2.5-4 2.5V12z";
            case "mp3", "wav", "flac", "aac", "ogg", "wma", "m4a" ->
                "M12 3v10.55c-.59-.34-1.27-.55-2-.55-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4V7h4V3h-6zm0 12c0 1.1-.9 2-2 2s-2-.9-2-2 .9-2 2-2 2 .9 2 2z";
            case "zip", "rar", "7z", "tar", "gz", "bz2", "xz" ->
                "M20 6h-8l-2-2H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2zm-1 10h-3v-1h3v1zm0-2h-3v-1h3v1zm0-2h-3v-1h3v1z";
            case "txt", "rtf", "log", "md", "readme" ->
                "M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm4 18H6V4h7v5h5v11zm-8-8h6v1H10v-1zm0 2h6v1H10v-1zm0 2h4v1H10v-1z";
            case "java", "js", "py", "cpp", "c", "html", "css", "php", "rb", "go", "rs", "kt", "swift", "ts", "jsx",
                    "tsx", "vue", "scala", "sh", "bat", "ps1" ->
                "M9.4 16.6L4.8 12l4.6-4.6L8 6l-6 6 6 6 1.4-1.4zm5.2 0l4.6-4.6-4.6-4.6L16 6l6 6-6 6-1.4-1.4z";
            case "json", "xml", "yml", "yaml", "toml", "ini", "cfg", "conf" ->
                "M12 3C7.58 3 4 4.79 4 7v10c0 2.21 3.58 4 8 4s8-1.79 8-4V7c0-2.21-3.58-4-8-4zm6 14c0 .5-2.69 2-6 2s-6-1.5-6-2v-2.23c1.61.78 3.72 1.23 6 1.23s4.39-.45 6-1.23V17zm0-4c0 .5-2.69 2-6 2s-6-1.5-6-2v-2.23c1.61.78 3.72 1.23 6 1.23s4.39-.45 6-1.23V13zm0-4c0 .5-2.69 2-6 2s-6-1.5-6-2v-2.23c1.61.78 3.72 1.23 6 1.23s4.39-.45 6-1.23V9zm-6-4c3.31 0 6 1.5 6 2s-2.69 2-6 2-6-1.5-6-2 2.69-2 6-2z";
            case "csv", "ods", "tsv" ->
                "M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm4 18H6V4h7v5h5v11zm-8-8h2v2H10v-2zm0 3h2v2H10v-2zm3-3h2v2h-2v-2zm0 3h2v2h-2v-2zm3-3h2v2h-2v-2zm0 3h2v2h-2v-2z";
            case "odp" ->
                "M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm4 18H6V4h7v5h5v11zm-8-8h4v1H10v-1zm0 2h6v1H10v-1zm0 2h2v1H10v-1z";
            case "ttf", "otf", "woff", "woff2", "eot" -> "M9 4v3h5v12h3V7h5V4H9zm-6 8h3v7h2v-7h3V9H3v3z";
            case "exe", "msi", "dmg", "pkg", "deb", "rpm", "app" ->
                "M19.43 12.98c.04-.32.07-.64.07-.98s-.03-.66-.07-.98l2.11-1.65c.19-.15.24-.42.12-.64l-2-3.46c-.12-.22-.39-.3-.61-.22l-2.49 1c-.52-.4-1.08-.73-1.69-.98l-.38-2.65C14.46 2.18 14.25 2 14 2h-4c-.25 0-.46.18-.49.42l-.38 2.65c-.61.25-1.17.59-1.69.98l-2.49-1c-.23-.09-.49 0-.61.22l-2 3.46c-.13.22-.07.49.12.64l2.11 1.65c-.04.32-.07.65-.07.98s.03.66.07.98l-2.11 1.65c-.19.15-.24.42-.12.64l2 3.46c.12.22.39.3.61.22l2.49-1c.52.4 1.08.73 1.69.98l.38 2.65c.03.24.24.42.49.42h4c.25 0 .46-.18.49-.42l.38-2.65c.61-.25 1.17-.59 1.69-.98l2.49 1c.23.09.49 0 .61-.22l2-3.46c.12-.22.07-.49-.12-.64l-2.11-1.65zM12 15.5c-1.93 0-3.5-1.57-3.5-3.5s1.57-3.5 3.5-3.5 3.5 1.57 3.5 3.5-1.57 3.5-3.5 3.5z";
            default ->
                "M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm4 18H6V4h7v5h5v11z";
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
        if (selectedFiles.isEmpty())
            return;
        animateButton(clearAllBtn);
        int fileCount = selectedFiles.size();
        showToast("Cleared " + fileCount + " file" + (fileCount > 1 ? "s" : ""),
                ToastNotification.NotificationType.INFO);
        animateFileListDisappearance();
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.millis(250), e -> {
                    selectedFiles.clear();
                    totalSize = 0;
                    updateFileDisplay();
                    logger.info("Cleared all selected files");
                }));
        timeline.play();
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024)
            return bytes + " B";
        if (bytes < 1024 * 1024)
            return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024)
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
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

        buttonRow = new HBox(20, cancelBtn);
        buttonRow.setAlignment(Pos.CENTER);

        content.getChildren().addAll(icon, status, progressBar, buttonRow);
        Scene scene = new Scene(content);
        URL cssResource = getClass().getResource("/styles/global.css");
        if (cssResource != null) {
            scene.getStylesheets().add(cssResource.toExternalForm());
        }
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
                logger.info("Encryption completed - generated transfer code: {}", currentTransferCode);

                // Register the transfer with the service
                String fileName = encryptedFiles.size() == 1 ? encryptedFiles.get(0).getName() : "MultipleFiles.zip";
                long fileSize = encryptedFiles.size() == 1 ? encryptedFiles.get(0).length()
                        : encryptedFiles.stream().mapToLong(File::length).sum();
                logger.info("About to call initiateTransfer with code: {}", currentTransferCode);

                transferService
                        .initiateTransfer(currentTransferCode, selectedFiles,
                                UserSession.getInstance().getCurrentUser().getUsername(), fileName, fileSize)
                        .thenAccept(session -> {
                            Platform.runLater(() -> {
                                logger.info("Transfer initiated successfully - session ID: {}",
                                        session.getSender().getSessionId());
                                currentSessionId = session.getSender().getSessionId();
                                showCodeDetailsInPopup(currentTransferCode);
                                registerForReceiverConnection();
                            });
                        })
                        .exceptionally(throwable -> {
                            Platform.runLater(() -> {
                                logger.error("Failed to initialize transfer: {}", throwable.getMessage(), throwable);
                                showToast("Failed to initialize transfer: " + throwable.getMessage(),
                                        ToastNotification.NotificationType.ERROR);
                                if (transferStage != null)
                                    transferStage.close();
                                disableFileUI(false);
                            });
                            return null;
                        });
            });
            encryptionTask.setOnFailed(e -> {
                encryptionProgressBar.progressProperty().unbind();
                showToast("Encryption failed: " + encryptionTask.getException().getMessage(),
                        ToastNotification.NotificationType.ERROR);
                if (transferStage != null)
                    transferStage.close();
                disableFileUI(false);
            });
            new Thread(encryptionTask).start();
        } catch (Exception ex) {
            showToast("Encryption setup failed: " + ex.getMessage(), ToastNotification.NotificationType.ERROR);
            if (transferStage != null)
                transferStage.close();
            disableFileUI(false);
        }
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
        heading.setTextFill(Color.web("#6366f1")); // indigo
        heading.setAlignment(Pos.CENTER);
        heading.setMaxWidth(700);
        heading.setWrapText(true);
        Label status = new Label(
                "Share this code with the receiver and wait for them to connect.\nKeep this window open until the transfer is complete.");
        status.setFont(Font.font("Inter", 16));
        status.setTextFill(Color.web("#374151"));
        status.setAlignment(Pos.CENTER);
        status.setMaxWidth(700);
        status.setWrapText(true);
        Label code = new Label(transferCode);
        code.getStyleClass().add("code-label");
        code.setFont(Font.font("Consolas", FontWeight.BOLD, 36));
        code.setTextFill(Color.web("#6366f1"));
        code.setAlignment(Pos.CENTER);
        code.setWrapText(true);
        code.setMaxWidth(700);
        code.setStyle(
                "-fx-background-color: rgba(99, 102, 241, 0.1); -fx-border-color: #6366f1; -fx-border-width: 2; -fx-border-radius: 12; -fx-background-radius: 12; -fx-padding: 20 40;");
        Region divider = new Region();
        divider.setMinWidth(700);
        divider.setPrefHeight(2);
        divider.setStyle("-fx-background-color: rgba(99, 102, 241, 0.2);");
        Label note = new Label(
                "Do not close this window until the transfer is complete.\nThe code is valid for 30 minutes.");
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
        buttonRow = new HBox(16, cancelBtn);
        buttonRow.setAlignment(Pos.CENTER);
        content.getChildren().addAll(icon, heading, status, code, note, divider, buttonRow);
        transferStage.getScene().setRoot(content);
    }

    private void registerForReceiverConnection() {
        // Register callback for when receiver connects
        transferService.registerReceiverConnectionCallback(currentTransferCode, () -> {
            Platform.runLater(() -> {
                showReceiverConnectionDialog();
            });
        });

        // Connect as WebSocket client (sender) to receive peerConnected events
        transferService.connectSenderWebSocketClient(currentTransferCode,
                UserSession.getInstance().getCurrentUser().getUsername());

        // Also set a timeout in case no receiver connects
        Timeline timeoutTimer = new Timeline(
                new KeyFrame(Duration.seconds(180), e -> { // 3 minutes timeout
                    Platform.runLater(() -> {
                        showToast("No receiver connected within 3 minutes. Please try again.",
                                ToastNotification.NotificationType.ERROR);
                        if (transferStage != null)
                            transferStage.close();
                        disableFileUI(false);
                    });
                }));
        timeoutTimer.play();
    }

    private void showReceiverConnectionDialog() {
        Platform.runLater(() -> {
            // Create a custom dialog with better styling
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle("Receiver Connected");
            dialog.setHeaderText("A receiver has connected with your transfer code.");

            // Create custom content
            VBox content = new VBox(20);
            content.setAlignment(Pos.CENTER);
            content.setPadding(new Insets(20));
            content.setMinWidth(400);

            Label icon = new Label("\uD83D\uDDA5\uFE0F"); // 🖥️
            icon.setFont(Font.font("Segoe UI Emoji", 48));

            Label status = new Label("Do you want to start the file transfer?");
            status.setFont(Font.font("Inter", 14));

            content.getChildren().addAll(icon, status);
            dialog.getDialogPane().setContent(content);
            dialog.getDialogPane().getStyleClass().add("dialog-pane");

            ButtonType startButtonType = new ButtonType("Start Transfer", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(startButtonType, ButtonType.CANCEL);

            dialog.showAndWait().ifPresent(response -> {
                if (response == startButtonType) {
                    startFileTransfer();
                } else {
                    handlePopupCancel();
                }
            });
        });
    }

    private void startFileTransfer() {
        VBox content = new VBox(25);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(40));
        content.getStyleClass().add("transfer-popup-pane");
        content.setMinWidth(900);
        content.setMinHeight(600);

        Label icon = new Label("\u26A1"); // ⚡
        icon.setFont(Font.font("Segoe UI Emoji", 64));
        icon.getStyleClass().add("popup-icon-neon");

        Label heading = new Label("Transferring Files...");
        heading.setFont(Font.font("Inter", FontWeight.BOLD, 28));
        heading.setTextFill(Color.web("#a855f7")); // Purple
        heading.setAlignment(Pos.CENTER);

        Label fileInfoLabel = new Label("Directing encrypted stream...");
        fileInfoLabel.setFont(Font.font("Inter", 16));
        fileInfoLabel.setTextFill(Color.web("#4b5563"));
        fileInfoLabel.setAlignment(Pos.CENTER);

        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(600);
        progressBar.setPrefHeight(20);
        progressBar.getStyleClass().add("transfer-progress-bar-neon");

        // Progress label
        Label progressLabel = new Label("Preparing transfer...");
        progressLabel.setFont(Font.font("Inter", 14));
        progressLabel.setTextFill(Color.web("#6b7280"));
        progressLabel.setAlignment(Pos.CENTER);

        // Speed label
        Label speedLabel = new Label("");
        speedLabel.setFont(Font.font("Inter", 12));
        speedLabel.setTextFill(Color.web("#9ca3af"));
        speedLabel.setAlignment(Pos.CENTER);

        Button cancelBtn = new Button("Cancel Transfer");
        cancelBtn.getStyleClass().add("popup-btn-red");
        cancelBtn.setMinWidth(160);
        cancelBtn.setFont(Font.font("Inter", FontWeight.BOLD, 15));
        cancelBtn.setOnAction(e -> handlePopupCancel());

        buttonRow = new HBox(20, cancelBtn);
        buttonRow.setAlignment(Pos.CENTER);

        content.getChildren().addAll(icon, heading, fileInfoLabel, progressBar, progressLabel, speedLabel, buttonRow);
        transferStage.getScene().setRoot(content);

        // Save references for progress updates
        this.encryptionProgressBar = progressBar;
        this.popupStatusLabel = progressLabel;
        this.cancelButton = cancelBtn;

        transferStartTime = System.currentTimeMillis();

        // Start the actual file transfer
        transferService.startFileTransfer(currentTransferCode,
                progress -> {
                    Platform.runLater(() -> {
                        if (encryptionProgressBar.progressProperty().isBound()) {
                            encryptionProgressBar.progressProperty().unbind();
                        }
                        encryptionProgressBar.setProgress(progress.getProgress());

                        // Update progress label with percentage and file info
                        String fileName = progress.getFileName() != null ? progress.getFileName() : "files";
                        progressLabel.setText(
                                String.format("Transferring %s: %.1f%%", fileName, progress.getProgress() * 100));

                        // Calculate and show transfer speed
                        if (progress.getBytesTransferred() > 0) {
                            long bytesPerSecond = progress.getBytesTransferred()
                                    / Math.max(1, (System.currentTimeMillis() - transferStartTime) / 1000);
                            speedLabel.setText(String.format("Speed: %s/s", formatFileSize(bytesPerSecond)));
                        }
                    });
                },
                complete -> {
                    Platform.runLater(() -> {
                        if (encryptionProgressBar.progressProperty().isBound()) {
                            encryptionProgressBar.progressProperty().unbind();
                        }
                        if (complete.isSuccess()) {
                            icon.setText("\u2705"); // ✅
                            heading.setText("Transfer Complete!");
                            progressLabel.setText("Files transferred successfully!");
                            encryptionProgressBar.setProgress(1.0);
                            speedLabel.setText("");
                            showToast("Files transferred successfully!", ToastNotification.NotificationType.SUCCESS);

                            // Change cancel button to close button
                            cancelBtn.setText("Close");
                            cancelBtn.setOnAction(e -> {
                                if (transferStage != null)
                                    transferStage.close();
                                disableFileUI(false);
                            });
                        } else {
                            icon.setText("\u274C"); // ❌
                            heading.setText("Transfer Failed");
                            heading.setTextFill(Color.web("#ef4444"));
                            progressLabel.setText("Error: " + complete.getErrorMessage());
                            showToast("Transfer failed: " + complete.getErrorMessage(),
                                    ToastNotification.NotificationType.ERROR);
                            cancelBtn.setText("Close");
                        }
                    });
                });
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

    private void disableFileUI(boolean disable) {
        selectFilesBtn.setDisable(disable);
        clearAllBtn.setDisable(disable);
        directTransferBtn.setDisable(disable);
        selectedFilesList.setDisable(disable);
    }

    private String generateTransferCode() {
        SecureRandom random = new SecureRandom();
        int code = 100000 + random.nextInt(900000); // 6-digit
        return String.valueOf(code);
    }

    private void showToast(String message, ToastNotification.NotificationType type) {
        if (selectedFilesList != null && selectedFilesList.getScene() != null
                && selectedFilesList.getScene().getWindow() != null) {
            Stage stage = (Stage) selectedFilesList.getScene().getWindow();
            ToastNotification.show(stage, message, type, Duration.seconds(3), 70);
        } else {
            logger.warn("Could not show toast: Scene or Window is null. Message: {}", message);
        }
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

    private Stage getStage() {
        return (Stage) selectedFilesList.getScene().getWindow();
    }

    @Override
    protected Stage getCurrentStage() {
        return getStage();
    }
}
