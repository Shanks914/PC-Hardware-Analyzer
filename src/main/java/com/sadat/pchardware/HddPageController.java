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

/** HDD catalog browser with PCB Store-style filters and motherboard SATA checks. */
public class HddPageController {
    private static final int PAGE_SIZE = 10;
    @FXML private BorderPane root;
    @FXML private TextField minPriceField, maxPriceField, searchField;
    @FXML private ComboBox<String> availabilityFilter, colorFilter, readSpeedFilter, writeSpeedFilter;
    @FXML private ComboBox<String> brandFilter, capacityFilter, formFactorFilter, rpmFilter, sortBox;
    @FXML private CheckBox compatibleOnlyFilter;
    @FXML private VBox productRows;
    @FXML private Label resultCount, compatibilityHint, pageStatus, paginationLabel;
    @FXML private Button previousButton, nextButton;

    private List<Part> hdds = List.of(), filtered = List.of(), motherboards = List.of();
    private Part selectedHdd, selectedMotherboard, selectedSsd;
    private Consumer<Part> onAdd = part -> {};
    private Runnable onBack = () -> {};
    private Runnable onCompare = () -> {};
    private int page;

    @FXML private void initialize() {
        options(availabilityFilter, "Any availability", "In stock", "Pre-order", "Out of stock", "Not specified");
        options(colorFilter, "Any color", "Black", "Blue", "Green", "Red", "Purple", "Silver", "Orange", "Gray", "White");
        options(readSpeedFilter, "Any read speed", "Under 180MB/s", "180–285MB/s", "285–500MB/s", "Over 500MB/s");
        options(writeSpeedFilter, "Any write speed", "Under 180MB/s write speed", "180–285MB/s write speed", "285–520MB/s write speed", "Over 520MB/s write speed");
        options(capacityFilter, "Any capacity", "Up to 1TB", "2–4TB", "5–10TB", "Above 10TB");
        options(formFactorFilter, "Any form factor", "2.5-Inch", "3.5-Inch");
        options(rpmFilter, "Any RPM", "5400 RPM", "5640–5900 RPM", "7200 RPM", "Other RPM");
        options(sortBox, "Recommended", "Price: low to high", "Price: high to low", "Name: A to Z");
        sortBox.setOnAction(e -> applyFilters());
        compatibleOnlyFilter.setDisable(true);
    }
    private void options(ComboBox<String> box, String... values) { box.setItems(FXCollections.observableArrayList(values)); box.getSelectionModel().selectFirst(); }
    public Node getView() { return root; }
    public void setActions(Runnable back, Consumer<Part> add) { onBack = back; onAdd = add; }
    public void setCompareAction(Runnable action) { onCompare = action; }
    public void setParts(List<Part> parts) {
        motherboards = parts.stream().filter(p -> p.category().equalsIgnoreCase("Motherboard")).toList();
        hdds = parts.stream().filter(p -> p.category().equalsIgnoreCase("HDD")).toList();
        Set<String> brands = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        hdds.forEach(p -> { if (!value(p,"brand").isBlank()) brands.add(value(p,"brand")); });
        brandFilter.getItems().setAll("Any brand"); brandFilter.getItems().addAll(brands); brandFilter.getSelectionModel().selectFirst();
        applyFilters();
        if (hdds.isEmpty()) pageStatus.setText("No HDDs found in catalog/hdds.json. Reload local data from the build page.");
    }
    public void setSelectedMotherboard(Part board) {
        selectedMotherboard = board; compatibleOnlyFilter.setDisable(board == null);
        if (board == null) { compatibilityHint.setText("Choose a motherboard to enable compatibility filtering."); compatibleOnlyFilter.setSelected(false); }
        else compatibilityHint.setText("Selected motherboard: " + board.name() + " · SATA support and available ports will be checked.");
        applyFilters();
    }
    public void setSelectedHdd(Part hdd) { selectedHdd = hdd; renderPage(); }
    public void setSelectedSsd(Part ssd) { selectedSsd = ssd; applyFilters(); }
    public void setSearchQuery(String query) { searchField.setText(query == null ? "" : query); applyFilters(); }
    @FXML private void backToBuild() { onBack.run(); }
    @FXML private void compareComponents() { onCompare.run(); }
    @FXML private void clearFilters() {
        minPriceField.clear(); maxPriceField.clear(); searchField.clear();
        for (ComboBox<String> box : List.of(availabilityFilter,colorFilter,readSpeedFilter,writeSpeedFilter,brandFilter,capacityFilter,formFactorFilter,rpmFilter,sortBox)) box.getSelectionModel().selectFirst();
        compatibleOnlyFilter.setSelected(false); applyFilters();
    }
    @FXML private void applyFilters() {
        page = 0; Double min = parsePrice(minPriceField.getText()), max = parsePrice(maxPriceField.getText());
        if ((!minPriceField.getText().isBlank() && min == null) || (!maxPriceField.getText().isBlank() && max == null)) { pageStatus.setText("Enter valid numeric values for the price range."); return; }
        if (min != null && max != null && min > max) { pageStatus.setText("Minimum price cannot be greater than maximum price."); return; }
        String query = searchField.getText().trim().toLowerCase(Locale.ROOT);
        filtered = hdds.stream().filter(p -> min == null || p.price() >= min).filter(p -> max == null || p.price() <= max)
                .filter(p -> any(availabilityFilter) || selected(availabilityFilter).equalsIgnoreCase(value(p,"availability")))
                .filter(p -> any(colorFilter) || selected(colorFilter).equalsIgnoreCase(value(p,"color")))
                .filter(p -> speedMatches(number(p,"readSpeedMBps"), selected(readSpeedFilter), false))
                .filter(p -> speedMatches(number(p,"writeSpeedMBps"), selected(writeSpeedFilter), true))
                .filter(p -> any(brandFilter) || selected(brandFilter).equalsIgnoreCase(value(p,"brand")))
                .filter(p -> capacityMatches(number(p,"capacityGb"), selected(capacityFilter)))
                .filter(p -> any(formFactorFilter) || selected(formFactorFilter).equalsIgnoreCase(value(p,"formFactor")))
                .filter(p -> rpmMatches(number(p,"rotationSpeedRpm"), selected(rpmFilter)))
                .filter(p -> !compatibleOnlyFilter.isSelected() || compatibilityProblem(p).isBlank())
                .filter(p -> query.isBlank() || searchable(p).contains(query)).sorted(comparator(selected(sortBox))).toList();
        pageStatus.setText(hdds.isEmpty() ? "HDD catalog is empty." : "Local HDD catalog · Example BDT prices and availability for the course project.");
        renderPage();
    }
    @FXML private void previousPage() { if (page > 0) { page--; renderPage(); } }
    @FXML private void nextPage() { if ((page + 1) * PAGE_SIZE < filtered.size()) { page++; renderPage(); } }
    private void renderPage() {
        productRows.getChildren().clear(); resultCount.setText(filtered.size() + (filtered.size() == 1 ? " HDD" : " HDDs"));
        int pages = Math.max(1, (filtered.size() + PAGE_SIZE - 1) / PAGE_SIZE); page = Math.min(page, pages - 1);
        paginationLabel.setText("Page " + (page + 1) + " of " + pages); previousButton.setDisable(page == 0); nextButton.setDisable(page + 1 >= pages);
        for (Part part : filtered.subList(page * PAGE_SIZE, Math.min((page + 1) * PAGE_SIZE, filtered.size()))) productRows.getChildren().add(card(part));
    }
    private HBox card(Part part) {
        VBox detail = new VBox(6); Label name = new Label(part.name()); name.getStyleClass().add("processor-name"); name.setWrapText(true);
        Label specs = new Label(part.specs()); specs.getStyleClass().add("processor-specs"); specs.setWrapText(true);
        Label compat = new Label(compatibilityText(part)); compat.getStyleClass().add("compatibility-status");
        compat.getStyleClass().add(selectedMotherboard == null ? "compatibility-neutral" : compatibilityProblem(part).isBlank() ? "compatibility-ok" : "compatibility-error");
        detail.getChildren().addAll(name, specs, compat); HBox.setHgrow(detail, Priority.ALWAYS);
        VBox purchase = new VBox(10); purchase.setMinWidth(145); purchase.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        Label price = new Label(String.format("৳%,.0f", part.price())); price.getStyleClass().add("processor-price");
        boolean isChosen = selectedHdd != null && selectedHdd.name().equalsIgnoreCase(part.name());
        Button add = new Button(isChosen ? "Selected · Replace" : "Add to build"); add.getStyleClass().add(isChosen ? "button-primary" : "button-secondary"); add.setOnAction(e -> onAdd.accept(part));
        purchase.getChildren().addAll(price, add); HBox row = new HBox(18, detail, purchase); row.setAlignment(javafx.geometry.Pos.CENTER_LEFT); row.getStyleClass().add("processor-card"); return row;
    }
    private String compatibilityText(Part hdd) {
        if (selectedMotherboard == null) return "Select a motherboard to check SATA compatibility.";
        String issue = compatibilityProblem(hdd); return issue.isBlank() ? "Compatible with selected motherboard." : "Not compatible: " + issue;
    }
    private String compatibilityProblem(Part hdd) {
        if (selectedMotherboard == null) return "Motherboard is not selected.";
        Part board = selectedMotherboard;
        if (board.attribute("storageInterfaces").isBlank()) { String name = board.name(); board = motherboards.stream().filter(p -> p.name().equalsIgnoreCase(name)).findFirst().orElse(board); }
        String interfaces = value(board,"storageInterfaces").toUpperCase(Locale.ROOT);
        if (interfaces.isBlank()) return "motherboard storage-interface data is missing.";
        if (!interfaces.contains("SATA") || !value(hdd,"interface").equalsIgnoreCase("SATA")) return "this HDD requires SATA, which the motherboard does not list as supported.";
        int ports = number(board,"sataPorts");
        if (ports > 0) {
            int sataDevices = 1 + (selectedSsd != null && selectedSsd.attribute("interface").equalsIgnoreCase("SATA") ? 1 : 0);
            if (sataDevices > ports) return "motherboard has no free SATA port for this drive.";
        }
        return "";
    }
    private boolean any(ComboBox<String> box) { return selected(box).startsWith("Any "); }
    private String selected(ComboBox<String> box) { return box.getValue() == null ? "" : box.getValue(); }
    private boolean capacityMatches(int gb,String f) { return switch(f) { case "Up to 1TB" -> gb <= 1000; case "2–4TB" -> gb >= 2000 && gb <= 4000; case "5–10TB" -> gb >= 5000 && gb <= 10000; case "Above 10TB" -> gb > 10000; default -> true; }; }
    private boolean speedMatches(int n,String f,boolean write) { return switch(f) { case "Under 180MB/s", "Under 180MB/s write speed" -> n < 180; case "180–285MB/s", "180–285MB/s write speed" -> n >= 180 && n <= 285; case "285–500MB/s" -> n > 285 && n <= 500; case "285–520MB/s write speed" -> n > 285 && n <= 520; case "Over 500MB/s" -> n > 500; case "Over 520MB/s write speed" -> n > 520; default -> true; }; }
    private boolean rpmMatches(int n,String f) { return switch(f) { case "5400 RPM" -> n == 5400; case "5640–5900 RPM" -> n >= 5640 && n <= 5900; case "7200 RPM" -> n == 7200; case "Other RPM" -> n != 5400 && n != 5640 && n != 5900 && n != 7200; default -> true; }; }
    private String value(Part p,String key) { return p.attribute(key); }
    private int number(Part p,String key) { try { return Integer.parseInt(value(p,key)); } catch(Exception ignored) { return 0; } }
    private String searchable(Part p) { return (p.name()+" "+p.specs()+" "+String.join(" ",p.attributes().values())).toLowerCase(Locale.ROOT); }
    private Double parsePrice(String s) { if(s==null||s.isBlank())return null; try{return Double.parseDouble(s.replace(",","").trim());}catch(NumberFormatException e){return null;} }
    private Comparator<Part> comparator(String sort) { return switch(sort) { case "Price: low to high" -> Comparator.comparingDouble(Part::price); case "Price: high to low" -> Comparator.comparingDouble(Part::price).reversed(); case "Name: A to Z" -> Comparator.comparing(Part::name,String.CASE_INSENSITIVE_ORDER); default -> Comparator.comparing(Part::name,String.CASE_INSENSITIVE_ORDER); }; }
}
