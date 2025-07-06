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

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicInteger;

@Controller
public class SendFilesController extends BaseController implements Initializable {
    
    private static final Logger logger = LoggerFactory.getLogger(SendFilesController.class);
    
    private static final int MAX_FILES = 5;
    private static final long MAX_TOTAL_SIZE = 100 * 1024 * 1024; 
    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024; 
    
    @FXML private VBox selectedFilesList;
    @FXML private Text fileCountText;
    @FXML private Text totalSizeText;
    @FXML private VBox selectedFilesContainer;
    @FXML private VBox fileDropZone;
    @FXML private Button selectFilesBtn;
    @FXML private Button clearAllBtn;
    @FXML private Button directTransferBtn;
    @FXML private Button generateLinkBtn;
    
    private List<File> selectedFiles = new ArrayList<>();
    private long totalSize = 0;
    private MainController mainController;
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.info("SendFilesController initialized");
        updateFileDisplay();
        setupAnimations();
        setupButtonHoverEffects();
    }
    
    private void setupAnimations() {
        // Add subtle fade-in animation to the container when files are selected
        selectedFilesContainer.setOpacity(0);
        selectedFilesContainer.setVisible(false);
        selectedFilesContainer.setManaged(false);
    }
    
    private void setupButtonHoverEffects() {
        // Add hover effects to buttons
        setupButtonHoverEffect(selectFilesBtn);
        setupButtonHoverEffect(clearAllBtn);
        setupButtonHoverEffect(directTransferBtn);
        setupButtonHoverEffect(generateLinkBtn);
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
        
        for (File file : files) {
            String errorMessage = validateFile(file);
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
            showMultipleFileErrors(errors);
        }
        
        if (!validFiles.isEmpty()) {
            updateFileDisplay();
            animateFileListAppearance();
        }
    }
    
    private String validateFile(File file) {
        if (selectedFiles.size() >= MAX_FILES) {
            return "Maximum " + MAX_FILES + " files allowed";
        }
        if (file.length() > MAX_FILE_SIZE) {
            return "File too large (max 100MB)";
        }
        if (totalSize + file.length() > MAX_TOTAL_SIZE) {
            return "Would exceed 100MB total limit";
        }
        if (selectedFiles.stream().anyMatch(f -> f.getName().equals(file.getName()))) {
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
        selectedFilesList.getChildren().clear();
        
        if (selectedFiles.isEmpty()) {
            animateFileListDisappearance();
        } else {
            if (!selectedFilesContainer.isVisible()) {
                selectedFilesContainer.setVisible(true);
                selectedFilesContainer.setManaged(true);
                selectedFilesContainer.setOpacity(1);
            }
            AtomicInteger index = new AtomicInteger(0);
            for (File file : selectedFiles) {
                HBox fileItem = createFileItem(file);
                selectedFilesList.getChildren().add(fileItem);
                Platform.runLater(() -> {
                    Timeline timeline = new Timeline(
                        new KeyFrame(Duration.millis(50 * index.getAndIncrement()), e -> {
                            animateFileItemAppearance(fileItem);
                        })
                    );
                    timeline.play();
                });
            }
        }
        
        fileCountText.setText(String.format("(%d/%d)", selectedFiles.size(), MAX_FILES));
        totalSizeText.setText(String.format("Total: %s", formatFileSize(totalSize)));
        updateProgressColors();
        // Hide scrollbars 
        javafx.scene.Node parent = selectedFilesList.getParent();
        while (parent != null && !(parent instanceof ScrollPane)) {
            parent = parent.getParent();
        }
        if (parent instanceof ScrollPane) {
            ((ScrollPane) parent).setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            ((ScrollPane) parent).setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        }
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
        fileItem.setTranslateX(-20);
        fileItem.setScaleX(0.95);
        fileItem.setScaleY(0.95);
        
        FadeTransition fadeTransition = new FadeTransition(Duration.millis(200), fileItem);
        fadeTransition.setFromValue(0);
        fadeTransition.setToValue(1);
        
        TranslateTransition slideTransition = new TranslateTransition(Duration.millis(200), fileItem);
        slideTransition.setFromX(-20);
        slideTransition.setToX(0);
        
        ScaleTransition scaleTransition = new ScaleTransition(Duration.millis(200), fileItem);
        scaleTransition.setFromX(0.95);
        scaleTransition.setFromY(0.95);
        scaleTransition.setToX(1.0);
        scaleTransition.setToY(1.0);
        
        ParallelTransition parallelTransition = new ParallelTransition(
            fadeTransition, slideTransition, scaleTransition
        );
        parallelTransition.play();
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
                selectedFiles.remove(file);
                totalSize -= file.length();
                updateFileDisplay();
                logger.info("Removed file: {}", file.getName());
            });
        }
    }
    
    private void animateFileItemRemoval(HBox fileItem, Runnable onComplete) {
        TranslateTransition slideTransition = new TranslateTransition(Duration.millis(200), fileItem);
        slideTransition.setFromX(0);
        slideTransition.setToX(20);
        
        FadeTransition fadeTransition = new FadeTransition(Duration.millis(200), fileItem);
        fadeTransition.setFromValue(1);
        fadeTransition.setToValue(0);
        
        ScaleTransition scaleTransition = new ScaleTransition(Duration.millis(200), fileItem);
        scaleTransition.setFromX(1.0);
        scaleTransition.setFromY(1.0);
        scaleTransition.setToX(0.8);
        scaleTransition.setToY(0.8);
        
        ParallelTransition parallelTransition = new ParallelTransition(
            slideTransition, fadeTransition, scaleTransition
        );
        parallelTransition.setOnFinished(e -> onComplete.run());
        parallelTransition.play();
    }
    
    @FXML
    public void clearAllFiles() {
        if (selectedFiles.isEmpty()) return;
        
        animateButton(clearAllBtn);
        
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Clear All Files");
        alert.setHeaderText("Remove all selected files?");
        alert.setContentText("This will remove all " + selectedFiles.size() + " selected files from the list.");
        
        alert.getDialogPane().getStyleClass().add("dialog-pane");
        
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
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
        });
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
    
    @FXML
    public void startDirectTransfer() {
        if (selectedFiles.isEmpty()) {
            showAlert("No Files Selected", "Please select files to transfer.", "");
            return;
        }
        
        animateButton(directTransferBtn);
        
        logger.info("Starting direct transfer with {} files", selectedFiles.size());
        
        showTransferProgress("Direct Transfer", "Preparing files for transfer...");
        
        // TODO: Implement direct transfer logic
        // For now, simulate transfer progress
        simulateTransferProgress();
    }
    
    @FXML
    public void generateSecureLink() {
        if (selectedFiles.isEmpty()) {
            showAlert("No Files Selected", "Please select files to generate link.", "");
            return;
        }
        
        animateButton(generateLinkBtn);
        
        logger.info("Generating secure link for {} files", selectedFiles.size());
        
        // Show progress dialog
        showTransferProgress("Generate Secure Link", "Uploading files and generating secure link...");
        
        // TODO: Implement secure link generation
        // For now, simulate link generation progress
        simulateTransferProgress();
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
}

