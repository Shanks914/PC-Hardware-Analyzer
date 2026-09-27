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

/** Local SSD chooser with catalog-driven filters and motherboard interface checks. */
public class SsdPageController {
    private static final int PAGE_SIZE = 10;
    @FXML private BorderPane root;
    @FXML private TextField minPriceField, maxPriceField, searchField;
    @FXML private ComboBox<String> availabilityFilter, brandFilter, formFactorFilter, capacityFilter;
    @FXML private ComboBox<String> interfaceFilter, generationFilter, readSpeedFilter, typeFilter, sortBox;
    @FXML private CheckBox compatibleOnlyFilter;
    @FXML private VBox productRows;
    @FXML private Label resultCount, compatibilityHint, pageStatus, paginationLabel;
    @FXML private Button previousButton, nextButton;

    private List<Part> ssds = List.of(), filtered = List.of(), motherboards = List.of();
    private Part selectedSsd, selectedMotherboard;
    private Consumer<Part> onAdd = part -> {};
    private Runnable onBack = () -> {};
    private Runnable onCompare = () -> {};
    private int page;

    @FXML private void initialize() {
        setOptions(availabilityFilter, "Any availability", "In stock", "Pre-order", "Out of stock", "Not specified");
        setOptions(formFactorFilter, "Any form factor", "2.5-Inch", "M.2 2280", "M.2 SATA", "External");
        setOptions(capacityFilter, "Any capacity", "Up to 256GB", "257–512GB", "513GB–1TB", "1–2TB", "Over 2TB");
        setOptions(interfaceFilter, "Any interface", "SATA", "PCIe NVMe", "USB");
        setOptions(generationFilter, "Any PCIe generation", "PCIe Gen3", "PCIe Gen4", "PCIe Gen5");
        setOptions(readSpeedFilter, "Any read speed", "Up to 1,000MB/s", "1,001–3,000MB/s", "3,001–5,000MB/s", "5,001–8,000MB/s", "Over 8,000MB/s");
        setOptions(typeFilter, "Any type", "Internal", "External");
        setOptions(sortBox, "Recommended", "Price: low to high", "Price: high to low", "Name: A to Z");
        sortBox.setOnAction(event -> applyFilters());
        compatibleOnlyFilter.setDisable(true);
    }

    private void setOptions(ComboBox<String> box, String... values) {
        box.setItems(FXCollections.observableArrayList(values));
        box.getSelectionModel().selectFirst();
    }
    public Node getView() { return root; }
    public void setActions(Runnable back, Consumer<Part> add) { onBack = back; onAdd = add; }
    public void setCompareAction(Runnable action) { onCompare = action; }
    public void setParts(List<Part> parts) {
        motherboards = parts.stream().filter(p -> p.category().equalsIgnoreCase("Motherboard")).toList();
        ssds = parts.stream().filter(p -> p.category().equalsIgnoreCase("SSD")).toList();
        Set<String> brands = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        ssds.forEach(p -> { if (!value(p, "brand").isBlank()) brands.add(value(p, "brand")); });
        brandFilter.getItems().setAll("Any brand"); brandFilter.getItems().addAll(brands); brandFilter.getSelectionModel().selectFirst();
        applyFilters();
        if (ssds.isEmpty()) pageStatus.setText("No SSDs found in catalog/ssds.json. Reload local data from the build page.");
    }
    public void setSelectedMotherboard(Part board) {
        selectedMotherboard = board;
        compatibleOnlyFilter.setDisable(board == null);
        if (board == null) {
            compatibilityHint.setText("Choose a motherboard to enable compatibility filtering.");
            compatibleOnlyFilter.setSelected(false);
        } else compatibilityHint.setText("Selected motherboard: " + board.name() + " · SSD interface and M.2 slot support will be checked.");
        applyFilters();
    }
    public void setSelectedSsd(Part ssd) { selectedSsd = ssd; renderPage(); }
    public void setSearchQuery(String query) { searchField.setText(query == null ? "" : query); applyFilters(); }
    @FXML private void backToBuild() { onBack.run(); }
    @FXML private void compareComponents() { onCompare.run(); }

    @FXML private void clearFilters() {
        minPriceField.clear(); maxPriceField.clear(); searchField.clear();
        for (ComboBox<String> box : List.of(availabilityFilter, brandFilter, formFactorFilter, capacityFilter,
                interfaceFilter, generationFilter, readSpeedFilter, typeFilter, sortBox)) box.getSelectionModel().selectFirst();
        compatibleOnlyFilter.setSelected(false); applyFilters();
    }

