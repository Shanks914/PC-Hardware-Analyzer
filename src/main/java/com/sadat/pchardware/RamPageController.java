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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

public class RamPageController {
    private static final int PAGE_SIZE = 10;

    @FXML private BorderPane root;
    @FXML private TextField minPriceField;
    @FXML private TextField maxPriceField;
    @FXML private ComboBox<String> availabilityFilter;
    @FXML private ComboBox<String> brandFilter;
    @FXML private ComboBox<String> speedFilter;
    @FXML private ComboBox<String> ramTypeFilter;
    @FXML private ComboBox<String> capacityFilter;
    @FXML private ComboBox<String> featureFilter;
    @FXML private CheckBox compatibleOnlyFilter;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> sortBox;
    @FXML private VBox productRows;
    @FXML private Label resultCount;
    @FXML private Label compatibilityHint;
    @FXML private Label pageStatus;
    @FXML private Label paginationLabel;
    @FXML private Button previousButton;
    @FXML private Button nextButton;

    private List<Part> memoryKits = List.of();
    private List<Part> filtered = List.of();
    private Part selectedMotherboard;
    private Part selectedMemory;
    private Consumer<Part> onAdd = part -> {};
    private Runnable onBack = () -> {};
    private int page;

    @FXML
    private void initialize() {
        availabilityFilter.setItems(FXCollections.observableArrayList("Any availability", "In stock", "Pre-order", "Out of stock", "Not specified"));
        availabilityFilter.getSelectionModel().selectFirst();
        brandFilter.getItems().setAll("Any brand");
        brandFilter.getSelectionModel().selectFirst();
        speedFilter.getItems().setAll("Any speed");
        speedFilter.getSelectionModel().selectFirst();
        ramTypeFilter.setItems(FXCollections.observableArrayList("Any RAM type"));
        ramTypeFilter.getSelectionModel().selectFirst();
        capacityFilter.setItems(FXCollections.observableArrayList("Any capacity"));
        capacityFilter.getSelectionModel().selectFirst();
        featureFilter.getItems().setAll("Any feature");
        featureFilter.getSelectionModel().selectFirst();
        sortBox.setItems(FXCollections.observableArrayList("Recommended", "Price: low to high", "Price: high to low", "Name: A to Z"));
        sortBox.getSelectionModel().selectFirst();
        sortBox.setOnAction(event -> applyFilters());
        compatibleOnlyFilter.setDisable(true);
    }

    public Node getView() { return root; }

    public void setActions(Runnable back, Consumer<Part> add) {
        onBack = back;
        onAdd = add;
    }

    public void setParts(List<Part> parts) {
        memoryKits = parts.stream().filter(part -> part.category().equalsIgnoreCase("RAM")).toList();
        Set<String> brands = new LinkedHashSet<>();
        Set<String> availabilities = new LinkedHashSet<>();
        Set<String> speeds = new LinkedHashSet<>();
        Set<String> types = new LinkedHashSet<>();
        Set<String> capacities = new LinkedHashSet<>();
        Set<String> features = new LinkedHashSet<>();
        memoryKits.stream().map(part -> part.attribute("brand").isBlank() ? "Other" : part.attribute("brand"))
                .distinct().sorted(String.CASE_INSENSITIVE_ORDER).forEach(brands::add);
        memoryKits.stream().map(part -> part.attribute("availability")).filter(value -> !value.isBlank())
                .distinct().sorted(String.CASE_INSENSITIVE_ORDER).forEach(availabilities::add);
        memoryKits.stream().map(part -> part.attribute("speedMhz")).filter(value -> !value.isBlank())
                .distinct().sorted(Comparator.comparingInt(this::parseNumber)).forEach(value -> speeds.add(value + " MHz"));
        memoryKits.stream().map(part -> part.attribute("ramType")).filter(value -> !value.isBlank())
                .distinct().sorted(String.CASE_INSENSITIVE_ORDER).forEach(types::add);
        memoryKits.stream().map(part -> part.attribute("capacityGb")).filter(value -> !value.isBlank())
                .distinct().sorted(Comparator.comparingInt(this::parseNumber)).forEach(value -> capacities.add(value + "GB"));
        memoryKits.stream().map(part -> part.attribute("features")).filter(value -> !value.isBlank())
                .flatMap(value -> java.util.Arrays.stream(value.split("\\|"))).map(String::trim)
                .filter(value -> !value.isBlank()).distinct().sorted(String.CASE_INSENSITIVE_ORDER).forEach(features::add);
        brandFilter.getItems().setAll("Any brand");
        brandFilter.getItems().addAll(brands);
        availabilityFilter.getItems().setAll("Any availability");
        availabilityFilter.getItems().addAll(availabilities);
        speedFilter.getItems().setAll("Any speed");
        speedFilter.getItems().addAll(speeds);
        ramTypeFilter.getItems().setAll("Any RAM type");
        ramTypeFilter.getItems().addAll(types);
        capacityFilter.getItems().setAll("Any capacity");
        capacityFilter.getItems().addAll(capacities);
        featureFilter.getItems().setAll("Any feature");
        featureFilter.getItems().addAll(features);
        brandFilter.getSelectionModel().selectFirst();
        availabilityFilter.getSelectionModel().selectFirst();
        speedFilter.getSelectionModel().selectFirst();
        ramTypeFilter.getSelectionModel().selectFirst();
        capacityFilter.getSelectionModel().selectFirst();
        featureFilter.getSelectionModel().selectFirst();
        page = 0;
        applyFilters();
        if (memoryKits.isEmpty()) pageStatus.setText("No desktop RAM kits found in the local catalog. Reload local data on the build page.");
    }

