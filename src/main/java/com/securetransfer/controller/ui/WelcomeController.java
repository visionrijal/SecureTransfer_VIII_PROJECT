package com.securetransfer.controller.ui;

import javafx.fxml.FXML;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;

@Controller
public class WelcomeController extends BaseController {
    private static final Logger logger = LoggerFactory.getLogger(WelcomeController.class);

    private MainController mainController;

    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }

    @FXML
    public void showSendFiles() {
        if (mainController != null) {
            mainController.showSendFiles();
        }
    }

    @FXML
    public void showReceiveFiles() {
        if (mainController != null) {
            mainController.showReceiveFiles();
        }
    }
}
