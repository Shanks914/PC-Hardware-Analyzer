package com.sadat.pchardware;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.StackPane;
import javafx.scene.Node;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.function.Predicate;

public class MainController implements AutoCloseable {
    @FXML private HeaderController headerController;
    @FXML private BuilderController builderController;
    @FXML private BuildPanelController buildPanelController;
    @FXML private StackPane pageHost;
    @FXML private Node builderScroll;
    @FXML private ProcessorPageController processorPageController;
    @FXML private MotherboardPageController motherboardPageController;
    @FXML private RamPageController ramPageController;

    private final CatalogService catalogService = new CatalogService();
    private final BuildRepository buildRepository = new SqliteBuildRepository();
    private final List<Part> sampleParts = List.of(
            new Part("GPU", "GeForce RTX 4060", "8GB · PCIe 4.0", 47000),
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
        headerController.setActions(this::loadLocalCatalog, this::searchChanged);
        builderController.setOnChoose(this::choosePart);
        builderController.setOnRemove(this::removePart);
        buildPanelController.setActions(
                this::saveBuild,
                this::loadSavedBuild,
                this::deleteSavedBuild,
                this::newBuild
        );
        processorPageController.setActions(
                this::showBuilderPage,
                part -> {
                    build.put("processor", part);
                    refreshBuild();
                    showBuilderPage();
                }
        );
        motherboardPageController.setActions(
                this::showBuilderPage,
                part -> {
                    build.put("motherboard", part);
                    refreshBuild();
                    showBuilderPage();
                }
        );
        ramPageController.setActions(
                this::showBuilderPage,
                part -> {
                    build.put("memory", part);
                    refreshBuild();
                    showBuilderPage();
                }
        );
        processorPageController.setParts(parts);
        motherboardPageController.setParts(parts);
        motherboardPageController.setSelectedProcessor(build.get("processor"));
        ramPageController.setParts(parts);
        ramPageController.setSelectedMotherboard(build.get("motherboard"));
        refreshBuild();
    }

    public void onViewReady() {
        loadLocalCatalog();
    }

    private void searchChanged(String query) {
        searchText = query.toLowerCase().trim();
        builderController.setSearchText(searchText);
        if (processorPageController != null) processorPageController.setSearchQuery(query);
        if (motherboardPageController != null) motherboardPageController.setSearchQuery(query);
        if (ramPageController != null) ramPageController.setSearchQuery(query);
    }

    private void refreshBuild() {
        String label = activeBuildName == null ? "Unsaved build" : "Saved: " + activeBuildName;
        builderController.showSelections(build);
        buildPanelController.showBuild(List.copyOf(build.values()), label);
        buildPanelController.showCompatibility(BuildCompatibility.check(build));
    }

    private void loadLocalCatalog() {
        builderController.setCatalogStatus("Loading the project’s local JSON catalogs...");
        catalogService.loadLocalParts().whenComplete((loadedParts, error) -> Platform.runLater(() -> {
            if (error != null) {
                builderController.setCatalogStatus("Could not read a local hardware JSON file. Other sample components remain available.");
            } else if (loadedParts.isEmpty()) {
                builderController.setCatalogStatus("Local JSON files contain no valid components.");
            } else {
                List<Part> combined = new ArrayList<>(sampleParts);
                combined.addAll(loadedParts);
                parts = List.copyOf(combined);
                processorPageController.setParts(parts);
                motherboardPageController.setParts(parts);
                ramPageController.setParts(parts);
                ramPageController.setSelectedMotherboard(build.get("motherboard"));
                long cpuCount = loadedParts.stream().filter(part -> part.category().equalsIgnoreCase("CPU")).count();
                long boardCount = loadedParts.stream().filter(part -> part.category().equalsIgnoreCase("Motherboard")).count();
                long ramCount = loadedParts.stream().filter(part -> part.category().equalsIgnoreCase("RAM")).count();
                builderController.setCatalogStatus("Loaded " + cpuCount + " processors, " + boardCount
                        + " motherboards, and " + ramCount + " RAM kits from local JSON. Other slots use sample data.");
            }
        }));
    }

    private void choosePart(String slotKey, String category) {
        if (slotKey.equals("processor")) {
            processorPageController.setParts(parts);
            processorPageController.setSelected(build.get("processor"));
            showProcessorPage();
            return;
        }
        if (slotKey.equals("motherboard")) {
            motherboardPageController.setParts(parts);
            motherboardPageController.setSelectedProcessor(build.get("processor"));
            motherboardPageController.setSelectedMotherboard(build.get("motherboard"));
            showMotherboardPage();
            return;
        }
        if (slotKey.equals("memory")) {
            ramPageController.setParts(parts);
            ramPageController.setSelectedMotherboard(build.get("motherboard"));
            ramPageController.setSelectedMemory(build.get("memory"));
            showRamPage();
            return;
        }
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

    private void showProcessorPage() {
        builderScroll.setVisible(false);
        builderScroll.setManaged(false);
        motherboardPageController.getView().setVisible(false);
        motherboardPageController.getView().setManaged(false);
        ramPageController.getView().setVisible(false);
        ramPageController.getView().setManaged(false);
        processorPageController.getView().setVisible(true);
        processorPageController.getView().setManaged(true);
    }

    private void showMotherboardPage() {
        builderScroll.setVisible(false);
        builderScroll.setManaged(false);
        processorPageController.getView().setVisible(false);
        processorPageController.getView().setManaged(false);
        ramPageController.getView().setVisible(false);
        ramPageController.getView().setManaged(false);
        motherboardPageController.getView().setVisible(true);
        motherboardPageController.getView().setManaged(true);
    }

    private void showRamPage() {
        builderScroll.setVisible(false);
        builderScroll.setManaged(false);
        processorPageController.getView().setVisible(false);
        processorPageController.getView().setManaged(false);
        motherboardPageController.getView().setVisible(false);
        motherboardPageController.getView().setManaged(false);
        ramPageController.getView().setVisible(true);
        ramPageController.getView().setManaged(true);
    }

    private void showBuilderPage() {
        processorPageController.getView().setVisible(false);
        processorPageController.getView().setManaged(false);
        motherboardPageController.getView().setVisible(false);
        motherboardPageController.getView().setManaged(false);
        ramPageController.getView().setVisible(false);
        ramPageController.getView().setManaged(false);
        builderScroll.setVisible(true);
        builderScroll.setManaged(true);
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
