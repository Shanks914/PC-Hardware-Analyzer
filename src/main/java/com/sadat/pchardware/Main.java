package com.sadat.pchardware;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class Main extends Application {
    private MainController controller;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(Main.class.getResource("/com/sadat/pchardware/main-view.fxml"));
        Scene scene = new Scene(loader.load(), 1280, 800);
        controller = loader.getController();

        stage.setTitle("PC Hardware Analyzer");
        stage.setMinWidth(900);
        stage.setMinHeight(620);
        stage.setScene(scene);
        stage.show();
        controller.onViewReady();
    }

    @Override
    public void stop() {
        if (controller != null) controller.close();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
