package com.sadat.pchardware;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;

import java.util.List;
import java.util.function.Consumer;

/** Shared behavior for the dedicated load-build and delete-build pages. */
public class SavedBuildPageController {
    @FXML private Node root;
    @FXML private Label titleLabel;
    @FXML private Label descriptionLabel;
    @FXML private Label statusLabel;
    @FXML private ListView<SavedBuild> buildsList;
    @FXML private Button actionButton;

    private Runnable backAction = () -> { };
    private Consumer<SavedBuild> selectedAction = ignored -> { };

    public void configure(String title, String description, String actionText,
                          Runnable backAction, Consumer<SavedBuild> selectedAction) {
        titleLabel.setText(title);
        descriptionLabel.setText(description);
        actionButton.setText(actionText);
        this.backAction = backAction;
        this.selectedAction = selectedAction;
        actionButton.setOnAction(event -> {
            SavedBuild selected = buildsList.getSelectionModel().getSelectedItem();
            if (selected == null) {
                statusLabel.setText("Select a saved build first.");
                return;
            }
            this.selectedAction.accept(selected);
        });
    }

    @FXML private void goBack() { backAction.run(); }

    public Node getView() { return root; }

    public void setBuilds(List<SavedBuild> builds) {
        buildsList.getItems().setAll(builds);
        if (builds.isEmpty()) {
            statusLabel.setText("No saved builds yet. Create and save a build from the builder page.");
        } else {
            buildsList.getSelectionModel().selectFirst();
            statusLabel.setText(builds.size() + " saved build(s)");
        }
    }

    public void setStatus(String message) { statusLabel.setText(message); }
}