    @FXML private void applyFilters() {
        page = 0;
        Double min = parsePrice(minPriceField.getText()), max = parsePrice(maxPriceField.getText());
        if ((!minPriceField.getText().isBlank() && min == null) || (!maxPriceField.getText().isBlank() && max == null)) {
            pageStatus.setText("Enter valid numeric values for the price range."); return;
        }
        if (min != null && max != null && min > max) { pageStatus.setText("Minimum price cannot be greater than maximum price."); return; }
        String query = searchField.getText().trim().toLowerCase(Locale.ROOT);
        filtered = ssds.stream().filter(p -> min == null || p.price() >= min).filter(p -> max == null || p.price() <= max)
                .filter(p -> choose(availabilityFilter).equals("Any availability") || choose(availabilityFilter).equalsIgnoreCase(value(p,"availability")))
                .filter(p -> choose(brandFilter).equals("Any brand") || choose(brandFilter).equalsIgnoreCase(value(p,"brand")))
                .filter(p -> choose(formFactorFilter).equals("Any form factor") || choose(formFactorFilter).equalsIgnoreCase(value(p,"formFactor")))
                .filter(p -> matchesCapacity(p, choose(capacityFilter)))
                .filter(p -> choose(interfaceFilter).equals("Any interface") || choose(interfaceFilter).equalsIgnoreCase(value(p,"interface")))
                .filter(p -> matchesGeneration(p, choose(generationFilter)))
                .filter(p -> matchesReadSpeed(p, choose(readSpeedFilter)))
                .filter(p -> choose(typeFilter).equals("Any type") || choose(typeFilter).equalsIgnoreCase(value(p,"deviceType")))
                .filter(p -> !compatibleOnlyFilter.isSelected() || compatibilityProblem(p).isBlank())
                .filter(p -> query.isBlank() || searchable(p).contains(query)).sorted(comparator(choose(sortBox))).toList();
        pageStatus.setText(ssds.isEmpty() ? "SSD catalog is empty." : "Local SSD catalog · Example BDT prices and availability for the course project.");
        renderPage();
    }
    @FXML private void previousPage() { if (page > 0) { page--; renderPage(); } }
    @FXML private void nextPage() { if ((page + 1) * PAGE_SIZE < filtered.size()) { page++; renderPage(); } }