    public void setSelectedMotherboard(Part motherboard) {
        selectedMotherboard = motherboard;
        compatibleOnlyFilter.setDisable(motherboard == null);
        if (motherboard == null) {
            compatibleOnlyFilter.setSelected(false);
            compatibilityHint.setText("Choose a motherboard to enable compatibility filtering.");
        } else {
            compatibilityHint.setText("Selected motherboard: " + motherboard.name() + " · "
                    + fallbackAttribute(motherboard, "ramType", "RAM type not specified") + " · "
                    + fallbackAttribute(motherboard, "maxMemoryGb", "maximum capacity not specified") + "GB maximum · "
                    + fallbackAttribute(motherboard, "memorySlots", "slot count not specified") + " slots.");
        }
        applyFilters();
    }

    public void setSelectedMemory(Part memory) {
        selectedMemory = memory;
        renderPage();
    }

    public void setSearchQuery(String query) {
        searchField.setText(query == null ? "" : query);
        applyFilters();
    }

    @FXML private void backToBuild() { onBack.run(); }

    @FXML
    private void clearFilters() {
        minPriceField.clear();
        maxPriceField.clear();
        availabilityFilter.getSelectionModel().selectFirst();
        brandFilter.getSelectionModel().selectFirst();
        speedFilter.getSelectionModel().selectFirst();
        ramTypeFilter.getSelectionModel().selectFirst();
        capacityFilter.getSelectionModel().selectFirst();
        featureFilter.getSelectionModel().selectFirst();
        compatibleOnlyFilter.setSelected(false);
        searchField.clear();
        sortBox.getSelectionModel().selectFirst();
        applyFilters();
    }

