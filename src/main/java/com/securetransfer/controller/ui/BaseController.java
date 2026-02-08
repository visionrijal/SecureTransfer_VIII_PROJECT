package com.securetransfer.controller.ui;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

public abstract class BaseController {
    protected static final Logger logger = LoggerFactory.getLogger(BaseController.class);
    protected ApplicationContext springContext;

    public void setSpringContext(ApplicationContext context) {
        this.springContext = context;
    }

    protected FXMLLoader createFxmlLoader(String fxmlPath) {
        logger.debug("Creating FXML loader for path: {}", fxmlPath);
        FXMLLoader loader = new FXMLLoader();
        loader.setLocation(getClass().getResource(fxmlPath));
        loader.setControllerFactory(springContext::getBean);
        return loader;
    }

    protected void loadFXML(String fxmlPath) {
        try {
            logger.debug("Loading FXML: {}", fxmlPath);
            FXMLLoader loader = createFxmlLoader(fxmlPath);
            Parent root = loader.load();

            Object controller = loader.getController();
            if (controller instanceof BaseController) {
                ((BaseController) controller).setSpringContext(springContext);
            }

            Stage stage = getCurrentStage();
            if (stage != null) {
                Scene scene = new Scene(root);

                // CSS is now handled globally and via FXML imports

                stage.setScene(scene);
                stage.show();
            }
        } catch (IOException e) {
            logger.error("Error loading FXML: {}", fxmlPath, e);
        }
    }

    protected Stage getCurrentStage() {
        // This is a simplified version - in a real app you'd track the current stage
        return null; // Will be overridden by controllers that need it
    }

    // Default implementations for navigation methods
    // Controllers can override these if they need custom navigation behavior
    protected void loadLoginScreen() {
        loadFXML("/fxml/login.fxml");
    }

    protected void loadRegistrationScreen() {
        loadFXML("/fxml/registration.fxml");
    }
}