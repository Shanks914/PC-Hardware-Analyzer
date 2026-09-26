package com.sadat.pchardware;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** GPU catalog page with functional filters and PCIe/PSU compatibility feedback. */
public class GpuPageController {
    private static final int PAGE_SIZE = 10;
    private static final Pattern WATTAGE = Pattern.compile("(?i)(\\d+)\\s*W\\b");
    @FXML private BorderPane root;
    @FXML private TextField minPriceField, maxPriceField, searchField;
    @FXML private ComboBox<String> availabilityFilter, brandFilter, outputFilter, chipsetFilter, memorySizeFilter, memoryTypeFilter, sortBox;
    @FXML private CheckBox compatibleOnlyFilter;
    @FXML private VBox productRows;
    @FXML private Label resultCount, compatibilityHint, pageStatus, paginationLabel;
    @FXML private Button previousButton, nextButton;
    private List<Part> gpus = List.of(), filtered = List.of(), motherboards = List.of();
    private Part selectedGpu, selectedMotherboard, selectedPsu;
    private Consumer<Part> onAdd = part -> {};
    private Runnable onBack = () -> {};
    private int page;

    @FXML private void initialize() {
        options(availabilityFilter, "Any availability", "In stock", "Pre-order", "Out of stock", "Not specified");
        options(chipsetFilter, "Any chipset", "NVIDIA GeForce", "AMD Radeon", "Intel Arc");
        options(sortBox, "Recommended", "Price: low to high", "Price: high to low", "Name: A to Z");
        sortBox.setOnAction(e -> applyFilters());
        compatibleOnlyFilter.setDisable(true);
    }
    private void options(ComboBox<String> box, String... values) { box.setItems(FXCollections.observableArrayList(values)); box.getSelectionModel().selectFirst(); }
    public Node getView() { return root; }
    public void setActions(Runnable back, Consumer<Part> add) { onBack = back; onAdd = add; }
    public void setParts(List<Part> parts) {
        gpus = parts.stream().filter(p -> p.category().equalsIgnoreCase("GPU")).toList();
        motherboards = parts.stream().filter(p -> p.category().equalsIgnoreCase("Motherboard")).toList();
        populateFilter(brandFilter, "Any brand", gpus.stream().map(p -> p.attribute("brand")).toList());
        Set<String> outputs = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        gpus.forEach(p -> outputs.addAll(List.of(p.attribute("outputs").split("\\|"))));
        populateFilter(outputFilter, "Any output", List.copyOf(outputs));
        populateFilter(memorySizeFilter, "Any memory size", gpus.stream().map(p -> p.attribute("memorySizeGb") + "GB").toList());
        populateFilter(memoryTypeFilter, "Any memory type", gpus.stream().map(p -> p.attribute("memoryType")).toList());
        applyFilters();
        if (gpus.isEmpty()) pageStatus.setText("No GPUs found in catalog/gpus.json. Reload local data from the build page.");
    }
    private void populateFilter(ComboBox<String> box, String all, List<String> values) {
        Set<String> unique = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        values.stream().filter(v -> v != null && !v.isBlank()).forEach(unique::add);
        box.getItems().setAll(all); box.getItems().addAll(unique); box.getSelectionModel().selectFirst();
    }
    public void setSelectedMotherboard(Part board) {
        selectedMotherboard = board; updateCompatibilityHint(); applyFilters();
    }
    public void setSelectedPsu(Part psu) { selectedPsu = psu; updateCompatibilityHint(); applyFilters(); }
    public void setSelectedGpu(Part gpu) { selectedGpu = gpu; renderPage(); }
    public void setSearchQuery(String query) { searchField.setText(query == null ? "" : query); applyFilters(); }
    @FXML private void backToBuild() { onBack.run(); }
    @FXML private void clearFilters() {
        minPriceField.clear(); maxPriceField.clear(); searchField.clear();
        for (ComboBox<String> box : List.of(availabilityFilter, brandFilter, outputFilter, chipsetFilter, memorySizeFilter, memoryTypeFilter, sortBox)) box.getSelectionModel().selectFirst();
        compatibleOnlyFilter.setSelected(false); applyFilters();
    }
    @FXML private void applyFilters() {
        page = 0; Double min = parsePrice(minPriceField.getText()), max = parsePrice(maxPriceField.getText());
        if ((!minPriceField.getText().isBlank() && min == null) || (!maxPriceField.getText().isBlank() && max == null)) { pageStatus.setText("Enter valid numeric values for the price range."); return; }
        if (min != null && max != null && min > max) { pageStatus.setText("Minimum price cannot be greater than maximum price."); return; }
        String query = searchField.getText().trim().toLowerCase(Locale.ROOT);
        filtered = gpus.stream().filter(p -> min == null || p.price() >= min).filter(p -> max == null || p.price() <= max)
                .filter(p -> any(availabilityFilter) || eq(p,"availability",selected(availabilityFilter)))
                .filter(p -> any(brandFilter) || eq(p,"brand",selected(brandFilter)))
                .filter(p -> any(outputFilter) || List.of(p.attribute("outputs").split("\\|")).stream().anyMatch(o -> o.equalsIgnoreCase(selected(outputFilter))))
                .filter(p -> any(chipsetFilter) || eq(p,"chipsetFamily",selected(chipsetFilter)))
                .filter(p -> any(memorySizeFilter) || p.attribute("memorySizeGb").equals(selected(memorySizeFilter).replaceAll("\\D", "")))
                .filter(p -> any(memoryTypeFilter) || eq(p,"memoryType",selected(memoryTypeFilter)))
                .filter(p -> !compatibleOnlyFilter.isSelected() || compatibilityIssue(p).isBlank())
                .filter(p -> query.isBlank() || searchable(p).contains(query)).sorted(comparator(selected(sortBox))).toList();
        pageStatus.setText(gpus.isEmpty() ? "GPU catalog is empty." : "Local graphics-card catalog · Example BDT prices and availability for the course project.");
        renderPage();
    }
    @FXML private void previousPage() { if (page > 0) { page--; renderPage(); } }
    @FXML private void nextPage() { if ((page + 1) * PAGE_SIZE < filtered.size()) { page++; renderPage(); } }
    private void renderPage() {
        productRows.getChildren().clear(); resultCount.setText(filtered.size() + (filtered.size() == 1 ? " graphics card" : " graphics cards"));
        int pages = Math.max(1, (filtered.size() + PAGE_SIZE - 1) / PAGE_SIZE); page = Math.min(page, pages - 1);
        paginationLabel.setText("Page " + (page + 1) + " of " + pages); previousButton.setDisable(page == 0); nextButton.setDisable(page + 1 >= pages);
        for (Part p : filtered.subList(page * PAGE_SIZE, Math.min((page + 1) * PAGE_SIZE, filtered.size()))) productRows.getChildren().add(card(p));
    }
    private HBox card(Part gpu) {
        VBox detail = new VBox(6); Label name = new Label(gpu.name()); name.getStyleClass().add("processor-name"); name.setWrapText(true);
        Label specs = new Label(gpu.specs()); specs.getStyleClass().add("processor-specs"); specs.setWrapText(true);
        Label compat = new Label(compatibilityText(gpu)); compat.getStyleClass().add("compatibility-status");
        compat.getStyleClass().add(selectedMotherboard == null || selectedPsu == null ? "compatibility-neutral" : compatibilityIssue(gpu).isBlank() ? "compatibility-ok" : "compatibility-error");
        detail.getChildren().addAll(name, specs, compat); HBox.setHgrow(detail, Priority.ALWAYS);
        VBox buy = new VBox(10); buy.setMinWidth(145); buy.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        Label price = new Label(String.format("৳%,.0f", gpu.price())); price.getStyleClass().add("processor-price");
        boolean chosen = selectedGpu != null && selectedGpu.name().equalsIgnoreCase(gpu.name());
        Button add = new Button(chosen ? "Selected · Replace" : "Add to build"); add.getStyleClass().add(chosen ? "button-primary" : "button-secondary"); add.setOnAction(e -> onAdd.accept(gpu));
        buy.getChildren().addAll(price, add); HBox row = new HBox(18, detail, buy); row.setAlignment(javafx.geometry.Pos.CENTER_LEFT); row.getStyleClass().add("processor-card"); return row;
    }
    private String compatibilityText(Part gpu) {
        if (selectedMotherboard == null) return "Select a motherboard to check PCIe slot compatibility.";
        String boardIssue = motherboardIssue(); if (!boardIssue.isBlank()) return "Not compatible: " + boardIssue;
        if (selectedPsu == null) return "Motherboard PCIe slot is compatible. Select a PSU to verify power requirements.";
        String issue = compatibilityIssue(gpu); return issue.isBlank() ? "Compatible with selected motherboard and PSU." : "Not compatible: " + issue;
    }
    private String compatibilityIssue(Part gpu) {
        if (selectedMotherboard == null) return "motherboard is not selected.";
        String boardIssue = motherboardIssue(); if (!boardIssue.isBlank()) return boardIssue;
        if (selectedPsu == null) return "power-supply selection is required for a wattage check.";
        int recommended = number(gpu,"recommendedPsuW"), supplied = wattage(selectedPsu);
        if (recommended > 0 && supplied > 0 && supplied < recommended) return "GPU recommends a " + recommended + "W PSU, but the selected PSU is " + supplied + "W.";
        if (recommended > 0 && supplied <= 0) return "PSU wattage could not be read from its specification.";
        return "";
    }
    private String motherboardIssue() {
        Part board = selectedMotherboard;
        if (board.attribute("pcieX16Slots").isBlank()) { String name = board.name(); board = motherboards.stream().filter(p -> p.name().equalsIgnoreCase(name)).findFirst().orElse(board); }
        if (board.attribute("pcieX16Slots").isBlank()) return "motherboard PCIe x16 slot data is missing.";
        return number(board,"pcieX16Slots") < 1 ? "motherboard has no PCIe x16 graphics slot." : "";
    }
    private void updateCompatibilityHint() {
        compatibleOnlyFilter.setDisable(selectedMotherboard == null || selectedPsu == null);
        if (selectedMotherboard == null) { compatibilityHint.setText("Choose a motherboard and PSU to enable compatibility filtering."); compatibleOnlyFilter.setSelected(false); }
        else if (selectedPsu == null) { compatibilityHint.setText("Selected motherboard: " + selectedMotherboard.name() + " · Select a PSU to check GPU power requirements."); compatibleOnlyFilter.setSelected(false); }
        else compatibilityHint.setText("Selected motherboard and PSU: checking PCIe x16 and recommended power requirements.");
    }
    private boolean any(ComboBox<String> box) { return selected(box).startsWith("Any "); }
    private String selected(ComboBox<String> box) { return box.getValue() == null ? "" : box.getValue(); }
    private boolean eq(Part p,String key,String value) { return p.attribute(key).equalsIgnoreCase(value); }
    private String searchable(Part p) { return (p.name()+" "+p.specs()+" "+String.join(" ",p.attributes().values())).toLowerCase(Locale.ROOT); }
    private int number(Part p,String key) { try { return Integer.parseInt(p.attribute(key)); } catch(Exception ignored) { return 0; } }
    private int wattage(Part p) { Matcher m=WATTAGE.matcher(p.name()+" "+p.specs()+" "+p.attribute("wattage")); return m.find()?Integer.parseInt(m.group(1)):0; }
    private Double parsePrice(String s) { if(s==null||s.isBlank())return null; try{return Double.parseDouble(s.replace(",","").trim());}catch(NumberFormatException e){return null;} }
    private Comparator<Part> comparator(String sort) { return switch(sort) { case "Price: low to high" -> Comparator.comparingDouble(Part::price); case "Price: high to low" -> Comparator.comparingDouble(Part::price).reversed(); case "Name: A to Z" -> Comparator.comparing(Part::name,String.CASE_INSENSITIVE_ORDER); default -> Comparator.comparing(Part::name,String.CASE_INSENSITIVE_ORDER); }; }
}
