package com.sadat.pchardware;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Alert;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class Main extends Application {
    private final ObservableList<Part> parts = FXCollections.observableArrayList(
            new Part("CPU", "AMD Ryzen 5 5600", "6 cores · AM4 · 65W", 18500),
            new Part("GPU", "GeForce RTX 4060", "8GB · PCIe 4.0", 47000),
            new Part("Motherboard", "MSI B550M PRO-VDH", "AM4 · DDR4 · mATX", 14500),
            new Part("RAM", "Corsair Vengeance 16GB", "2 × 8GB · DDR4 · 3200MHz", 5200),
            new Part("Storage", "WD Blue SN580 1TB", "NVMe M.2 · PCIe 4.0", 8500),
            new Part("PSU", "Cooler Master MWE 650", "650W · 80+ Bronze", 7800)
    );

    private final ObservableList<Part> build = FXCollections.observableArrayList();
    private final VBox productList = new VBox(12);
    private final VBox buildList = new VBox(10);
    private final Label totalLabel = new Label("৳0");
    private final Label buildCountLabel = new Label("0 parts selected");
    private final Label catalogStatus = new Label("Showing sample parts until catalog sync completes");
    private final CatalogService catalogService = new CatalogService();
    private final BuildRepository buildRepository = new SqliteBuildRepository();
    private final Label activeBuildLabel = new Label("Unsaved build");
    private Long activeBuildId;
    private String activeBuildName;
    private String activeCategory = "All";
    private String searchText = "";

    @Override
    public void start(Stage stage) {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #0b0e14;");

        root.setTop(createHeader());

        HBox workspace = new HBox(20);
        workspace.setPadding(new Insets(22));
        workspace.getChildren().addAll(
                createSidebar(),
                createCatalog(),
                createBuildPanel()
        );
        HBox.setHgrow(workspace.getChildren().get(1), Priority.ALWAYS);

        ScrollPane page = new ScrollPane(workspace);
        page.setFitToWidth(true);
        page.setStyle("-fx-background: #0b0e14; -fx-background-color: #0b0e14;");
        root.setCenter(page);

        Scene scene = new Scene(root, 1280, 800);
        stage.setTitle("PC Hardware Analyzer");
        stage.setMinWidth(900);
        stage.setMinHeight(620);
        stage.setScene(scene);
        stage.show();

        refreshProducts();
        loadCatalog();
        stage.setOnCloseRequest(event -> catalogService.close());
    }

    private HBox createHeader() {
        Label brand = new Label("PC BUILDER");
        brand.setStyle("-fx-text-fill: #f4f6fb; -fx-font-size: 20px; -fx-font-weight: bold;");

        Label subtitle = new Label("HARDWARE ANALYZER  /  BANGLADESH");
        subtitle.setStyle("-fx-text-fill: #8993a4; -fx-font-size: 11px;");

        VBox branding = new VBox(4, brand, subtitle);

        TextField search = new TextField();
        search.setPromptText("Search processors, graphics cards...");
        search.setPrefWidth(330);
        search.setStyle(fieldStyle());
        search.textProperty().addListener((obs, oldValue, newValue) -> {
            searchText = newValue.toLowerCase();
            refreshProducts();
        });

        Label currency = new Label("BDT  ৳");
        currency.setStyle("-fx-text-fill: #cbd3df; -fx-font-weight: bold;");

        Button syncButton = new Button("Sync catalog");
        syncButton.setStyle(buttonStyle(true));
        syncButton.setOnAction(event -> loadCatalog());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(24, branding, spacer, search, syncButton, currency);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(18, 26, 18, 26));
        header.setStyle("-fx-background-color: #111722; -fx-border-color: #202938; -fx-border-width: 0 0 1 0;");
        return header;
    }

    private VBox createSidebar() {
        Label title = new Label("COMPONENTS");
        title.setStyle("-fx-text-fill: #8993a4; -fx-font-size: 11px; -fx-font-weight: bold;");

        VBox sidebar = new VBox(8);
        sidebar.setPadding(new Insets(18));
        sidebar.setPrefWidth(190);
        sidebar.setMinWidth(165);
        sidebar.setStyle(cardStyle());

        sidebar.getChildren().add(title);
        for (String category : List.of("All", "CPU", "GPU", "Motherboard", "RAM", "Storage", "PSU")) {
            Button button = new Button(category);
            button.setMaxWidth(Double.MAX_VALUE);
            button.setAlignment(Pos.CENTER_LEFT);
            button.setStyle(buttonStyle(category.equals(activeCategory)));
            button.setOnAction(event -> {
                activeCategory = category;
                for (var node : sidebar.getChildren()) {
                    if (node instanceof Button b) {
                        b.setStyle(buttonStyle(b.getText().equals(activeCategory)));
                    }
                }
                refreshProducts();
            });
            sidebar.getChildren().add(button);
        }
        return sidebar;
    }

    private VBox createCatalog() {
        Label eyebrow = new Label("BUILD YOUR NEXT PC");
        eyebrow.setStyle("-fx-text-fill: #69d49b; -fx-font-size: 11px; -fx-font-weight: bold;");

        Label heading = new Label("Choose your components");
        heading.setStyle("-fx-text-fill: #f4f6fb; -fx-font-size: 27px; -fx-font-weight: bold;");

        Label description = new Label("Compare parts and keep an eye on your total build cost.");
        description.setStyle("-fx-text-fill: #8993a4; -fx-font-size: 13px;");

        catalogStatus.setStyle("-fx-text-fill: #8993a4; -fx-font-size: 11px;");

        productList.setPadding(new Insets(4, 0, 0, 0));

        VBox catalog = new VBox(10, eyebrow, heading, description, catalogStatus, productList);
        catalog.setPadding(new Insets(8, 2, 12, 2));
        catalog.setMinWidth(360);
        return catalog;
    }

    private VBox createBuildPanel() {
        Label heading = new Label("Your build");
        heading.setStyle("-fx-text-fill: #f4f6fb; -fx-font-size: 19px; -fx-font-weight: bold;");
        activeBuildLabel.setStyle("-fx-text-fill: #8993a4; -fx-font-size: 11px;");

        Label currencyCaption = new Label("ESTIMATED TOTAL");
        currencyCaption.setStyle("-fx-text-fill: #8993a4; -fx-font-size: 10px; -fx-font-weight: bold;");
        totalLabel.setStyle("-fx-text-fill: #69d49b; -fx-font-size: 26px; -fx-font-weight: bold;");
        buildCountLabel.setStyle("-fx-text-fill: #aeb8c7; -fx-font-size: 12px;");

        Button clearButton = new Button("Clear build");
        clearButton.setStyle(buttonStyle(false));
        clearButton.setOnAction(event -> {
            build.clear();
            activeBuildId = null;
            activeBuildName = null;
            activeBuildLabel.setText("Unsaved build");
            refreshBuild();
        });

        Button saveButton = new Button("Save / Update build");
        saveButton.setMaxWidth(Double.MAX_VALUE);
        saveButton.setStyle(buttonStyle(true));
        saveButton.setOnAction(event -> saveBuild());

        Button loadButton = new Button("Load saved build");
        loadButton.setMaxWidth(Double.MAX_VALUE);
        loadButton.setStyle(buttonStyle(false));
        loadButton.setOnAction(event -> loadSavedBuild());

        Button deleteButton = new Button("Delete saved build");
        deleteButton.setMaxWidth(Double.MAX_VALUE);
        deleteButton.setStyle(buttonStyle(false));
        deleteButton.setOnAction(event -> deleteSavedBuild());

        buildList.setPadding(new Insets(8, 0, 8, 0));

        VBox panel = new VBox(14, heading, activeBuildLabel, currencyCaption, totalLabel,
                buildCountLabel, saveButton, loadButton, deleteButton, clearButton, buildList);
        panel.setPadding(new Insets(20));
        panel.setPrefWidth(265);
        panel.setMinWidth(230);
        panel.setStyle(cardStyle());
        return panel;
    }

    private void refreshProducts() {
        productList.getChildren().clear();

        parts.stream()
                .filter(part -> activeCategory.equals("All") || part.category().equals(activeCategory))
                .filter(part -> searchText.isBlank()
                        || part.name().toLowerCase().contains(searchText)
                        || part.category().toLowerCase().contains(searchText))
                .forEach(part -> productList.getChildren().add(createPartCard(part)));
    }

    private HBox createPartCard(Part part) {
        Label category = new Label(part.category().toUpperCase());
        category.setStyle("-fx-text-fill: #69d49b; -fx-font-size: 10px; -fx-font-weight: bold;");

        Label name = new Label(part.name());
        name.setStyle("-fx-text-fill: #f4f6fb; -fx-font-size: 15px; -fx-font-weight: bold;");

        Label specs = new Label(part.specs());
        specs.setStyle("-fx-text-fill: #8993a4; -fx-font-size: 12px;");

        Label price = new Label(String.format("৳%,.0f", part.price()));
        price.setStyle("-fx-text-fill: #f4f6fb; -fx-font-size: 15px; -fx-font-weight: bold;");

        Button add = new Button("Add");
        add.setStyle(buttonStyle(true));
        add.setOnAction(event -> {
            build.add(part);
            refreshBuild();
        });

        VBox details = new VBox(6, category, name, specs);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        VBox priceAndButton = new VBox(8, price, add);
        priceAndButton.setAlignment(Pos.CENTER_RIGHT);

        HBox card = new HBox(14, details, spacer, priceAndButton);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(16));
        card.setStyle(cardStyle());
        return card;
    }

    private void refreshBuild() {
        buildList.getChildren().clear();
        for (Part part : build) {
            Label item = new Label(part.category() + "  ·  " + part.name()
                    + "\n৳" + String.format("%,.0f", part.price()));
            item.setWrapText(true);
            item.setMaxWidth(Double.MAX_VALUE);
            item.setStyle("-fx-text-fill: #cbd3df; -fx-font-size: 12px; -fx-padding: 10; "
                    + "-fx-background-color: #171f2c; -fx-background-radius: 8;");
            buildList.getChildren().add(item);
        }

        double total = build.stream().mapToDouble(Part::price).sum();
        totalLabel.setText(String.format("৳%,.0f", total));
        buildCountLabel.setText(build.size() + (build.size() == 1 ? " part selected" : " parts selected"));
    }

    private void loadCatalog() {
        catalogStatus.setText("Fetching catalog from GitHub...");

        catalogService.fetchParts().whenComplete((loadedParts, error) ->
                Platform.runLater(() -> {
                    if (error != null) {
                        catalogStatus.setText("Sync failed; sample parts are still shown.");
                    } else if (loadedParts.isEmpty()) {
                        catalogStatus.setText("Catalog loaded, but it contains no valid parts.");
                    } else {
                        parts.setAll(loadedParts);
                        refreshProducts();
                        catalogStatus.setText("Synced " + loadedParts.size() + " parts from GitHub.");
                    }
                })
        );
    }

    private void saveBuild() {
        if (build.isEmpty()) {
            showMessage(Alert.AlertType.INFORMATION, "Empty build", "Add at least one part before saving.");
            return;
        }

        TextInputDialog dialog = new TextInputDialog(activeBuildName == null ? "My PC Build" : activeBuildName);
        dialog.setTitle("Save PC build");
        dialog.setHeaderText(activeBuildId == null ? "Create a saved build" : "Update this saved build");
        dialog.setContentText("Build name:");
        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty() || result.get().isBlank()) return;

        try {
            activeBuildName = result.get().trim();
            activeBuildId = buildRepository.save(activeBuildId, activeBuildName, List.copyOf(build));
            activeBuildLabel.setText("Saved: " + activeBuildName);
            showMessage(Alert.AlertType.INFORMATION, "Build saved", "Your build is stored in the SQLite database.");
        } catch (SQLException e) {
            showMessage(Alert.AlertType.ERROR, "Database error", e.getMessage());
        }
    }

    private void loadSavedBuild() {
        try {
            List<SavedBuild> savedBuilds = buildRepository.findAll();
            if (savedBuilds.isEmpty()) {
                showMessage(Alert.AlertType.INFORMATION, "No saved builds", "Save a build first.");
                return;
            }

            ChoiceDialog<SavedBuild> dialog = new ChoiceDialog<>(savedBuilds.get(0), savedBuilds);
            dialog.setTitle("Load PC build");
            dialog.setHeaderText("Choose a saved build to load");
            dialog.setContentText("Saved builds:");
            dialog.showAndWait().ifPresent(selected -> {
                try {
                    SavedBuild saved = buildRepository.findById(selected.id());
                    if (saved != null) {
                        activeBuildId = saved.id();
                        activeBuildName = saved.name();
                        activeBuildLabel.setText("Loaded: " + activeBuildName);
                        build.setAll(saved.parts());
                        refreshBuild();
                    }
                } catch (SQLException e) {
                    showMessage(Alert.AlertType.ERROR, "Database error", e.getMessage());
                }
            });
        } catch (SQLException e) {
            showMessage(Alert.AlertType.ERROR, "Database error", e.getMessage());
        }
    }

    private void deleteSavedBuild() {
        try {
            List<SavedBuild> savedBuilds = buildRepository.findAll();
            if (savedBuilds.isEmpty()) {
                showMessage(Alert.AlertType.INFORMATION, "No saved builds", "There are no builds to delete.");
                return;
            }

            ChoiceDialog<SavedBuild> dialog = new ChoiceDialog<>(savedBuilds.get(0), savedBuilds);
            dialog.setTitle("Delete PC build");
            dialog.setHeaderText("Choose a saved build to delete");
            dialog.setContentText("Saved builds:");
            dialog.showAndWait().ifPresent(selected -> {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                        "Delete '" + selected.name() + "'? This cannot be undone.");
                confirm.setHeaderText("Confirm deletion");
                if (confirm.showAndWait().filter(button -> button == javafx.scene.control.ButtonType.OK).isPresent()) {
                    try {
                        buildRepository.delete(selected.id());
                        if (selected.id() == (activeBuildId == null ? -1 : activeBuildId)) {
                            activeBuildId = null;
                            activeBuildName = null;
                            activeBuildLabel.setText("Unsaved build");
                        }
                        showMessage(Alert.AlertType.INFORMATION, "Build deleted", "The saved build was removed.");
                    } catch (SQLException e) {
                        showMessage(Alert.AlertType.ERROR, "Database error", e.getMessage());
                    }
                }
            });
        } catch (SQLException e) {
            showMessage(Alert.AlertType.ERROR, "Database error", e.getMessage());
        }
    }

    private void showMessage(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message == null ? "An unexpected error occurred." : message);
        alert.showAndWait();
    }

    private String cardStyle() {
        return "-fx-background-color: #111722; -fx-background-radius: 12; "
                + "-fx-border-color: #202938; -fx-border-radius: 12;";
    }

    private String buttonStyle(boolean primary) {
        return primary
                ? "-fx-background-color: #69d49b; -fx-text-fill: #0b1711; -fx-font-weight: bold; "
                + "-fx-background-radius: 7; -fx-cursor: hand;"
                : "-fx-background-color: #1a2230; -fx-text-fill: #cbd3df; "
                + "-fx-background-radius: 7; -fx-cursor: hand;";
    }

    private String fieldStyle() {
        return "-fx-background-color: #0b0e14; -fx-text-fill: #f4f6fb; "
                + "-fx-prompt-text-fill: #687386; -fx-background-radius: 8; "
                + "-fx-border-color: #293446; -fx-border-radius: 8;";
    }

    public static void main(String[] args) {
        launch(args);
    }
}
