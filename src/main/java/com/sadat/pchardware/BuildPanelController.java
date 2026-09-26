package com.sadat.pchardware;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BuildPanelController {
    @FXML private VBox buildList;
    @FXML private Label totalLabel;
    @FXML private Label buildCountLabel;
    @FXML private Label activeBuildLabel;
    @FXML private Label powerLabel;
    @FXML private Button saveButton;
    @FXML private Button loadButton;
    @FXML private Button deleteButton;
    @FXML private Button newButton;

    private Runnable onSave = () -> {};
    private Runnable onLoad = () -> {};
    private Runnable onDelete = () -> {};
    private Runnable onNew = () -> {};

    public void setActions(Runnable save, Runnable load, Runnable delete, Runnable newBuild) {
        onSave = save;
        onLoad = load;
        onDelete = delete;
        onNew = newBuild;
        saveButton.setOnAction(event -> onSave.run());
        loadButton.setOnAction(event -> onLoad.run());
        deleteButton.setOnAction(event -> onDelete.run());
        newButton.setOnAction(event -> onNew.run());
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
        powerLabel.setText(estimateWattage(parts) + "W");
    }

    private int estimateWattage(List<Part> parts) {
        if (parts.isEmpty()) return 0;
        int watts = 50;
        Pattern pattern = Pattern.compile("(?i)\\b(\\d{2,3})\\s*W\\b");
        for (Part part : parts) {
            String category = part.category().toLowerCase();
            if (category.equals("cpu") || category.equals("gpu")) {
                Matcher matcher = pattern.matcher(part.specs());
                if (matcher.find()) {
                    watts += Integer.parseInt(matcher.group(1));
                } else {
                    watts += category.equals("cpu") ? 95 : 250;
                }
            } else if (category.equals("motherboard")) {
                watts += 40;
            } else if (category.equals("ram")) {
                watts += 10;
            } else if (category.equals("storage")) {
                watts += 10;
            }
        }
        return ((watts + 24) / 25) * 25;
    }
}
