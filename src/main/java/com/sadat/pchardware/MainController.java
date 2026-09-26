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
    @FXML private BuilderController builderController;
    @FXML private BuildPanelController buildPanelController;
    @FXML private StackPane pageHost;
    @FXML private Node builderScroll;
    @FXML private ProcessorPageController processorPageController;
    @FXML private MotherboardPageController motherboardPageController;
    @FXML private RamPageController ramPageController;
    @FXML private SsdPageController ssdPageController;
    @FXML private HddPageController hddPageController;
    @FXML private GpuPageController gpuPageController;
    @FXML private PsuPageController psuPageController;
    @FXML private CategoryChooserController categoryChooserController;
    @FXML private SavedBuildPageController loadBuildPageController;
    @FXML private SavedBuildPageController deleteBuildPageController;

    private final CatalogService catalogService = new CatalogService();
    private final BuildRepository buildRepository = new SqliteBuildRepository();
    private final List<Part> sampleParts = List.of();

    private List<Part> parts = sampleParts;
    private final LinkedHashMap<String, Part> build = new LinkedHashMap<>();
    private String searchText = "";
    private Long activeBuildId;
    private String activeBuildName;

    @FXML
    private void initialize() {
        builderController.setOnChoose(this::choosePart);
        builderController.setOnRemove(this::removePart);
        buildPanelController.setActions(
                this::saveBuild,
                this::showLoadBuildPage,
                this::showDeleteBuildPage,
                this::newBuild
        );
        loadBuildPageController.configure("Load a saved build",
                "Choose a saved build to restore it in the PC builder.", "Load selected build",
                this::showBuilderPage, this::loadSelectedBuild);
        deleteBuildPageController.configure("Delete a saved build",
                "Select a build to permanently remove it from SQLite.", "Delete selected build",
                this::showBuilderPage, this::deleteSelectedBuild);
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
                    ssdPageController.setSelectedMotherboard(part);
                    hddPageController.setSelectedMotherboard(part);
                    gpuPageController.setSelectedMotherboard(part);
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
        ssdPageController.setActions(this::showBuilderPage, part -> {
            build.put("storage-ssd", part);
            refreshBuild();
            showBuilderPage();
        });
        hddPageController.setActions(this::showBuilderPage, part -> {
            build.put("storage-hdd", part);
            refreshBuild();
            showBuilderPage();
        });
        gpuPageController.setActions(this::showBuilderPage, part -> {
            build.put("graphics", part);
            refreshBuild();
            showBuilderPage();
        });
        psuPageController.setActions(this::showBuilderPage, part -> {
            build.put("power", part);
            gpuPageController.setSelectedPsu(part);
            refreshBuild();
            showBuilderPage();
        });
        categoryChooserController.setActions(this::showBuilderPage, part -> {
            build.put(categoryChooserController.getSelectedSlot(), part);
            refreshBuild();
            showBuilderPage();
        });
        processorPageController.setParts(parts);
        motherboardPageController.setParts(parts);
        motherboardPageController.setSelectedProcessor(build.get("processor"));
        ramPageController.setParts(parts);
        ramPageController.setSelectedMotherboard(build.get("motherboard"));
        ssdPageController.setParts(parts);
        ssdPageController.setSelectedMotherboard(build.get("motherboard"));
        hddPageController.setParts(parts);
        hddPageController.setSelectedMotherboard(build.get("motherboard"));
        hddPageController.setSelectedSsd(build.get("storage-ssd"));
        gpuPageController.setParts(parts);
        gpuPageController.setSelectedMotherboard(build.get("motherboard"));
        gpuPageController.setSelectedPsu(build.get("power"));
        psuPageController.setParts(parts);
        psuPageController.setSelectedGpu(build.get("graphics"));
        categoryChooserController.setParts(parts);
        refreshBuild();
    }

    public void onViewReady() {
        loadLocalCatalog();
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
                builderController.setCatalogStatus("Could not read a local catalog JSON file. Check the project resources and reload.");
            } else if (loadedParts.isEmpty()) {
                builderController.setCatalogStatus("Local JSON files contain no valid components.");
            } else {
                List<Part> combined = new ArrayList<>(sampleParts);
                combined.addAll(loadedParts);
                parts = List.copyOf(combined);
                processorPageController.setParts(parts);
                motherboardPageController.setParts(parts);
                ramPageController.setParts(parts);
                ssdPageController.setParts(parts);
                hddPageController.setParts(parts);
                gpuPageController.setParts(parts);
                psuPageController.setParts(parts);
                categoryChooserController.setParts(parts);
                ramPageController.setSelectedMotherboard(build.get("motherboard"));
                ssdPageController.setSelectedMotherboard(build.get("motherboard"));
                hddPageController.setSelectedMotherboard(build.get("motherboard"));
                gpuPageController.setSelectedMotherboard(build.get("motherboard"));
                gpuPageController.setSelectedPsu(build.get("power"));
                psuPageController.setSelectedGpu(build.get("graphics"));
                hddPageController.setSelectedSsd(build.get("storage-ssd"));
                long cpuCount = loadedParts.stream().filter(part -> part.category().equalsIgnoreCase("CPU")).count();
                long boardCount = loadedParts.stream().filter(part -> part.category().equalsIgnoreCase("Motherboard")).count();
                long ramCount = loadedParts.stream().filter(part -> part.category().equalsIgnoreCase("RAM")).count();
                long ssdCount = loadedParts.stream().filter(part -> part.category().equalsIgnoreCase("SSD")).count();
                long hddCount = loadedParts.stream().filter(part -> part.category().equalsIgnoreCase("HDD")).count();
                long gpuCount = loadedParts.stream().filter(part -> part.category().equalsIgnoreCase("GPU")).count();
                long psuCount = loadedParts.stream().filter(part -> part.category().equalsIgnoreCase("PSU")).count();
                long extrasCount = loadedParts.stream().filter(part -> List.of("CPU Cooler", "Casing", "Casing Fan", "Monitor", "Keyboard", "Mouse", "UPS").stream().anyMatch(category -> part.category().equalsIgnoreCase(category))).count();
                builderController.setCatalogStatus("Loaded " + cpuCount + " processors, " + boardCount
                        + " motherboards, " + ramCount + " RAM kits, " + ssdCount + " SSDs, " + hddCount + " HDDs, " + gpuCount + " graphics cards, " + psuCount + " power supplies, and " + extrasCount + " cooling/case/accessory products from local JSON.");
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
        if (slotKey.equals("storage-ssd")) {
            ssdPageController.setParts(parts);
            ssdPageController.setSelectedMotherboard(build.get("motherboard"));
            ssdPageController.setSelectedSsd(build.get("storage-ssd"));
            showSsdPage();
            return;
        }
        if (slotKey.equals("storage-hdd")) {
            hddPageController.setParts(parts);
            hddPageController.setSelectedMotherboard(build.get("motherboard"));
            hddPageController.setSelectedHdd(build.get("storage-hdd"));
            hddPageController.setSelectedSsd(build.get("storage-ssd"));
            showHddPage();
            return;
        }
        if (slotKey.equals("graphics")) {
            gpuPageController.setParts(parts);
            gpuPageController.setSelectedMotherboard(build.get("motherboard"));
            gpuPageController.setSelectedPsu(build.get("power"));
            gpuPageController.setSelectedGpu(build.get("graphics"));
            showGpuPage();
            return;
        }
        if (slotKey.equals("power")) {
            psuPageController.setParts(parts);
            psuPageController.setSelectedGpu(build.get("graphics"));
            psuPageController.setSelectedPsu(build.get("power"));
            showPsuPage();
            return;
        }
        if (List.of("cpu-cooler", "casing", "casing-fan", "monitor", "keyboard", "mouse", "ups").contains(slotKey)) {
            categoryChooserController.setParts(parts);
            categoryChooserController.openChooser(slotKey, build);
            showCategoryChooserPage();
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
            if (slotKey.equals("power")) gpuPageController.setSelectedPsu(part);
            refreshBuild();
        });
    }

    private void showProcessorPage() {
        hideSavedBuildPages();
        categoryChooserController.getView().setVisible(false); categoryChooserController.getView().setManaged(false);
        psuPageController.getView().setVisible(false); psuPageController.getView().setManaged(false);
        gpuPageController.getView().setVisible(false); gpuPageController.getView().setManaged(false);
        hddPageController.getView().setVisible(false); hddPageController.getView().setManaged(false);
        ssdPageController.getView().setVisible(false); ssdPageController.getView().setManaged(false);
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
        hideSavedBuildPages();
        categoryChooserController.getView().setVisible(false); categoryChooserController.getView().setManaged(false);
        psuPageController.getView().setVisible(false); psuPageController.getView().setManaged(false);
        gpuPageController.getView().setVisible(false); gpuPageController.getView().setManaged(false);
        hddPageController.getView().setVisible(false); hddPageController.getView().setManaged(false);
        ssdPageController.getView().setVisible(false); ssdPageController.getView().setManaged(false);
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
        hideSavedBuildPages();
        categoryChooserController.getView().setVisible(false); categoryChooserController.getView().setManaged(false);
        psuPageController.getView().setVisible(false); psuPageController.getView().setManaged(false);
        gpuPageController.getView().setVisible(false); gpuPageController.getView().setManaged(false);
        hddPageController.getView().setVisible(false); hddPageController.getView().setManaged(false);
        ssdPageController.getView().setVisible(false); ssdPageController.getView().setManaged(false);
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
        hideSavedBuildPages();
        categoryChooserController.getView().setVisible(false); categoryChooserController.getView().setManaged(false);
        psuPageController.getView().setVisible(false); psuPageController.getView().setManaged(false);
        gpuPageController.getView().setVisible(false); gpuPageController.getView().setManaged(false);
        hddPageController.getView().setVisible(false); hddPageController.getView().setManaged(false);
        ssdPageController.getView().setVisible(false); ssdPageController.getView().setManaged(false);
        processorPageController.getView().setVisible(false);
        processorPageController.getView().setManaged(false);
        motherboardPageController.getView().setVisible(false);
        motherboardPageController.getView().setManaged(false);
        ramPageController.getView().setVisible(false);
        ramPageController.getView().setManaged(false);
        builderScroll.setVisible(true);
        builderScroll.setManaged(true);
    }

    private void showSsdPage() {
        hideSavedBuildPages();
        categoryChooserController.getView().setVisible(false); categoryChooserController.getView().setManaged(false);
        psuPageController.getView().setVisible(false); psuPageController.getView().setManaged(false);
        gpuPageController.getView().setVisible(false); gpuPageController.getView().setManaged(false);
        hddPageController.getView().setVisible(false); hddPageController.getView().setManaged(false);
        builderScroll.setVisible(false); builderScroll.setManaged(false);
        processorPageController.getView().setVisible(false); processorPageController.getView().setManaged(false);
        motherboardPageController.getView().setVisible(false); motherboardPageController.getView().setManaged(false);
        ramPageController.getView().setVisible(false); ramPageController.getView().setManaged(false);
        ssdPageController.getView().setVisible(true); ssdPageController.getView().setManaged(true);
    }

    private void showHddPage() {
        hideSavedBuildPages();
        categoryChooserController.getView().setVisible(false); categoryChooserController.getView().setManaged(false);
        psuPageController.getView().setVisible(false); psuPageController.getView().setManaged(false);
        gpuPageController.getView().setVisible(false); gpuPageController.getView().setManaged(false);
        builderScroll.setVisible(false); builderScroll.setManaged(false);
        processorPageController.getView().setVisible(false); processorPageController.getView().setManaged(false);
        motherboardPageController.getView().setVisible(false); motherboardPageController.getView().setManaged(false);
        ramPageController.getView().setVisible(false); ramPageController.getView().setManaged(false);
        ssdPageController.getView().setVisible(false); ssdPageController.getView().setManaged(false);
        hddPageController.getView().setVisible(true); hddPageController.getView().setManaged(true);
    }

    private void showGpuPage() {
        hideSavedBuildPages();
        categoryChooserController.getView().setVisible(false); categoryChooserController.getView().setManaged(false);
        psuPageController.getView().setVisible(false); psuPageController.getView().setManaged(false);
        builderScroll.setVisible(false); builderScroll.setManaged(false);
        processorPageController.getView().setVisible(false); processorPageController.getView().setManaged(false);
        motherboardPageController.getView().setVisible(false); motherboardPageController.getView().setManaged(false);
        ramPageController.getView().setVisible(false); ramPageController.getView().setManaged(false);
        ssdPageController.getView().setVisible(false); ssdPageController.getView().setManaged(false);
        hddPageController.getView().setVisible(false); hddPageController.getView().setManaged(false);
        gpuPageController.getView().setVisible(true); gpuPageController.getView().setManaged(true);
    }

    private void showPsuPage() {
        hideSavedBuildPages();
        categoryChooserController.getView().setVisible(false); categoryChooserController.getView().setManaged(false);
        builderScroll.setVisible(false); builderScroll.setManaged(false);
        processorPageController.getView().setVisible(false); processorPageController.getView().setManaged(false);
        motherboardPageController.getView().setVisible(false); motherboardPageController.getView().setManaged(false);
        ramPageController.getView().setVisible(false); ramPageController.getView().setManaged(false);
        ssdPageController.getView().setVisible(false); ssdPageController.getView().setManaged(false);
        hddPageController.getView().setVisible(false); hddPageController.getView().setManaged(false);
        gpuPageController.getView().setVisible(false); gpuPageController.getView().setManaged(false);
        psuPageController.getView().setVisible(true); psuPageController.getView().setManaged(true);
    }

    private void showCategoryChooserPage() {
        hideSavedBuildPages();
        builderScroll.setVisible(false); builderScroll.setManaged(false);
        processorPageController.getView().setVisible(false); processorPageController.getView().setManaged(false);
        motherboardPageController.getView().setVisible(false); motherboardPageController.getView().setManaged(false);
        ramPageController.getView().setVisible(false); ramPageController.getView().setManaged(false);
        ssdPageController.getView().setVisible(false); ssdPageController.getView().setManaged(false);
        hddPageController.getView().setVisible(false); hddPageController.getView().setManaged(false);
        gpuPageController.getView().setVisible(false); gpuPageController.getView().setManaged(false);
        psuPageController.getView().setVisible(false); psuPageController.getView().setManaged(false);
        categoryChooserController.getView().setVisible(true); categoryChooserController.getView().setManaged(true);
    }

    private void saveBuild() {
        if (build.isEmpty()) {
            showMessage(Alert.AlertType.INFORMATION, "Empty build", "Add at least one part before saving.");
            return;
        }
        TextInputDialog dialog = new TextInputDialog(activeBuildName == null ? "My PC Build" : activeBuildName);
        dialog.setTitle("Save / Update build");
        dialog.setHeaderText(activeBuildId == null ? "Name your PC build" : "Update the saved PC build");
        dialog.setContentText("Build name:");
        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty() || result.get().isBlank()) return;
        try {
            String requestedName = result.get().trim();
            Long existingId = activeBuildId;
            long savedId = buildRepository.save(existingId, requestedName, List.copyOf(build.values()));
            activeBuildName = requestedName;
            activeBuildId = savedId;
            refreshBuild();
            showMessage(Alert.AlertType.INFORMATION, "Build saved", "“" + activeBuildName + "” was saved to the SQLite database.");
        } catch (SQLException e) {
            showMessage(Alert.AlertType.ERROR, "Database error", e.getMessage());
        }
    }

    private void showLoadBuildPage() {
        hideAllPages();
        try {
            loadBuildPageController.setBuilds(buildRepository.findAll());
            loadBuildPageController.getView().setVisible(true);
            loadBuildPageController.getView().setManaged(true);
        } catch (SQLException e) {
            showMessage(Alert.AlertType.ERROR, "Database error", e.getMessage());
            showBuilderPage();
        }
    }

    private void showDeleteBuildPage() {
        hideAllPages();
        try {
            deleteBuildPageController.setBuilds(buildRepository.findAll());
            deleteBuildPageController.getView().setVisible(true);
            deleteBuildPageController.getView().setManaged(true);
        } catch (SQLException e) {
            showMessage(Alert.AlertType.ERROR, "Database error", e.getMessage());
            showBuilderPage();
        }
    }

    private void loadSelectedBuild(SavedBuild selected) {
        try {
            SavedBuild saved = buildRepository.findById(selected.id());
            if (saved == null) {
                loadBuildPageController.setStatus("That saved build no longer exists. Refresh the page.");
                return;
            }
            activeBuildId = saved.id();
            activeBuildName = saved.name();
            build.clear();
            for (Part part : saved.parts()) build.put(slotFor(part), part);
            refreshBuild();
            showBuilderPage();
        } catch (SQLException e) {
            loadBuildPageController.setStatus("Could not load build: " + e.getMessage());
        }
    }

    private void deleteSelectedBuild(SavedBuild selected) {
        javafx.scene.control.ButtonType deleteButton = new javafx.scene.control.ButtonType(
                "Delete build", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete '" + selected.name() + "'? This cannot be undone.",
                deleteButton, javafx.scene.control.ButtonType.CANCEL);
        confirmation.setTitle("Confirm deletion");
        confirmation.setHeaderText("Confirm deletion");
        if (confirmation.showAndWait().filter(button -> button == deleteButton).isEmpty()) return;
        try {
            buildRepository.delete(selected.id());
            if (activeBuildId != null && activeBuildId == selected.id()) {
                activeBuildId = null;
                activeBuildName = null;
                build.clear();
                refreshBuild();
            }
            deleteBuildPageController.setBuilds(buildRepository.findAll());
            deleteBuildPageController.setStatus("Saved build deleted.");
        } catch (SQLException e) {
            deleteBuildPageController.setStatus("Could not delete build: " + e.getMessage());
        }
    }

    private void hideSavedBuildPages() {
        for (Node page : List.of(loadBuildPageController.getView(), deleteBuildPageController.getView())) {
            page.setVisible(false);
            page.setManaged(false);
        }
    }

    private void hideAllPages() {
        builderScroll.setVisible(false);
        builderScroll.setManaged(false);
        for (Node page : List.of(processorPageController.getView(), motherboardPageController.getView(),
                ramPageController.getView(), ssdPageController.getView(), hddPageController.getView(),
                gpuPageController.getView(), psuPageController.getView(), categoryChooserController.getView(),
                loadBuildPageController.getView(), deleteBuildPageController.getView())) {
            page.setVisible(false);
            page.setManaged(false);
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
            case "ssd" -> "storage-ssd";
            case "hdd" -> "storage-hdd";
            case "cpu cooler" -> "cpu-cooler";
            case "casing fan" -> "casing-fan";
            case "casing" -> "casing";
            case "monitor" -> "monitor";
            case "keyboard" -> "keyboard";
            case "mouse" -> "mouse";
            case "ups" -> "ups";
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