    @FXML
    private void applyFilters() {
        page = 0;
        Double min = parsePrice(minPriceField.getText());
        Double max = parsePrice(maxPriceField.getText());
        if ((!minPriceField.getText().isBlank() && min == null) || (!maxPriceField.getText().isBlank() && max == null)) {
            pageStatus.setText("Enter valid numeric values for the price range.");
            return;
        }
        if (min != null && max != null && min > max) {
            pageStatus.setText("Minimum price cannot be greater than maximum price.");
            return;
        }
        String query = searchField.getText().trim().toLowerCase(Locale.ROOT);
        String availability = availabilityFilter.getValue();
        String brand = brandFilter.getValue();
        String speed = speedFilter.getValue();
        String ramType = ramTypeFilter.getValue();
        String capacity = capacityFilter.getValue();
        String feature = featureFilter.getValue();
        filtered = memoryKits.stream()
                .filter(part -> min == null || part.price() >= min)
                .filter(part -> max == null || part.price() <= max)
                .filter(part -> "Any availability".equals(availability) || availability.equalsIgnoreCase(availabilityOf(part)))
                .filter(part -> "Any brand".equals(brand) || brand.equalsIgnoreCase(value(part, "brand")))
                .filter(part -> "Any speed".equals(speed) || numericAttribute(part, "speedMhz") == parseNumber(speed))
                .filter(part -> "Any RAM type".equals(ramType) || ramType.equalsIgnoreCase(value(part, "ramType")))
                .filter(part -> "Any capacity".equals(capacity) || numericAttribute(part, "capacityGb") == parseNumber(capacity))
                .filter(part -> "Any feature".equals(feature) || java.util.Arrays.stream(features(part).split("\\|"))
                        .anyMatch(value -> value.trim().equalsIgnoreCase(feature)))
                .filter(part -> !compatibleOnlyFilter.isSelected() || isCompatibleWithMotherboard(part, selectedMotherboard))
                .filter(part -> query.isBlank() || searchText(part).contains(query))
                .sorted(comparator(sortBox.getValue()))
                .toList();
        pageStatus.setText(filtered.isEmpty() ? "No RAM kits match these filters." : "Showing local desktop RAM kits. Price and stock values are project catalog examples.");
        renderPage();
    }

    @FXML private void previousPage() { if (page > 0) { page--; renderPage(); } }
    @FXML private void nextPage() { if ((page + 1) * PAGE_SIZE < filtered.size()) { page++; renderPage(); } }

