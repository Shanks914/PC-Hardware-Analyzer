package com.sadat.pchardware;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class BuilderController {
    @FXML private VBox componentRows;
    @FXML private VBox peripheralRows;
    @FXML private VBox peripheralSection;
    @FXML private Label catalogStatus;
    @FXML private CheckBox hideUnconfigured;

    private final List<Slot> coreSlots = List.of(
            new Slot("processor", "Processor", "CPU", true),
            new Slot("motherboard", "Motherboard", "Motherboard", true),
            new Slot("cpu-cooler", "CPU Cooler", null, false),
            new Slot("memory", "Desktop RAM", "RAM", true),
            new Slot("storage-ssd", "SSD", "Storage", false),
            new Slot("storage-hdd", "Hard Disk Drive", "Storage", false),
            new Slot("graphics", "Graphics Card", "GPU", true),
            new Slot("power", "Power Supply", "PSU", true),
            new Slot("casing", "Casing", null, true),
            new Slot("casing-fan", "Casing Fan", null, false)
    );
    private final List<Slot> peripherals = List.of(
            new Slot("monitor", "Monitor", null, false),
            new Slot("keyboard", "Keyboard", null, false),
            new Slot("mouse", "Mouse", null, false),
            new Slot("ups", "UPS", null, false)
    );
    private final Map<String, HBox> rowsBySlot = new LinkedHashMap<>();
    private Map<String, Part> selected = Map.of();
    private BiConsumer<String, String> onChoose = (key, type) -> {};
    private Consumer<String> onRemove = key -> {};
    private String searchText = "";
    private boolean hideEmpty;

    @FXML
    private void initialize() {
        for (Slot slot : coreSlots) componentRows.getChildren().add(createRow(slot));
        for (Slot slot : peripherals) peripheralRows.getChildren().add(createRow(slot));
        hideUnconfigured.selectedProperty().addListener((obs, oldValue, newValue) -> {
            hideEmpty = newValue;
            applyVisibility();
        });
    }

    public void setOnChoose(BiConsumer<String, String> action) {
        onChoose = action;
    }

    public void setOnRemove(Consumer<String> action) {
        onRemove = action;
    }

    public void setSearchText(String searchText) {
        this.searchText = searchText;
    }

    public String getSearchText() {
        return searchText;
    }

    public void setCatalogStatus(String status) {
        catalogStatus.setText(status);
    }

    public void showSelections(Map<String, Part> selections) {
        selected = Map.copyOf(selections);
        for (Slot slot : coreSlots) renderRow(slot);
        for (Slot slot : peripherals) renderRow(slot);
        applyVisibility();
    }

    private HBox createRow(Slot slot) {
        Label number = new Label(String.format("%02d", rowsBySlot.size() + 1));
        number.getStyleClass().add("row-number");
        Label title = new Label(slot.title());
        title.getStyleClass().add("quote-row-title");
        Label requirement = new Label(slot.required() ? "REQUIRED" : "OPTIONAL");
        requirement.getStyleClass().add(slot.required() ? "required-badge" : "optional-badge");
        VBox labels = new VBox(5, title, requirement);

        Label selectedName = new Label();
        selectedName.getStyleClass().add("quote-row-selection");
        selectedName.setWrapText(true);
        Label price = new Label();
        price.getStyleClass().add("row-price");
        VBox chosenPart = new VBox(5, selectedName, price);
        HBox.setHgrow(chosenPart, Priority.ALWAYS);

        Button choose = new Button(slot.catalogType() == null ? "Not in catalog" : "Choose");
        choose.getStyleClass().add("button-secondary");
        choose.setDisable(slot.catalogType() == null);
        choose.setOnAction(event -> onChoose.accept(slot.key(), slot.catalogType()));
        Button remove = new Button("Remove");
        remove.getStyleClass().add("button-quiet");
        remove.setOnAction(event -> onRemove.accept(slot.key()));
        HBox actions = new HBox(8, choose, remove);
        actions.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(14, number, labels, spacer, chosenPart, actions);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        row.getStyleClass().add("quote-row");
        rowsBySlot.put(slot.key(), row);
        renderRowContent(slot, row, selectedName, price, choose, remove);
        return row;
    }

    private void renderRow(Slot slot) {
        HBox row = rowsBySlot.get(slot.key());
        if (row == null) return;
        VBox chosenPart = (VBox) row.getChildren().get(3);
        Label name = (Label) chosenPart.getChildren().get(0);
        Label price = (Label) chosenPart.getChildren().get(1);
        HBox actions = (HBox) row.getChildren().get(4);
        Button choose = (Button) actions.getChildren().get(0);
        Button remove = (Button) actions.getChildren().get(1);
        renderRowContent(slot, row, name, price, choose, remove);
    }

    private void renderRowContent(Slot slot, HBox row, Label name, Label price, Button choose, Button remove) {
        Part part = selected.get(slot.key());
        if (part == null) {
            name.setText(slot.catalogType() == null
                    ? "This component type is not in the current catalog"
                    : "Choose a component for your build");
            name.getStyleClass().remove("selected-part-name");
            name.getStyleClass().add("quote-row-selection");
            price.setText("");
            remove.setDisable(true);
            row.getStyleClass().remove("quote-row-configured");
            choose.setText(slot.catalogType() == null ? "Not in catalog" : "Choose");
        } else {
            name.setText(part.name() + (part.specs().isBlank() ? "" : "  ·  " + part.specs()));
            name.getStyleClass().remove("quote-row-selection");
            name.getStyleClass().add("selected-part-name");
            price.setText(String.format("৳%,.0f", part.price()));
            remove.setDisable(false);
            if (!row.getStyleClass().contains("quote-row-configured")) row.getStyleClass().add("quote-row-configured");
            choose.setText("Change");
        }
    }

    private void applyVisibility() {
        for (Slot slot : coreSlots) setRowVisibility(componentRows, slot);
        for (Slot slot : peripherals) setRowVisibility(peripheralRows, slot);
        boolean hasVisiblePeripheral = !hideEmpty || peripherals.stream().anyMatch(slot -> selected.containsKey(slot.key()));
        peripheralSection.setVisible(hasVisiblePeripheral);
        peripheralSection.setManaged(hasVisiblePeripheral);
    }

    private void setRowVisibility(VBox parent, Slot slot) {
        HBox row = rowsBySlot.get(slot.key());
        boolean visible = !hideEmpty || selected.containsKey(slot.key());
        row.setVisible(visible);
        row.setManaged(visible);
    }

    private record Slot(String key, String title, String catalogType, boolean required) {
    }
}
