package com.sadat.pchardware;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.IOException;

public class Main extends Application {
    private MainController controller;

    @Override
    public void start(Stage stage) throws IOException {
        StartupDiagnostics.mark("JavaFX start() entered");
        FXMLLoader loader = new FXMLLoader(Main.class.getResource("/com/sadat/pchardware/main-view.fxml"));
        Scene scene = new Scene(loader.load(), 1280, 800);
        StartupDiagnostics.mark("Main FXML loaded successfully");
        controller = loader.getController();

        stage.setTitle("PC Hardware Analyzer");
        var icon = Main.class.getResource("/icons/pc-hardware-analyzer.png");
        if (icon != null) stage.getIcons().add(new Image(icon.toExternalForm()));
        stage.setMinWidth(900);
        stage.setMinHeight(620);
        stage.setScene(scene);
        stage.show();
        StartupDiagnostics.mark("Main window shown");
        controller.onViewReady();
        StartupDiagnostics.mark("Main controller initialized");
    }

    @Override
    public void stop() {
        StartupDiagnostics.mark("JavaFX stop() entered");
        if (controller != null) controller.close();
    }

    public static void main(String[] args) {
        StartupDiagnostics.initialize();
        StartupDiagnostics.mark("Calling JavaFX Application.launch()");
        launch(args);
        StartupDiagnostics.mark("JavaFX Application.launch() returned");
    }
}
