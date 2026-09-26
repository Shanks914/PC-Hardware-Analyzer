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

public class ProcessorPageController {
    private static final int PAGE_SIZE = 10;
    private static final Pattern SOCKET_PATTERN = Pattern.compile("(?i)\\b(AM\\s?\\d+|LGA\\s?-?\\s?\\d{4,5}|TR\\d+)\\b");
    private static final Pattern CORES_PATTERN = Pattern.compile("(?i)(\\d+)\\s*(?:physical\\s+)?cores?\\b");
    private static final Pattern THREADS_PATTERN = Pattern.compile("(?i)(\\d+)\\s*threads?\\b");
    private static final Pattern CLOCK_PATTERN = Pattern.compile("(?i)(\\d+(?:\\.\\d+)?)\\s*GHz\\b");

    @FXML private BorderPane root;
    @FXML private TextField minPriceField;
    @FXML private TextField maxPriceField;
    @FXML private ComboBox<String> availabilityFilter;
    @FXML private CheckBox amdFilter;
    @FXML private CheckBox intelFilter;
    @FXML private ComboBox<String> socketFilter;
    @FXML private ComboBox<String> coresFilter;
    @FXML private ComboBox<String> threadsFilter;
    @FXML private ComboBox<String> clockFilter;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> sortBox;
    @FXML private VBox productRows;
    @FXML private Label resultCount;
    @FXML private Label pageStatus;
    @FXML private Label paginationLabel;
    @FXML private Button previousButton;
    @FXML private Button nextButton;

    private List<Part> processors = List.of();
    private List<Part> filtered = List.of();
    private Part selected;
    private Consumer<Part> onAdd = part -> {};
    private Runnable onBack = () -> {};
    private int page;

    @FXML
    private void initialize() {
        availabilityFilter.setItems(FXCollections.observableArrayList(
                "Any availability", "In stock", "Pre-order", "Out of stock", "Not specified"));
        availabilityFilter.getSelectionModel().selectFirst();
        coresFilter.setItems(FXCollections.observableArrayList("Any cores", "2", "4", "6", "8", "10", "12", "14", "16", "18", "20", "24", "32", "64"));
        coresFilter.getSelectionModel().selectFirst();
        threadsFilter.setItems(FXCollections.observableArrayList("Any threads", "4", "8", "12", "16", "20", "24", "28", "32", "48", "64", "96", "128"));
        threadsFilter.getSelectionModel().selectFirst();
        clockFilter.setItems(FXCollections.observableArrayList("Any clock speed", "Up to 2.4 GHz", "2.5–3.0 GHz", "3.1–3.5 GHz", "3.6–4.0 GHz", "4.1–4.5 GHz", "4.6–5.0 GHz", "Above 5.0 GHz"));
        clockFilter.getSelectionModel().selectFirst();
        socketFilter.getItems().setAll("Any socket");
        socketFilter.getSelectionModel().selectFirst();
        sortBox.setItems(FXCollections.observableArrayList("Recommended", "Price: low to high", "Price: high to low", "Name: A to Z"));
        sortBox.getSelectionModel().selectFirst();
        sortBox.setOnAction(event -> applyFilters());
    }

    public Node getView() {
        return root;
    }

    public void setActions(Runnable back, Consumer<Part> add) {
        onBack = back;
        onAdd = add;
    }

    public void setParts(List<Part> parts) {
        processors = parts.stream()
                .filter(part -> part.category().equalsIgnoreCase("CPU") || part.category().equalsIgnoreCase("Processor"))
                .toList();
        Set<String> sockets = new LinkedHashSet<>();
        processors.stream().map(this::socketOf).filter(value -> !value.isBlank()).sorted().forEach(sockets::add);
        socketFilter.getItems().setAll("Any socket");
        socketFilter.getItems().addAll(sockets);
        socketFilter.getSelectionModel().selectFirst();
        page = 0;
        applyFilters();
        if (processors.isEmpty()) pageStatus.setText("No processors found in the local catalog. Use Reload local data on the build page.");
    }

    public void setSelected(Part selected) {
        this.selected = selected;
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
        amdFilter.setSelected(false);
        intelFilter.setSelected(false);
        socketFilter.getSelectionModel().selectFirst();
        coresFilter.getSelectionModel().selectFirst();
        threadsFilter.getSelectionModel().selectFirst();
        clockFilter.getSelectionModel().selectFirst();
        searchField.clear();
        sortBox.getSelectionModel().selectFirst();
        applyFilters();
    }

