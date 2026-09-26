package com.sadat.pchardware;

import javafx.fxml.FXML;
import javafx.scene.control.Button;

import java.util.List;
import java.util.function.Consumer;

public class SidebarController {
    @FXML private Button allButton;
    @FXML private Button cpuButton;
    @FXML private Button gpuButton;
    @FXML private Button motherboardButton;
    @FXML private Button ramButton;
    @FXML private Button storageButton;
    @FXML private Button psuButton;
    private Consumer<String> onCategorySelected = category -> {};
    private Button activeButton;

    public void setOnCategorySelected(Consumer<String> action) {
        onCategorySelected = action;
        select("All", allButton);
    }

    @FXML private void showAll() { select("All", allButton); }
    @FXML private void showCpu() { select("CPU", cpuButton); }
    @FXML private void showGpu() { select("GPU", gpuButton); }
    @FXML private void showMotherboard() { select("Motherboard", motherboardButton); }
    @FXML private void showRam() { select("RAM", ramButton); }
    @FXML private void showStorage() { select("Storage", storageButton); }
    @FXML private void showPsu() { select("PSU", psuButton); }

    private void select(String category, Button button) {
        if (activeButton != null) activeButton.getStyleClass().remove("category-selected");
        activeButton = button;
        if (activeButton != null && !activeButton.getStyleClass().contains("category-selected")) {
            activeButton.getStyleClass().add("category-selected");
        }
        onCategorySelected.accept(category);
    }
}
