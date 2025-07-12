package com.securetransfer;

import com.securetransfer.controller.ui.BaseController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.net.URL;

public class JavaFXApplication extends Application {

    private ConfigurableApplicationContext springContext;
    private Parent rootNode;

    public static void main(String[] args) {
        // Set ICE port range globally for all ICE agents before anything else
        System.setProperty("org.ice4j.ice.MIN_PORT", "5000");
        System.setProperty("org.ice4j.ice.MAX_PORT", "5100");
        System.setProperty("org.ice4j.ice.harvest.PREFERRED_PORT", "5000");
        launch(args);
    }

    @Override
    public void init() {
        springContext = SpringApplication.run(SecureTransferApplication.class);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        try {
            URL fxmlUrl = getClass().getClassLoader().getResource("fxml/login.fxml");
            if (fxmlUrl == null) {
                throw new IOException("Cannot find fxml/login.fxml");
            }

            FXMLLoader fxmlLoader = new FXMLLoader(fxmlUrl);
            fxmlLoader.setControllerFactory(springContext::getBean);
            rootNode = fxmlLoader.load();
            
            Object controller = fxmlLoader.getController();
            if (controller instanceof BaseController) {
                ((BaseController) controller).setSpringContext(springContext);
            }
            
            Scene scene = new Scene(rootNode);
            
            URL cssUrl = getClass().getClassLoader().getResource("styles/global.css");
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            }
            
            primaryStage.setTitle("Secure Transfer");
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(800);
            primaryStage.setMinHeight(600);
            primaryStage.show();
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    @Override
    public void stop() {
        springContext.close();
    }
} 