    @FXML
    private void applyFilters() {
        page = 0;
        Double min = parsePrice(minPriceField.getText());
        Double max = parsePrice(maxPriceField.getText());
        if ((!minPriceField.getText().isBlank() && min == null)
                || (!maxPriceField.getText().isBlank() && max == null)) {
            pageStatus.setText("Enter valid numeric values for the price range.");
            return;
        }
        if (min != null && max != null && min > max) {
            pageStatus.setText("Minimum price cannot be greater than maximum price.");
            return;
        }
        String query = searchField.getText().trim().toLowerCase(Locale.ROOT);
        boolean brandRestricted = amdFilter.isSelected() || intelFilter.isSelected();
        String availability = availabilityFilter.getValue();
        String socket = socketFilter.getValue();
        String cores = coresFilter.getValue();
        String threads = threadsFilter.getValue();
        String clock = clockFilter.getValue();

        filtered = processors.stream()
                .filter(part -> min == null || part.price() >= min)
                .filter(part -> max == null || part.price() <= max)
                .filter(part -> !brandRestricted || (amdFilter.isSelected() && searchText(part).contains("amd"))
                        || (intelFilter.isSelected() && searchText(part).contains("intel")))
                .filter(part -> "Any socket".equals(socket) || socket.equalsIgnoreCase(socketOf(part)))
                .filter(part -> "Any cores".equals(cores) || cores.equals(coreCount(part)))
                .filter(part -> "Any threads".equals(threads) || threads.equals(threadCount(part)))
                .filter(part -> matchesClock(part, clock))
                .filter(part -> matchesAvailability(part, availability))
                .filter(part -> query.isBlank() || searchText(part).contains(query))
                .sorted(comparator(sortBox.getValue()))
                .toList();
        pageStatus.setText(filtered.isEmpty() ? "No processors match these filters." : "Showing matching catalog processors. Stock details are only available when supplied in the catalog.");
        renderPage();
    }

    @FXML private void previousPage() { if (page > 0) { page--; renderPage(); } }
    @FXML private void nextPage() { if ((page + 1) * PAGE_SIZE < filtered.size()) { page++; renderPage(); } }

    private void renderPage() {
        productRows.getChildren().clear();
        resultCount.setText(filtered.size() + (filtered.size() == 1 ? " processor" : " processors"));
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
        Label type = new Label("Processor  ·  " + availabilityOf(part));
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
        Button add = new Button(selected != null && samePart(selected, part) ? "Selected · Replace" : "Add to build");
        add.getStyleClass().add(selected != null && samePart(selected, part) ? "button-primary" : "button-secondary");
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
        String specs = searchText(part);
        if (Pattern.compile("\\b(out of stock|sold out|unavailable)\\b").matcher(specs).find()) return "Out of stock";
        if (Pattern.compile("\\b(pre[ -]?order|preorder)\\b").matcher(specs).find()) return "Pre-order";
        if (Pattern.compile("\\b(in stock|available)\\b").matcher(specs).find()) return "In stock";
        return "Not specified";
    }

    private String socketOf(Part part) {
        if (!part.attribute("socket").isBlank()) return part.attribute("socket").replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        Matcher matcher = SOCKET_PATTERN.matcher(searchText(part));
        return matcher.find() ? matcher.group(1).replaceAll("\\s+", "").toUpperCase(Locale.ROOT) : "";
    }

    private String coreCount(Part part) { return part.attribute("cores").isBlank() ? findNumber(CORES_PATTERN, part) : part.attribute("cores"); }
    private String threadCount(Part part) { return part.attribute("threads").isBlank() ? findNumber(THREADS_PATTERN, part) : part.attribute("threads"); }

    private String findNumber(Pattern pattern, Part part) {
        Matcher matcher = pattern.matcher(searchText(part));
        return matcher.find() ? matcher.group(1) : "";
    }

    private boolean matchesClock(Part part, String filter) {
        if (filter == null || filter.equals("Any clock speed")) return true;
        String boostClock = part.attribute("boostClockGHz");
        if (!boostClock.isBlank()) {
            double ghz = Double.parseDouble(boostClock);
            return clockRangeMatches(ghz, filter);
        }
        Matcher matcher = CLOCK_PATTERN.matcher(searchText(part));
        if (!matcher.find()) return false;
        double ghz = Double.parseDouble(matcher.group(1));
        return clockRangeMatches(ghz, filter);
    }

    private boolean clockRangeMatches(double ghz, String filter) {
        return switch (filter) {
            case "Up to 2.4 GHz" -> ghz <= 2.4;
            case "2.5–3.0 GHz" -> ghz >= 2.5 && ghz <= 3.0;
            case "3.1–3.5 GHz" -> ghz >= 3.1 && ghz <= 3.5;
            case "3.6–4.0 GHz" -> ghz >= 3.6 && ghz <= 4.0;
            case "4.1–4.5 GHz" -> ghz >= 4.1 && ghz <= 4.5;
            case "4.6–5.0 GHz" -> ghz >= 4.6 && ghz <= 5.0;
            case "Above 5.0 GHz" -> ghz > 5.0;
            default -> true;
        };
    }

    private Comparator<Part> comparator(String sort) {
        if (sort == null) return Comparator.comparing(Part::name, String.CASE_INSENSITIVE_ORDER);
        return switch (sort) {
            case "Price: low to high" -> Comparator.comparingDouble(Part::price);
            case "Price: high to low" -> Comparator.comparingDouble(Part::price).reversed();
            case "Name: A to Z" -> Comparator.comparing(Part::name, String.CASE_INSENSITIVE_ORDER);
            default -> (first, second) -> 0;
        };
    }

    private String searchText(Part part) {
        return (part.name() + " " + part.specs() + " " + String.join(" ", part.attributes().values())).toLowerCase(Locale.ROOT);
    }

    private Double parsePrice(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            return Double.parseDouble(text.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean samePart(Part first, Part second) {
        return first.name().equalsIgnoreCase(second.name()) && first.category().equalsIgnoreCase(second.category());
    }
}
