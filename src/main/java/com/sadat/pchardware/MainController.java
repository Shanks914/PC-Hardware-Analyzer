package com.sadat.pchardware;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.TextInputDialog;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.scene.layout.StackPane;
import javafx.scene.Node;

import java.sql.SQLException;
import java.io.IOException;
import java.nio.file.Path;
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
    private List<Part> parts = List.of();
    private final LinkedHashMap<String, Part> build = new LinkedHashMap<>();
    private String searchText = "";
    private Long activeBuildId;
    private String activeBuildName;
    private boolean buildIsSaved;
    private boolean catalogLoaded;

    @FXML
    private void initialize() {
        builderController.setOnChoose(this::choosePart);
        builderController.setOnRemove(this::removePart);
        builderController.setOnSync(() -> syncRemoteCatalogs(false));
        buildPanelController.setActions(
                () -> saveBuild(),
                this::showLoadBuildPage,
                this::showDeleteBuildPage,
                this::newBuild,
                this::downloadBuildPdf
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
                    putPart("processor", part);
                    refreshBuild();
                    showBuilderPage();
                }
        );
        motherboardPageController.setActions(
                this::showBuilderPage,
                part -> {
                    putPart("motherboard", part);
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
                    putPart("memory", part);
                    refreshBuild();
                    showBuilderPage();
                }
        );
        ssdPageController.setActions(this::showBuilderPage, part -> {
            putPart("storage-ssd", part);
            refreshBuild();
            showBuilderPage();
        });
        hddPageController.setActions(this::showBuilderPage, part -> {
            putPart("storage-hdd", part);
            refreshBuild();
            showBuilderPage();
        });
        gpuPageController.setActions(this::showBuilderPage, part -> {
            putPart("graphics", part);
            refreshBuild();
            showBuilderPage();
        });
        psuPageController.setActions(this::showBuilderPage, part -> {
            putPart("power", part);
            gpuPageController.setSelectedPsu(part);
            refreshBuild();
            showBuilderPage();
        });
        categoryChooserController.setActions(this::showBuilderPage, part -> {
            putPart(categoryChooserController.getSelectedSlot(), part);
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
        syncRemoteCatalogs(true);
    }

    private void refreshBuild() {
        String label = activeBuildName == null ? "Unsaved build"
                : buildIsSaved ? "Saved: " + activeBuildName : "Changes not saved: " + activeBuildName;
        builderController.showSelections(build);
        buildPanelController.showBuild(List.copyOf(build.values()), label);
        buildPanelController.showCompatibility(BuildCompatibility.check(build));
    }

    private void syncRemoteCatalogs(boolean startup) {
        builderController.setCatalogSyncing(true);
        builderController.setCatalogStatus(startup
                ? "Fetching component catalogs from GitHub..."
                : "Syncing component catalogs from GitHub...");
        catalogService.loadRemoteParts().whenComplete((loadedParts, error) -> Platform.runLater(() -> {
            if (error == null && !loadedParts.isEmpty()) {
                builderController.setCatalogSyncing(false);
                installCatalog(loadedParts, "Synced catalogs from GitHub");
                return;
            }
            String reason = errorMessage(error);
            if (startup && !catalogLoaded) {
                builderController.setCatalogStatus("GitHub is unavailable; loading the bundled catalogs...");
                catalogService.loadLocalParts().whenComplete((localParts, localError) -> Platform.runLater(() -> {
                    builderController.setCatalogSyncing(false);
                    if (localError != null || localParts.isEmpty()) {
                        builderController.setCatalogStatus("Could not load remote or bundled catalogs: "
                                + errorMessage(localError == null ? error : localError));
                    } else {
                        installCatalog(localParts, "Offline mode: bundled catalogs loaded; GitHub sync failed");
                    }
                }));
            } else {
                builderController.setCatalogSyncing(false);
                builderController.setCatalogStatus("GitHub sync failed; kept the current catalog. " + reason);
            }
        }));
    }

    private void installCatalog(List<Part> loadedParts, String statusPrefix) {
        parts = List.copyOf(loadedParts);
        catalogLoaded = true;
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
        long cpuCount = countParts("CPU");
        long boardCount = countParts("Motherboard");
        long ramCount = countParts("RAM");
        long ssdCount = countParts("SSD");
        long hddCount = countParts("HDD");
        long gpuCount = countParts("GPU");
        long psuCount = countParts("PSU");
        long extrasCount = parts.stream().filter(part -> List.of("CPU Cooler", "Casing", "Casing Fan", "Monitor", "Keyboard", "Mouse", "UPS")
                .stream().anyMatch(category -> part.category().equalsIgnoreCase(category))).count();
        builderController.setCatalogStatus(statusPrefix + ": " + cpuCount + " processors, " + boardCount
                + " motherboards, " + ramCount + " RAM kits, " + ssdCount + " SSDs, " + hddCount + " HDDs, "
                + gpuCount + " graphics cards, " + psuCount + " power supplies, and " + extrasCount
                + " cooling/case/accessory products.");
    }

    private long countParts(String category) {
        return parts.stream().filter(part -> part.category().equalsIgnoreCase(category)).count();
    }

    private String errorMessage(Throwable error) {
        if (error == null) return "No valid components were returned.";
        Throwable cause = error;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
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
            putPart(slotKey, part);
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

    private boolean saveBuild() {
        if (build.isEmpty()) {
            showMessage(Alert.AlertType.INFORMATION, "Empty build", "Add at least one part before saving.");
            return false;
        }
        TextInputDialog dialog = new TextInputDialog(activeBuildName == null ? "My PC Build" : activeBuildName);
        dialog.setTitle("Save / Update build");
        dialog.setHeaderText(activeBuildId == null ? "Name your PC build" : "Update the saved PC build");
        dialog.setContentText("Build name:");
        styleDialog(dialog, ButtonType.OK, ButtonType.CANCEL);
        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty() || result.get().isBlank()) return false;
        try {
            String requestedName = result.get().trim();
            Long existingId = activeBuildId;
            long savedId = buildRepository.save(existingId, requestedName, List.copyOf(build.values()));
            activeBuildName = requestedName;
            activeBuildId = savedId;
            buildIsSaved = true;
            refreshBuild();
            showMessage(Alert.AlertType.INFORMATION, "Build saved", "“" + activeBuildName + "” was saved to the SQLite database.");
            return true;
        } catch (SQLException e) {
            showMessage(Alert.AlertType.ERROR, "Database error", e.getMessage());
            return false;
        }
    }

    private void downloadBuildPdf() {
        if (build.isEmpty()) {
            showMessage(Alert.AlertType.INFORMATION, "Empty build", "Add components before downloading a build PDF.");
            return;
        }
        if (!buildIsSaved) {
            ButtonType saveAndContinue = new ButtonType("Save / Update first", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
            Alert prompt = new Alert(Alert.AlertType.CONFIRMATION,
                    "Save the current components before creating the PDF? The PDF will use the saved version.",
                    saveAndContinue, ButtonType.CANCEL);
            prompt.setTitle("Save build before PDF download");
            prompt.setHeaderText("Save or update this build first");
            styleDialog(prompt, saveAndContinue, ButtonType.CANCEL);
            if (prompt.showAndWait().filter(saveAndContinue::equals).isEmpty()) return;
            if (!saveBuild()) return;
        }

        try {
            SavedBuild saved = buildRepository.findById(activeBuildId);
            if (saved == null) {
                buildIsSaved = false;
                showMessage(Alert.AlertType.INFORMATION, "Build needs saving", "Save or update this build before downloading its PDF.");
                return;
            }
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Download PC build PDF");
            chooser.setInitialFileName(pdfFileName(saved.name()));
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
            Window owner = builderScroll.getScene() == null ? null : builderScroll.getScene().getWindow();
            java.io.File selectedFile = chooser.showSaveDialog(owner);
            if (selectedFile == null) return;
            Path destination = selectedFile.toPath();
            if (!destination.getFileName().toString().toLowerCase().endsWith(".pdf")) {
                destination = destination.resolveSibling(destination.getFileName() + ".pdf");
            }
            BuildPdfExporter.export(destination, saved);
            showMessage(Alert.AlertType.INFORMATION, "PDF downloaded", "Build PDF saved to:\n" + destination.toAbsolutePath());
        } catch (SQLException | IOException e) {
            showMessage(Alert.AlertType.ERROR, "PDF download failed", e.getMessage());
        }
    }

    private String pdfFileName(String buildName) {
        String safe = buildName.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return (safe.isBlank() ? "PC Build" : safe) + ".pdf";
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
            buildIsSaved = true;
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
                deleteButton, ButtonType.CANCEL);
        confirmation.setTitle("Confirm deletion");
        confirmation.setHeaderText("Confirm deletion");
        styleDialog(confirmation, deleteButton, ButtonType.CANCEL);
        confirmation.getDialogPane().lookupButton(deleteButton).getStyleClass().remove("button-primary");
        confirmation.getDialogPane().lookupButton(deleteButton).getStyleClass().add("button-danger");
        if (confirmation.showAndWait().filter(button -> button == deleteButton).isEmpty()) return;
        try {
            buildRepository.delete(selected.id());
            if (activeBuildId != null && activeBuildId.longValue() == selected.id()) {
                activeBuildId = null;
                activeBuildName = null;
                build.clear();
                buildIsSaved = false;
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
        buildIsSaved = false;
        refreshBuild();
    }

    private void putPart(String slotKey, Part part) {
        Part previous = build.put(slotKey, part);
        if (!part.equals(previous)) buildIsSaved = false;
    }

    private void removePart(String slotKey) {
        if (build.remove(slotKey) != null) {
            buildIsSaved = false;
            refreshBuild();
        }
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
        styleDialog(alert, ButtonType.OK);
        alert.showAndWait();
    }

    private void styleDialog(Dialog<?> dialog, ButtonType primaryButton, ButtonType... secondaryButtons) {
        DialogPane pane = dialog.getDialogPane();
        var stylesheet = getClass().getResource("app.css");
        if (stylesheet != null) pane.getStylesheets().add(stylesheet.toExternalForm());
        pane.getStyleClass().add("app-dialog");
        pane.setGraphic(null);
        if (primaryButton != null && pane.lookupButton(primaryButton) != null) {
            pane.lookupButton(primaryButton).getStyleClass().add("button-primary");
        }
        for (ButtonType button : secondaryButtons) {
            if (pane.lookupButton(button) != null) pane.lookupButton(button).getStyleClass().add("button-secondary");
        }
    }

    @Override
    public void close() {
        catalogService.close();
    }
}
