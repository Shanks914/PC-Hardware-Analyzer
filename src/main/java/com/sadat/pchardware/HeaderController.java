package com.sadat.pchardware;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;

import java.util.function.Consumer;

public class HeaderController {
    @FXML private TextField searchField;
    @FXML private Button syncButton;
    private Runnable onSync = () -> {};
    private Consumer<String> onSearch = query -> {};

    @FXML
    private void initialize() {
        searchField.textProperty().addListener((observable, oldValue, newValue) -> onSearch.accept(newValue));
    }

    public void setActions(Runnable onSync, Consumer<String> onSearch) {
        this.onSync = onSync;
        this.onSearch = onSearch;
        syncButton.setOnAction(event -> this.onSync.run());
    }
}
