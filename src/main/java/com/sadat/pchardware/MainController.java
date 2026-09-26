package com.sadat.pchardware;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.TextInputDialog;

import java.sql.SQLException;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.function.Predicate;

public class MainController implements AutoCloseable {
    @FXML private HeaderController headerController;
    @FXML private BuilderController builderController;
    @FXML private BuildPanelController buildPanelController;

    private final CatalogService catalogService = new CatalogService();
    private final BuildRepository buildRepository = new SqliteBuildRepository();
    private final List<Part> sampleParts = List.of(
            new Part("CPU", "AMD Ryzen 5 5600", "6 cores · AM4 · 65W", 18500),
            new Part("GPU", "GeForce RTX 4060", "8GB · PCIe 4.0", 47000),
            new Part("Motherboard", "MSI B550M PRO-VDH", "AM4 · DDR4 · mATX", 14500),
            new Part("RAM", "Corsair Vengeance 16GB", "2 × 8GB · DDR4 · 3200MHz", 5200),
            new Part("Storage", "WD Blue SN580 1TB", "NVMe M.2 · PCIe 4.0", 8500),
            new Part("PSU", "Cooler Master MWE 650", "650W · 80+ Bronze", 7800)
    );

    private List<Part> parts = sampleParts;
    private final LinkedHashMap<String, Part> build = new LinkedHashMap<>();
    private String searchText = "";
    private Long activeBuildId;
    private String activeBuildName;

    @FXML
    private void initialize() {
        headerController.setActions(this::loadCatalog, this::searchChanged);
        builderController.setOnChoose(this::choosePart);
        builderController.setOnRemove(this::removePart);
        buildPanelController.setActions(
                this::saveBuild,
                this::loadSavedBuild,
                this::deleteSavedBuild,
                this::newBuild
        );
        refreshBuild();
    }

    public void onViewReady() {
        loadCatalog();
    }

    private void searchChanged(String query) {
        searchText = query.toLowerCase().trim();
        builderController.setSearchText(searchText);
    }

    private void refreshBuild() {
        String label = activeBuildName == null ? "Unsaved build" : "Saved: " + activeBuildName;
        builderController.showSelections(build);
        buildPanelController.showBuild(List.copyOf(build.values()), label);
    }

    private void loadCatalog() {
        builderController.setCatalogStatus("Fetching catalog from GitHub...");
        catalogService.fetchParts().whenComplete((loadedParts, error) -> Platform.runLater(() -> {
            if (error != null) {
                builderController.setCatalogStatus("Sync failed; sample parts are still available.");
            } else if (loadedParts.isEmpty()) {
                builderController.setCatalogStatus("Catalog loaded, but it contains no valid parts.");
            } else {
                parts = loadedParts;
                builderController.setCatalogStatus("Synced " + loadedParts.size() + " parts from GitHub.");
            }
        }));
    }

    private void choosePart(String slotKey, String category) {
        if (category == null) {
            showMessage(Alert.AlertType.INFORMATION, "Catalog category unavailable",
                    "This component type is not included in the current catalog yet.");
            return;
        }
        Predicate<Part> slotFilter = part -> true;
        if (slotKey.equals("storage-hdd")) {
            slotFilter = part -> part.specs().toLowerCase().contains("hdd");
        } else if (slotKey.equals("storage-ssd")) {
            slotFilter = part -> !part.specs().toLowerCase().contains("hdd");
        }
        Predicate<Part> finalFilter = slotFilter;
        List<Part> choices = parts.stream()
                .filter(part -> part.category().equalsIgnoreCase(category))
                .filter(finalFilter)
                .filter(part -> searchText.isBlank()
                        || part.name().toLowerCase().contains(searchText)
                        || part.specs().toLowerCase().contains(searchText))
                .toList();
        if (choices.isEmpty()) {
            showMessage(Alert.AlertType.INFORMATION, "No matching parts", "No catalog entries match this slot and search.");
            return;
        }
        ChoiceDialog<Part> dialog = new ChoiceDialog<>(choices.get(0), choices);
        dialog.setTitle("Choose " + category);
        dialog.setHeaderText("Select a component for your build");
        dialog.setContentText("Part:");
        dialog.showAndWait().ifPresent(part -> {
            build.put(slotKey, part);
            refreshBuild();
        });
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
            activeBuildId = buildRepository.save(activeBuildId, activeBuildName, List.copyOf(build.values()));
            refreshBuild();
            showMessage(Alert.AlertType.INFORMATION, "Build saved", "Your build is stored in SQLite.");
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
                        build.clear();
                        for (Part part : saved.parts()) {
                            build.put(slotFor(part), part);
                        }
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
                Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                        "Delete '" + selected.name() + "'? This cannot be undone.");
                confirmation.setHeaderText("Confirm deletion");
                if (confirmation.showAndWait().filter(button -> button == javafx.scene.control.ButtonType.OK).isPresent()) {
                    try {
                        buildRepository.delete(selected.id());
                        if (activeBuildId != null && activeBuildId == selected.id()) {
                            activeBuildId = null;
                            activeBuildName = null;
                            refreshBuild();
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

    private void newBuild() {
        build.clear();
        activeBuildId = null;
        activeBuildName = null;
        refreshBuild();
    }

    private void removePart(String slotKey) {
        if (build.remove(slotKey) != null) refreshBuild();
    }

    private String slotFor(Part part) {
        return switch (part.category().toLowerCase()) {
            case "cpu" -> "processor";
            case "motherboard" -> "motherboard";
            case "ram" -> "memory";
            case "gpu" -> "graphics";
            case "psu" -> "power";
            case "storage" -> part.specs().toLowerCase().contains("hdd") ? "storage-hdd" : "storage-ssd";
            default -> part.category().toLowerCase();
        };
    }

    private void showMessage(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message == null ? "An unexpected error occurred." : message);
        alert.showAndWait();
    }

    @Override
    public void close() {
        catalogService.close();
    }
}
