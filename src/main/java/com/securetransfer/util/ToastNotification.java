package com.securetransfer.util;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.shape.SVGPath;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.stage.Window;
import java.net.URL;

public class ToastNotification {
    private static Popup currentPopup = null;

    public static void show(Stage ownerStage, String message, NotificationType type, Duration duration) {
        show(ownerStage, message, type, duration, 70);
    }

    public static void show(Stage ownerStage, String message, NotificationType type, Duration duration,
            double topMargin) {
        if (ownerStage == null) {
            // Fallback: get the focused window, or skip showing the notification
            Window window = Window.getWindows().stream().filter(Window::isFocused).findFirst().orElse(null);
            if (window instanceof Stage) {
                ownerStage = (Stage) window;
            } else {
                System.err.println("[ToastNotification] ownerStage is null, cannot show notification.");
                return;
            }
        }
        final Stage finalOwnerStage = ownerStage;
        Platform.runLater(() -> {
            if (currentPopup != null && currentPopup.isShowing()) {
                currentPopup.hide();
            }
            Popup popup = new Popup();
            currentPopup = popup;
            popup.setAutoFix(true);
            popup.setAutoHide(true);
            popup.setHideOnEscape(true);

            SVGPath icon = new SVGPath();
            icon.setContent(getIconSvg(type));
            icon.getStyleClass().add("toast-icon");
            icon.setScaleX(1.0);
            icon.setScaleY(1.0);

            Text text = new Text(message);
            text.getStyleClass().add("toast-text");
            text.setTextAlignment(TextAlignment.LEFT);
            text.setWrappingWidth(250);

            HBox content = new HBox(16, icon, text);
            content.setAlignment(Pos.CENTER_LEFT);

            StackPane pane = new StackPane(content);
            pane.setAlignment(Pos.CENTER_LEFT);
            pane.getStyleClass().addAll("toast-pane", type.styleClass);
            pane.setPrefWidth(320);
            pane.setPrefHeight(65);
            pane.setOpacity(0);

            // Set initial position for right-to-left animation
            pane.setTranslateX(100);

            URL cssResource = ToastNotification.class.getResource("/styles/global.css");
            if (cssResource != null) {
                pane.getStylesheets().add(cssResource.toExternalForm());
            }

            Scene scene = finalOwnerStage.getScene();
            double marginX = 20;
            double x = scene.getWindow().getX() + scene.getWidth() - pane.getPrefWidth() - marginX;
            double y = scene.getWindow().getY() + topMargin;

            popup.getContent().add(pane);
            popup.show(finalOwnerStage, x, y);

            // Slide in from right and fade in
            Timeline slideIn = new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(pane.opacityProperty(), 0),
                            new KeyValue(pane.translateXProperty(), 100)),
                    new KeyFrame(Duration.millis(300),
                            new KeyValue(pane.opacityProperty(), 1),
                            new KeyValue(pane.translateXProperty(), 0)));

            // Slide out to left and fade out
            Timeline slideOut = new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(pane.opacityProperty(), 1),
                            new KeyValue(pane.translateXProperty(), 0)),
                    new KeyFrame(Duration.millis(600), e -> popup.hide(),
                            new KeyValue(pane.opacityProperty(), 0),
                            new KeyValue(pane.translateXProperty(), -100)));

            slideIn.setOnFinished(e -> {
                Timeline delay = new Timeline(new KeyFrame(duration));
                delay.setOnFinished(event -> slideOut.play());
                delay.play();
            });

            slideIn.play();
        });
    }

    private static String getIconSvg(NotificationType type) {
        return switch (type) {
            case SUCCESS ->
                "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z"; // checkmark
                                                                                                                                         // circle
            case ERROR ->
                "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm5 13.59L15.59 17 12 13.41 8.41 17 7 15.59 10.59 12 7 8.41 8.41 7 12 10.59 15.59 7 17 8.41 13.41 12 17 15.59z"; // X
                                                                                                                                                                                                   // circle
            case WARNING -> "M1 21h22L12 2 1 21zm12-3h-2v-2h2v2zm0-4h-2v-4h2v4z"; // warning triangle
            case INFO ->
                "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z"; // info
                                                                                                                    // circle
        };
    }

    public enum NotificationType {
        SUCCESS("toast-success"),
        WARNING("toast-warning"),
        ERROR("toast-error"),
        INFO("toast-info");

        public final String styleClass;

        NotificationType(String styleClass) {
            this.styleClass = styleClass;
        }
    }
}