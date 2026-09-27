package com.sadat.pchardware;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.util.List;

public class BuildPanelController {
    @FXML private VBox buildList;
    @FXML private Label totalLabel;
    @FXML private Label buildCountLabel;
    @FXML private Label activeBuildLabel;
    @FXML private Label powerLabel;
    @FXML private Label compatibilityLabel;
    @FXML private Button saveButton;
    @FXML private Button loadButton;
    @FXML private Button compareBuildsButton;
    @FXML private Button deleteButton;
    @FXML private Button newButton;
    @FXML private Button downloadButton;

    private Runnable onSave = () -> {};
    private Runnable onLoad = () -> {};
    private Runnable onCompareBuilds = () -> {};
    private Runnable onDelete = () -> {};
    private Runnable onNew = () -> {};
    private Runnable onDownload = () -> {};

    public void setActions(Runnable save, Runnable load, Runnable compareBuilds, Runnable delete, Runnable newBuild, Runnable download) {
        onSave = save;
        onLoad = load;
        onCompareBuilds = compareBuilds;
        onDelete = delete;
        onNew = newBuild;
        onDownload = download;
        saveButton.setOnAction(event -> onSave.run());
        loadButton.setOnAction(event -> onLoad.run());
        compareBuildsButton.setOnAction(event -> onCompareBuilds.run());
        deleteButton.setOnAction(event -> onDelete.run());
        newButton.setOnAction(event -> onNew.run());
        downloadButton.setOnAction(event -> onDownload.run());
    }

    public void showBuild(List<Part> parts, String activeName) {
        activeBuildLabel.setText(activeName);
        buildList.getChildren().clear();
        for (int i = 0; i < parts.size(); i++) {
            Part part = parts.get(i);
            Label item = new Label(part.category() + " · " + part.name()
                    + "\n৳" + String.format("%,.0f", part.price()));
            item.setWrapText(true);
            item.getStyleClass().add("build-item");
            buildList.getChildren().add(item);
        }
        double total = parts.stream().mapToDouble(Part::price).sum();
        totalLabel.setText(String.format("৳%,.0f", total));
        buildCountLabel.setText(parts.size() + (parts.size() == 1 ? " part selected" : " parts selected"));
        powerLabel.setText(BuildComparisonMetrics.estimateBuildWatts(parts) + "W");
    }

    public void showCompatibility(BuildCompatibility.Result result) {
        compatibilityLabel.setText(result.message());
        compatibilityLabel.getStyleClass().removeAll("compatibility-neutral", "compatibility-ok", "compatibility-error", "compatibility-unknown");
        compatibilityLabel.getStyleClass().add(switch (result.state()) {
            case COMPATIBLE -> "compatibility-ok";
            case INCOMPATIBLE -> "compatibility-error";
            case UNKNOWN -> "compatibility-unknown";
            case NEUTRAL -> "compatibility-neutral";
        });
    }

}