    private void renderPage() {
        productRows.getChildren().clear();
        resultCount.setText(filtered.size() + (filtered.size() == 1 ? " memory kit" : " memory kits"));
        int pages = Math.max(1, (filtered.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.min(page, pages - 1);
        paginationLabel.setText("Page " + (page + 1) + " of " + pages);
        previousButton.setDisable(page == 0);
        nextButton.setDisable(page + 1 >= pages);
        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, filtered.size());
        for (Part part : filtered.subList(start, end)) productRows.getChildren().add(createProductCard(part));
    }

    private HBox createProductCard(Part part) {
        VBox details = new VBox(6);
        Label name = new Label(part.name());
        name.getStyleClass().add("processor-name");
        name.setWrapText(true);
        Label detailsLabel = new Label(value(part, "capacityGb") + "GB · " + value(part, "ramType") + " · "
                + value(part, "speedMhz") + "MHz · " + value(part, "modules") + " module(s)");
        detailsLabel.getStyleClass().add("muted-label");
        Label specs = new Label(part.specs().isBlank() ? "No specifications supplied" : part.specs());
        specs.getStyleClass().add("processor-specs");
        specs.setWrapText(true);
        Label compatibility = new Label(compatibilityFor(part));
        compatibility.getStyleClass().add("compatibility-status");
        compatibility.getStyleClass().add(compatibilityClass(part));
        details.getChildren().addAll(name, detailsLabel, specs, compatibility);
        HBox.setHgrow(details, Priority.ALWAYS);

        VBox purchase = new VBox(10);
        purchase.setMinWidth(145);
        purchase.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        Label price = new Label(String.format("৳%,.0f", part.price()));
        price.getStyleClass().add("processor-price");
        boolean isSelected = selectedMemory != null && samePart(selectedMemory, part);
        Button add = new Button(isSelected ? "Selected · Replace" : "Add to build");
        add.getStyleClass().add(isSelected ? "button-primary" : "button-secondary");
        add.setOnAction(event -> onAdd.accept(part));
        purchase.getChildren().addAll(price, add);
        HBox card = new HBox(18, details, purchase);
        card.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        card.getStyleClass().add("processor-card");
        return card;
    }

    private String compatibilityFor(Part memory) {
        if (selectedMotherboard == null) return "Select a motherboard to check RAM compatibility.";
        String problem = compatibilityProblem(memory, selectedMotherboard);
        return problem.isBlank() ? "Compatible with the selected motherboard." : "Not compatible: " + problem;
    }

    private String compatibilityClass(Part memory) {
        if (selectedMotherboard == null) return "compatibility-neutral";
        return compatibilityProblem(memory, selectedMotherboard).isBlank() ? "compatibility-ok" : "compatibility-error";
    }

    private boolean isCompatibleWithMotherboard(Part memory, Part motherboard) {
        return motherboard != null && compatibilityProblem(memory, motherboard).isBlank();
    }

    private String compatibilityProblem(Part memory, Part motherboard) {
        if (motherboard == null) return "Motherboard is not selected.";
        String boardType = value(motherboard, "ramType");
        String memoryType = value(memory, "ramType");
        if (boardType.isBlank() || memoryType.isBlank()) return "RAM type data is missing.";
        if (!boardType.equalsIgnoreCase(memoryType)) return "motherboard uses " + boardType + " but this kit is " + memoryType + ".";

        int capacity = numericAttribute(memory, "capacityGb");
        int maxCapacity = numericAttribute(motherboard, "maxMemoryGb");
        if (capacity <= 0 || maxCapacity <= 0) return "capacity data is missing.";
        if (capacity > maxCapacity) return "kit capacity " + capacity + "GB exceeds the motherboard maximum of " + maxCapacity + "GB.";

        int modules = numericAttribute(memory, "modules");
        int slots = numericAttribute(motherboard, "memorySlots");
        if (modules <= 0 || slots <= 0) return "memory slot or module-count data is missing.";
        if (modules > slots) return "this kit has " + modules + " modules but the motherboard has only " + slots + " memory slots.";
        return "";
    }

    private String availabilityOf(Part part) {
        return part.attribute("availability").isBlank() ? "Not specified" : part.attribute("availability");
    }

    private String features(Part part) { return part.attribute("features"); }
    private String value(Part part, String key) {
        String attribute = part.attribute(key);
        if (!attribute.isBlank()) return attribute;
        String expression = switch (key) {
            case "ramType" -> "(?i)\\bDDR[345]\\b";
            case "capacityGb" -> "(?i)(\\d+)\\s*GB\\b";
            case "maxMemoryGb" -> "(?i)(?:up to|max(?:imum)?(?: memory)?)\\s*(\\d+)\\s*GB\\b";
            case "modules" -> "(?i)(\\d+)\\s*[x×]";
            case "memorySlots" -> "(?i)(\\d+)\\s*(?:memory\\s+)?slots?\\b";
            case "speedMhz" -> "(?i)(\\d+)\\s*MHz\\b";
            default -> "";
        };
        if (expression.isBlank()) return "";
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(expression).matcher(part.specs());
        return matcher.find() ? (matcher.groupCount() > 0 ? matcher.group(1) : matcher.group()) : "";
    }

    private String fallbackAttribute(Part part, String key, String fallback) {
        String value = value(part, key);
        return value.isBlank() ? fallback : value;
    }

    private String searchText(Part part) {
        return (part.name() + " " + part.specs() + " " + String.join(" ", part.attributes().values())).toLowerCase(Locale.ROOT);
    }

    private int numericAttribute(Part part, String key) {
        try { return Integer.parseInt(value(part, key)); }
        catch (NumberFormatException e) { return 0; }
    }

    private int parseNumber(String value) {
        try { return Integer.parseInt(value.replaceAll("[^0-9]", "")); }
        catch (NumberFormatException e) { return -1; }
    }

    private Double parsePrice(String text) {
        if (text == null || text.isBlank()) return null;
        try { return Double.parseDouble(text.replace(",", "").trim()); }
        catch (NumberFormatException e) { return null; }
    }

    private Comparator<Part> comparator(String sort) {
        if (sort == null) return (first, second) -> 0;
        return switch (sort) {
            case "Price: low to high" -> Comparator.comparingDouble(Part::price);
            case "Price: high to low" -> Comparator.comparingDouble(Part::price).reversed();
            case "Name: A to Z" -> Comparator.comparing(Part::name, String.CASE_INSENSITIVE_ORDER);
            default -> (first, second) -> 0;
        };
    }

    private boolean samePart(Part first, Part second) {
        return first.name().equalsIgnoreCase(second.name()) && first.category().equalsIgnoreCase(second.category());
    }
}