    private void renderPage() {
        productRows.getChildren().clear();
        resultCount.setText(filtered.size() + (filtered.size() == 1 ? " SSD" : " SSDs"));
        int pages = Math.max(1, (filtered.size() + PAGE_SIZE - 1) / PAGE_SIZE); page = Math.min(page, pages - 1);
        paginationLabel.setText("Page " + (page + 1) + " of " + pages); previousButton.setDisable(page == 0); nextButton.setDisable(page + 1 >= pages);
        for (Part part : filtered.subList(page * PAGE_SIZE, Math.min((page + 1) * PAGE_SIZE, filtered.size()))) productRows.getChildren().add(card(part));
    }
    private HBox card(Part part) {
        VBox details = new VBox(6); Label name = new Label(part.name()); name.getStyleClass().add("processor-name"); name.setWrapText(true);
        Label specs = new Label(part.specs()); specs.getStyleClass().add("processor-specs"); specs.setWrapText(true);
        Label compat = new Label(compatibilityText(part)); compat.getStyleClass().add("compatibility-status");
        compat.getStyleClass().add(selectedMotherboard == null ? "compatibility-neutral" : compatibilityProblem(part).isBlank() ? "compatibility-ok" : "compatibility-error");
        details.getChildren().addAll(name, specs, compat); HBox.setHgrow(details, Priority.ALWAYS);
        VBox purchase = new VBox(10); purchase.setMinWidth(145); purchase.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        Label price = new Label(String.format("৳%,.0f", part.price())); price.getStyleClass().add("processor-price");
        boolean chosen = selectedSsd != null && selectedSsd.name().equalsIgnoreCase(part.name());
        Button add = new Button(chosen ? "Selected · Replace" : "Add to build"); add.getStyleClass().add(chosen ? "button-primary" : "button-secondary"); add.setOnAction(e -> onAdd.accept(part));
        purchase.getChildren().addAll(price, add); HBox row = new HBox(18, details, purchase); row.setAlignment(javafx.geometry.Pos.CENTER_LEFT); row.getStyleClass().add("processor-card"); return row;
    }
    private String compatibilityText(Part ssd) {
        if (selectedMotherboard == null) return "Select a motherboard to check storage compatibility.";
        String issue = compatibilityProblem(ssd);
        if (issue.isBlank()) {
            int gen = number(selectedMotherboard, "maxM2Generation"); int ssdGen = number(ssd, "pcieGeneration");
            return ssdGen > gen && gen > 0 ? "Compatible · SSD will run up to PCIe Gen" + gen + "." : "Compatible with selected motherboard.";
        }
        return "Not compatible: " + issue;
    }
    private String compatibilityProblem(Part ssd) {
        if (selectedMotherboard == null) return "Motherboard is not selected.";
        Part board = selectedMotherboard;
        if (board.attribute("storageInterfaces").isBlank()) {
            String boardName = board.name();
            board = motherboards.stream().filter(p -> p.name().equalsIgnoreCase(boardName)).findFirst().orElse(board);
        }
        String supported = value(board,"storageInterfaces").toUpperCase(Locale.ROOT);
        String iface = value(ssd,"interface");
        if (supported.isBlank()) return "motherboard storage-interface data is missing.";
        if (iface.equalsIgnoreCase("USB")) return supported.contains("USB") ? "" : "motherboard has no listed USB storage support.";
        if (iface.equalsIgnoreCase("SATA")) {
            if (!supported.contains("SATA")) return "motherboard does not list SATA storage support.";
            if (value(ssd,"formFactor").startsWith("M.2") && number(board,"m2Slots") < 1) return "motherboard has no M.2 slot for this M.2 SATA SSD.";
            return "";
        }
        if (iface.equalsIgnoreCase("PCIe NVMe")) {
            if (!supported.contains("NVME")) return "motherboard does not list NVMe support.";
            if (number(board,"m2Slots") < 1) return "motherboard has no M.2 slot for this NVMe SSD.";
            return "";
        }
        return "SSD interface is not recognized.";
    }
    private boolean matchesCapacity(Part p, String f) {
        int n = number(p,"capacityGb"); return switch (f) { case "Up to 256GB" -> n <= 256; case "257–512GB" -> n >= 257 && n <= 512; case "513GB–1TB" -> n >= 513 && n <= 1000; case "1–2TB" -> n > 1000 && n <= 2000; case "Over 2TB" -> n > 2000; default -> true; };
    }
    private boolean matchesGeneration(Part p, String f) { if (f.equals("Any PCIe generation")) return true; int gen=number(p,"pcieGeneration"); return gen == Integer.parseInt(f.replaceAll("\\D", "")); }
    private boolean matchesReadSpeed(Part p, String f) { int n=number(p,"maxReadMBps"); return switch(f) { case "Up to 1,000MB/s" -> n <= 1000; case "1,001–3,000MB/s" -> n >= 1001 && n <= 3000; case "3,001–5,000MB/s" -> n >= 3001 && n <= 5000; case "5,001–8,000MB/s" -> n >= 5001 && n <= 8000; case "Over 8,000MB/s" -> n > 8000; default -> true; }; }
    private String value(Part p,String key) { return p.attribute(key); }
    private int number(Part p,String key) { try { return Integer.parseInt(value(p,key)); } catch(Exception ignored) { return 0; } }
    private String choose(ComboBox<String> box) { return box.getValue() == null ? "" : box.getValue(); }
    private String searchable(Part p) { return (p.name()+" "+p.specs()+" "+String.join(" ",p.attributes().values())).toLowerCase(Locale.ROOT); }
    private Double parsePrice(String value) { if(value==null||value.isBlank())return null; try{return Double.parseDouble(value.replace(",","").trim());}catch(NumberFormatException e){return null;} }
    private Comparator<Part> comparator(String sort) { return switch(sort) { case "Price: low to high" -> Comparator.comparingDouble(Part::price); case "Price: high to low" -> Comparator.comparingDouble(Part::price).reversed(); case "Name: A to Z" -> Comparator.comparing(Part::name,String.CASE_INSENSITIVE_ORDER); default -> Comparator.comparing(Part::name,String.CASE_INSENSITIVE_ORDER); }; }
}
