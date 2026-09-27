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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MotherboardPageController {
    private static final int PAGE_SIZE = 10;
    private static final Pattern SOCKET_PATTERN = Pattern.compile("(?i)\\b(AM\\s?\\d+|LGA\\s?-?\\s?\\d{4,5}|TR\\s?\\d+)\\b");
    private static final Pattern CHIPSET_PATTERN = Pattern.compile("(?i)\\b([ABHXZ]\\d{3})[A-Z]?\\b");
    private static final Pattern FORM_FACTOR_PATTERN = Pattern.compile("(?i)\\b(MINI[- ]?ITX|MICRO[- ]?ATX|M[- ]?ATX|E[- ]?ATX|ATX|BTF)\\b");
    private static final Pattern RAM_PATTERN = Pattern.compile("(?i)\\b(DDR[345])\\b");
    private static final List<String> BRANDS = List.of("ASUS", "GIGABYTE", "MSI", "ASROCK", "BIOSTAR", "COLORFUL", "NZXT", "AFOX", "ARKTEK", "UNIKA", "MAXSUN", "REVENGER", "XENTHRA", "SUPER");

    @FXML private BorderPane root;
    @FXML private TextField minPriceField;
    @FXML private TextField maxPriceField;
    @FXML private ComboBox<String> availabilityFilter;
    @FXML private ComboBox<String> brandFilter;
    @FXML private ComboBox<String> platformFilter;
    @FXML private ComboBox<String> socketFilter;
    @FXML private CheckBox compatibleOnlyFilter;
    @FXML private ComboBox<String> chipsetFilter;
    @FXML private ComboBox<String> formFactorFilter;
    @FXML private ComboBox<String> ramTypeFilter;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> sortBox;
    @FXML private VBox productRows;
    @FXML private Label resultCount;
    @FXML private Label compatibilityHint;
    @FXML private Label pageStatus;
    @FXML private Label paginationLabel;
    @FXML private Button previousButton;
    @FXML private Button nextButton;

    private List<Part> motherboards = List.of();
    private List<Part> filtered = List.of();
    private Part selectedMotherboard;
    private Part selectedProcessor;
    private Consumer<Part> onAdd = part -> {};
    private Runnable onBack = () -> {};
    private Runnable onCompare = () -> {};
    private int page;

    @FXML
    private void initialize() {
        availabilityFilter.setItems(FXCollections.observableArrayList("Any availability", "In stock", "Pre-order", "Out of stock", "Not specified"));
        availabilityFilter.getSelectionModel().selectFirst();
        platformFilter.setItems(FXCollections.observableArrayList("Any platform", "AMD", "Intel"));
        platformFilter.getSelectionModel().selectFirst();
        brandFilter.getItems().setAll("Any brand");
        socketFilter.getItems().setAll("Any socket");
        chipsetFilter.getItems().setAll("Any chipset");
        formFactorFilter.setItems(FXCollections.observableArrayList("Any form factor", "ATX", "Micro ATX", "Mini ITX", "Extended ATX", "BTF"));
        formFactorFilter.getSelectionModel().selectFirst();
        ramTypeFilter.setItems(FXCollections.observableArrayList("Any RAM type", "DDR3", "DDR4", "DDR5"));
        ramTypeFilter.getSelectionModel().selectFirst();
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
    public void setCompareAction(Runnable action) { onCompare = action; }

    public void setParts(List<Part> parts) {
        motherboards = parts.stream()
                .filter(part -> {
                    String category = part.category().toLowerCase(Locale.ROOT);
                    return category.contains("motherboard") || category.equals("mobo");
                })
                .toList();
        updateDynamicFilters();
        page = 0;
        applyFilters();
        if (motherboards.isEmpty()) pageStatus.setText("No motherboards found in the local catalog. Use Reload local data on the build page.");
    }

    public void setSelectedProcessor(Part processor) {
        selectedProcessor = processor;
        String socket = processor == null ? "" : socketOf(processor);
        compatibleOnlyFilter.setDisable(socket.isBlank());
        compatibilityHint.setText(socket.isBlank()
                ? "Choose a processor with socket information to enable socket matching."
                : "Selected processor socket: " + socket + " · Enable matching in the filters to narrow results.");
        if (socket.isBlank()) compatibleOnlyFilter.setSelected(false);
        applyFilters();
    }

    public void setSelectedMotherboard(Part motherboard) {
        selectedMotherboard = motherboard;
        renderPage();
    }

    public void setSearchQuery(String query) {
        searchField.setText(query == null ? "" : query);
        applyFilters();
    }

    @FXML private void backToBuild() { onBack.run(); }
    @FXML private void compareComponents() { onCompare.run(); }

    @FXML
    private void clearFilters() {
        minPriceField.clear();
        maxPriceField.clear();
        availabilityFilter.getSelectionModel().selectFirst();
        brandFilter.getSelectionModel().selectFirst();
        platformFilter.getSelectionModel().selectFirst();
        socketFilter.getSelectionModel().selectFirst();
        compatibleOnlyFilter.setSelected(false);
        chipsetFilter.getSelectionModel().selectFirst();
        formFactorFilter.getSelectionModel().selectFirst();
        ramTypeFilter.getSelectionModel().selectFirst();
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
        String platform = platformFilter.getValue();
        String socket = socketFilter.getValue();
        String chipset = chipsetFilter.getValue();
        String formFactor = formFactorFilter.getValue();
        String ramType = ramTypeFilter.getValue();
        String cpuSocket = selectedProcessor == null ? "" : socketOf(selectedProcessor);

        filtered = motherboards.stream()
                .filter(part -> min == null || part.price() >= min)
                .filter(part -> max == null || part.price() <= max)
                .filter(part -> "Any brand".equals(brand) || brand.equalsIgnoreCase(brandOf(part)))
                .filter(part -> "Any platform".equals(platform) || platform.equalsIgnoreCase(platformOf(part)))
                .filter(part -> "Any socket".equals(socket) || socket.equalsIgnoreCase(socketOf(part)))
                .filter(part -> "Any chipset".equals(chipset) || chipset.equalsIgnoreCase(chipsetOf(part)))
                .filter(part -> "Any form factor".equals(formFactor) || normalizeFormFactor(formFactor).equalsIgnoreCase(formFactorOf(part)))
                .filter(part -> "Any RAM type".equals(ramType) || ramType.equalsIgnoreCase(ramTypeOf(part)))
                .filter(part -> !compatibleOnlyFilter.isSelected() || (!cpuSocket.isBlank() && cpuSocket.equalsIgnoreCase(socketOf(part))))
                .filter(part -> matchesAvailability(part, availability))
                .filter(part -> query.isBlank() || searchText(part).contains(query))
                .sorted(comparator(sortBox.getValue()))
                .toList();
        pageStatus.setText(filtered.isEmpty() ? "No motherboards match these filters." : "Showing matching catalog motherboards. Stock details are only available when supplied in the catalog.");
        renderPage();
    }

    @FXML private void previousPage() { if (page > 0) { page--; renderPage(); } }
    @FXML private void nextPage() { if ((page + 1) * PAGE_SIZE < filtered.size()) { page++; renderPage(); } }

    private void updateDynamicFilters() {
        Set<String> brands = new LinkedHashSet<>();
        Set<String> sockets = new LinkedHashSet<>();
        Set<String> chipsets = new LinkedHashSet<>();
        motherboards.stream().map(this::brandOf).filter(value -> !value.isBlank()).sorted().forEach(brands::add);
        motherboards.stream().map(this::socketOf).filter(value -> !value.isBlank()).sorted().forEach(sockets::add);
        motherboards.stream().map(this::chipsetOf).filter(value -> !value.isBlank()).sorted().forEach(chipsets::add);
        brandFilter.getItems().setAll("Any brand");
        brandFilter.getItems().addAll(brands);
        brandFilter.getSelectionModel().selectFirst();
        socketFilter.getItems().setAll("Any socket");
        socketFilter.getItems().addAll(sockets);
        socketFilter.getSelectionModel().selectFirst();
        chipsetFilter.getItems().setAll("Any chipset");
        chipsetFilter.getItems().addAll(chipsets);
        chipsetFilter.getSelectionModel().selectFirst();
    }

    private void renderPage() {
        productRows.getChildren().clear();
        resultCount.setText(filtered.size() + (filtered.size() == 1 ? " motherboard" : " motherboards"));
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
        String detailsLine = java.util.stream.Stream.of(socketOf(part), formFactorOf(part), ramTypeOf(part))
                .filter(value -> !value.isBlank()).collect(java.util.stream.Collectors.joining("  ·  "));
        Label type = new Label("Motherboard" + (detailsLine.isBlank() ? "" : "  ·  " + detailsLine));
        type.getStyleClass().add("muted-label");
        Label specs = new Label(part.specs().isBlank() ? "No specifications supplied" : part.specs());
        specs.getStyleClass().add("processor-specs");
        specs.setWrapText(true);
        details.getChildren().addAll(name, type, specs);
        HBox.setHgrow(details, Priority.ALWAYS);

        VBox purchase = new VBox(10);
        purchase.setMinWidth(145);
        purchase.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        Label price = new Label(String.format("৳%,.0f", part.price()));
        price.getStyleClass().add("processor-price");
        boolean isSelected = selectedMotherboard != null && samePart(selectedMotherboard, part);
        Button add = new Button(isSelected ? "Selected · Replace" : "Add to build");
        add.getStyleClass().add(isSelected ? "button-primary" : "button-secondary");
        add.setOnAction(event -> onAdd.accept(part));
        purchase.getChildren().addAll(price, add);

        HBox card = new HBox(18, details, purchase);
        card.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        card.getStyleClass().add("processor-card");
        return card;
    }

    private boolean matchesAvailability(Part part, String filter) {
        if (filter == null || filter.equals("Any availability")) return true;
        return availabilityOf(part).equalsIgnoreCase(filter);
    }

    private String availabilityOf(Part part) {
        if (!part.attribute("availability").isBlank()) return part.attribute("availability");
        String text = searchText(part);
        if (Pattern.compile("\\b(out of stock|sold out|unavailable)\\b").matcher(text).find()) return "Out of stock";
        if (Pattern.compile("\\b(pre[ -]?order|preorder)\\b").matcher(text).find()) return "Pre-order";
        if (Pattern.compile("\\b(in stock|available)\\b").matcher(text).find()) return "In stock";
        return "Not specified";
    }

    private String brandOf(Part part) {
        if (!part.attribute("brand").isBlank()) return part.attribute("brand").toUpperCase(Locale.ROOT);
        String text = searchText(part).toUpperCase(Locale.ROOT);
        for (String brand : BRANDS) if (text.contains(brand)) return brand.equals("SUPER") ? "SUPERMICRO" : brand;
        String first = part.name().trim().split("\\s+")[0];
        return first.matches("(?i)[A-Z][A-Z0-9-]{2,}") ? first.toUpperCase(Locale.ROOT) : "";
    }

    private String platformOf(Part part) {
        if (!part.attribute("platform").isBlank()) return part.attribute("platform");
        String socket = socketOf(part);
        if (socket.startsWith("AM") || socket.startsWith("TR")) return "AMD";
        if (socket.startsWith("LGA")) return "Intel";
        String text = searchText(part);
        if (text.contains("amd")) return "AMD";
        if (text.contains("intel") || text.contains("intl")) return "Intel";
        return "Unknown";
    }

    private String socketOf(Part part) {
        if (!part.attribute("socket").isBlank()) return part.attribute("socket").replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        Matcher matcher = SOCKET_PATTERN.matcher(searchText(part));
        return matcher.find() ? matcher.group(1).replaceAll("\\s+", "").toUpperCase(Locale.ROOT) : "";
    }

    private String chipsetOf(Part part) {
        if (!part.attribute("chipset").isBlank()) return part.attribute("chipset").toUpperCase(Locale.ROOT);
        Matcher matcher = CHIPSET_PATTERN.matcher(searchText(part));
        return matcher.find() ? matcher.group(1).toUpperCase(Locale.ROOT) : "";
    }

    private String formFactorOf(Part part) {
        if (!part.attribute("formFactor").isBlank()) return normalizeFormFactor(part.attribute("formFactor"));
        Matcher matcher = FORM_FACTOR_PATTERN.matcher(searchText(part));
        if (!matcher.find()) return "";
        return normalizeFormFactor(matcher.group(1));
    }

    private String normalizeFormFactor(String formFactor) {
        String value = formFactor.toUpperCase(Locale.ROOT).replaceAll("[ _-]+", " ").trim();
        return switch (value) {
            case "MATX", "M ATX", "MICRO ATX" -> "Micro ATX";
            case "MINIITX", "MINI ITX" -> "Mini ITX";
            case "EATX", "E ATX", "EXTENDED ATX" -> "Extended ATX";
            default -> value;
        };
    }

    private String ramTypeOf(Part part) {
        if (!part.attribute("ramType").isBlank()) return part.attribute("ramType").toUpperCase(Locale.ROOT);
        Matcher matcher = RAM_PATTERN.matcher(searchText(part));
        return matcher.find() ? matcher.group(1).toUpperCase(Locale.ROOT) : "";
    }

    private String searchText(Part part) { return (part.name() + " " + part.specs() + " " + String.join(" ", part.attributes().values())).toLowerCase(Locale.ROOT); }

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
