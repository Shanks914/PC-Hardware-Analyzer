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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

/** Reusable, data-driven chooser for cooling, case, and accessory catalogs. */
public class CategoryChooserController {
    private static final int PAGE_SIZE = 10;
    private static final Map<String, Profile> PROFILES = Map.of(
            "cpu-cooler", new Profile("CPU Cooler", "CPU Coolers", List.of(
                    new Facet("PROCESSOR TYPE", "processorType"), new Facet("FAN SPEED", "fanSpeedRpm"),
                    new Facet("COOLER SIZE", "coolerSize"), new Facet("SOCKET", "socketSupport"),
                    new Facet("COLOR", "color"), new Facet("SPECIAL FEATURES", "specialFeatures")), true),
            "casing", new Profile("Casing", "PC Casings", List.of(
                    new Facet("CASE TYPE", "caseType"), new Facet("MOTHERBOARD SUPPORT", "motherboardSupport"),
                    new Facet("GPU CLEARANCE", "gpuClearanceClass"), new Facet("CPU COOLER CLEARANCE", "coolerClearanceClass"),
                    new Facet("COLOR", "color"), new Facet("INCLUDED FANS", "includedFans")), true),
            "casing-fan", new Profile("Casing Fan", "Casing Fans", List.of(
                    new Facet("FAN DIAMETER", "fanDiameter"), new Facet("COLOR", "color"), new Facet("NOISE LEVEL", "noiseClass"),
                    new Facet("MULTIPACK QUANTITY", "packCount"), new Facet("FAN SIZE", "fanSize"),
                    new Facet("LIGHTING TYPE", "lightingType"), new Facet("AIRFLOW VOLUME", "airflowClass"),
                    new Facet("RPM", "rpmClass"), new Facet("POWER CONNECTOR", "powerConnector")), true),
            "monitor", new Profile("Monitor", "Monitors", List.of(
                    new Facet("SCREEN SIZE", "screenSizeClass"), new Facet("RESOLUTION", "resolution"),
                    new Facet("PANEL TYPE", "panelType"), new Facet("REFRESH RATE", "refreshRateHz"),
                    new Facet("RESPONSE TIME", "responseTimeClass"), new Facet("DISPLAY TYPE", "displayType"),
                    new Facet("COLOR", "color")), false),
            "keyboard", new Profile("Keyboard", "Keyboards", List.of(
                    new Facet("CONNECTION", "connection"), new Facet("KEY TECHNOLOGY", "keyboardTechnology"),
                    new Facet("LAYOUT", "layout"), new Facet("SWITCH TYPE", "switchType"),
                    new Facet("LIGHTING TYPE", "lightingType"), new Facet("HOT SWAPPABLE", "hotSwappable"),
                    new Facet("COLOR", "color")), false),
            "mouse", new Profile("Mouse", "Mice", List.of(
                    new Facet("CONNECTION", "connection"), new Facet("SENSOR TYPE", "sensorType"),
                    new Facet("MAX DPI", "dpiClass"), new Facet("MOUSE TYPE", "mouseType"),
                    new Facet("POLLING RATE", "pollingRateHz"), new Facet("BUTTON COUNT", "buttonsClass"),
                    new Facet("COLOR", "color")), false),
            "ups", new Profile("UPS", "UPS Units", List.of(
                    new Facet("TECHNOLOGY", "technology"), new Facet("CAPACITY", "capacityClass"),
                    new Facet("OUTPUT WAVEFORM", "outputWaveform"), new Facet("BACKUP TIME", "backupTimeClass"),
                    new Facet("OUTLET COUNT", "outletCount")), false)
    );

    @FXML private BorderPane root;
    @FXML private Label pageTitle, filterTitle, resultsTitle, resultCount, compatibilityHint, pageStatus, paginationLabel;
    @FXML private TextField minPriceField, maxPriceField, searchField;
    @FXML private ComboBox<String> availabilityFilter, brandFilter, sortBox;
    @FXML private CheckBox compatibleOnlyFilter;
    @FXML private VBox facetContainer, productRows;
    @FXML private Button previousButton, nextButton;

    private List<Part> allParts = List.of(), categoryParts = List.of(), filtered = List.of();
    private final List<FacetControl> facetControls = new ArrayList<>();
    private Map<String, Part> build = Map.of();
    private Profile profile;
    private String slotKey;
    private String sharedSearch = "";
    private int page;
    private Runnable onBack = () -> {};
    private Consumer<Part> onAdd = part -> {};

    @FXML private void initialize() {
        options(availabilityFilter, "Any availability", "In stock", "Pre-order", "Out of stock", "Not specified");
        options(sortBox, "Recommended", "Price: low to high", "Price: high to low", "Name: A to Z");
        sortBox.setOnAction(event -> applyFilters());
        compatibleOnlyFilter.setDisable(true);
    }
    private void options(ComboBox<String> box, String... values) { box.setItems(FXCollections.observableArrayList(values)); box.getSelectionModel().selectFirst(); }
    public Node getView() { return root; }
    public void setActions(Runnable back, Consumer<Part> add) { onBack = back; onAdd = add; }
    public String getSelectedSlot() { return slotKey; }
    public void setParts(List<Part> parts) { allParts = List.copyOf(parts); if (profile != null) populateCategory(); }
    public void setSearchQuery(String query) { sharedSearch = query == null ? "" : query.trim(); if (searchField != null && profile != null) { searchField.setText(sharedSearch); applyFilters(); } }

    public void openChooser(String slot, Map<String, Part> currentBuild) {
        Profile next = PROFILES.get(slot);
        if (next == null) throw new IllegalArgumentException("No catalog chooser profile for slot " + slot);
        slotKey = slot; profile = next; build = enrichBuild(currentBuild);
        pageTitle.setText(profile.title()); filterTitle.setText("Filter " + profile.title().toLowerCase(Locale.ROOT));
        resultsTitle.setText(profile.plural()); searchField.setText(sharedSearch);
        compatibleOnlyFilter.setVisible(profile.compatibility()); compatibleOnlyFilter.setManaged(profile.compatibility());
        compatibleOnlyFilter.setSelected(false);
        buildFacetControls(); populateCategory(); updateCompatibilityHint();
    }
    private Map<String, Part> enrichBuild(Map<String, Part> currentBuild) {
        java.util.LinkedHashMap<String, Part> enriched = new java.util.LinkedHashMap<>();
        currentBuild.forEach((key, part) -> {
            Part catalogPart = part.attributes().isEmpty() ? allParts.stream()
                    .filter(candidate -> candidate.category().equalsIgnoreCase(part.category()))
                    .filter(candidate -> candidate.name().equalsIgnoreCase(part.name())).findFirst().orElse(part) : part;
            enriched.put(key, catalogPart);
        });
        return Map.copyOf(enriched);
    }
    private void populateCategory() {
        categoryParts = allParts.stream().filter(part -> part.category().equalsIgnoreCase(profile.category())).toList();
        populateValues(brandFilter, "Any brand", categoryParts.stream().map(part -> part.attribute("brand")).toList());
        for (FacetControl facet : facetControls) {
            List<String> values = new ArrayList<>();
            for (Part part : categoryParts) {
                String raw = part.attribute(facet.spec().key());
                if (raw.isBlank()) continue;
                if (isMultiValue(facet.spec().key())) values.addAll(splitValues(raw)); else values.add(raw);
            }
            populateValues(facet.combo(), "Any " + facet.spec().label().toLowerCase(Locale.ROOT), values);
        }
        applyFilters();
        if (categoryParts.isEmpty()) pageStatus.setText("No " + profile.plural().toLowerCase(Locale.ROOT) + " loaded. Check the local JSON catalog and reload.");
    }
    private void buildFacetControls() {
        facetContainer.getChildren().clear(); facetControls.clear();
        for (Facet facet : profile.facets()) {
            Label label = new Label(facet.label()); label.getStyleClass().add("section-label");
            ComboBox<String> combo = new ComboBox<>(); combo.setMaxWidth(Double.MAX_VALUE);
            facetContainer.getChildren().addAll(label, combo); facetControls.add(new FacetControl(facet, combo));
        }
    }
    private void populateValues(ComboBox<String> combo, String anyLabel, List<String> values) {
        Set<String> unique = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        values.stream().filter(value -> value != null && !value.isBlank()).forEach(unique::add);
        combo.getItems().setAll(anyLabel); combo.getItems().addAll(unique); combo.getSelectionModel().selectFirst();
    }
    private boolean isMultiValue(String key) { return Set.of("processorType", "socketSupport", "specialFeatures", "motherboardSupport", "supportedFanDiameters").contains(key); }
    private List<String> splitValues(String value) { return List.of(value.split("\\|")); }

    @FXML private void backToBuild() { onBack.run(); }
    @FXML private void clearFilters() {
        minPriceField.clear(); maxPriceField.clear(); availabilityFilter.getSelectionModel().selectFirst();
        brandFilter.getSelectionModel().selectFirst(); searchField.clear(); sharedSearch = ""; sortBox.getSelectionModel().selectFirst();
        facetControls.forEach(control -> control.combo().getSelectionModel().selectFirst());
        compatibleOnlyFilter.setSelected(false); applyFilters();
    }
    @FXML private void applyFilters() {
        if (profile == null) return;
        page = 0; Double minimum = parsePrice(minPriceField.getText()), maximum = parsePrice(maxPriceField.getText());
        if ((!minPriceField.getText().isBlank() && minimum == null) || (!maxPriceField.getText().isBlank() && maximum == null)) { pageStatus.setText("Enter valid numeric values for the price range."); return; }
        if (minimum != null && maximum != null && minimum > maximum) { pageStatus.setText("Minimum price cannot be greater than maximum price."); return; }
        String query = searchField.getText().trim().toLowerCase(Locale.ROOT); sharedSearch = query;
        filtered = categoryParts.stream().filter(part -> minimum == null || part.price() >= minimum)
                .filter(part -> maximum == null || part.price() <= maximum)
                .filter(part -> selected(availabilityFilter).startsWith("Any ") || part.attribute("availability").equalsIgnoreCase(selected(availabilityFilter)))
                .filter(part -> selected(brandFilter).startsWith("Any ") || part.attribute("brand").equalsIgnoreCase(selected(brandFilter)))
                .filter(this::matchesFacets)
                .filter(part -> !compatibleOnlyFilter.isSelected() || compatibilityProblem(part).isBlank())
                .filter(part -> query.isBlank() || searchable(part).contains(query))
                .sorted(comparator(selected(sortBox))).toList();
        pageStatus.setText(categoryParts.isEmpty() ? "Local catalog is empty." : "Local " + profile.plural().toLowerCase(Locale.ROOT) + " · Example BDT prices and availability for the course project.");
        renderPage();
    }
    private boolean matchesFacets(Part part) {
        for (FacetControl control : facetControls) {
            String filter = selected(control.combo());
            if (filter.startsWith("Any ")) continue;
            String value = part.attribute(control.spec().key());
            if (isMultiValue(control.spec().key())) {
                if (splitValues(value).stream().noneMatch(item -> item.trim().equalsIgnoreCase(filter))) return false;
            } else if (!value.equalsIgnoreCase(filter)) return false;
        }
        return true;
    }
    @FXML private void previousPage() { if (page > 0) { page--; renderPage(); } }
    @FXML private void nextPage() { if ((page + 1) * PAGE_SIZE < filtered.size()) { page++; renderPage(); } }
    private void renderPage() {
        productRows.getChildren().clear(); resultCount.setText(filtered.size() + (filtered.size() == 1 ? " product" : " products"));
        int pages = Math.max(1, (filtered.size() + PAGE_SIZE - 1) / PAGE_SIZE); page = Math.min(page, pages - 1);
        paginationLabel.setText("Page " + (page + 1) + " of " + pages); previousButton.setDisable(page == 0); nextButton.setDisable(page + 1 >= pages);
        for (Part part : filtered.subList(page * PAGE_SIZE, Math.min((page + 1) * PAGE_SIZE, filtered.size()))) productRows.getChildren().add(card(part));
    }
    private HBox card(Part part) {
        VBox details = new VBox(6); Label name = new Label(part.name()); name.getStyleClass().add("processor-name"); name.setWrapText(true);
        Label specs = new Label(part.specs()); specs.getStyleClass().add("processor-specs"); specs.setWrapText(true);
        Label compatibility = new Label(compatibilityText(part)); compatibility.getStyleClass().add("compatibility-status");
        compatibility.getStyleClass().add(profile.compatibility() ? (hasDependency() ? (compatibilityProblem(part).isBlank() ? "compatibility-ok" : "compatibility-error") : "compatibility-neutral") : "compatibility-neutral");
        details.getChildren().addAll(name, specs, compatibility); HBox.setHgrow(details, Priority.ALWAYS);
        VBox buy = new VBox(10); buy.setMinWidth(145); buy.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        Label price = new Label(String.format("৳%,.0f", part.price())); price.getStyleClass().add("processor-price");
        Part selected = build.get(slotKey); boolean chosen = selected != null && selected.name().equalsIgnoreCase(part.name());
        Button add = new Button(chosen ? "Selected · Replace" : "Add to build"); add.getStyleClass().add(chosen ? "button-primary" : "button-secondary"); add.setOnAction(event -> onAdd.accept(part));
        buy.getChildren().addAll(price, add); HBox row = new HBox(18, details, buy); row.setAlignment(javafx.geometry.Pos.CENTER_LEFT); row.getStyleClass().add("processor-card"); return row;
    }
    private void updateCompatibilityHint() {
        if (!profile.compatibility()) { compatibilityHint.setText("Choose products with search and filters, then add your selection to the build."); return; }
        String dependency = dependencyName();
        compatibleOnlyFilter.setDisable(!hasDependency());
        if (hasDependency()) compatibilityHint.setText("Selected " + dependency + ": compatibility is checked against this component.");
        else { compatibilityHint.setText("Select a " + dependency + " on the build page to enable compatibility filtering."); compatibleOnlyFilter.setSelected(false); }
    }
    private boolean hasDependency() { return switch (slotKey) { case "cpu-cooler" -> build.get("processor") != null; case "casing" -> build.get("motherboard") != null; case "casing-fan" -> build.get("casing") != null; default -> false; }; }
    private String dependencyName() { return switch (slotKey) { case "cpu-cooler" -> "processor"; case "casing" -> "motherboard"; case "casing-fan" -> "casing"; default -> "component"; }; }
    private String compatibilityText(Part part) {
        if (!profile.compatibility()) return "Catalog selection · " + part.attribute("availability");
        if (!hasDependency()) return "Select a " + dependencyName() + " to check compatibility.";
        String issue = compatibilityProblem(part); return issue.isBlank() ? "Compatible with selected " + dependencyName() + "." : "Not compatible: " + issue;
    }
    private String compatibilityProblem(Part part) {
        if (slotKey.equals("cpu-cooler")) {
            Part cpu = build.get("processor"); if (cpu == null) return "select a processor first.";
            String socket = cpu.attribute("socket");
            if (!socket.isBlank() && splitValues(part.attribute("socketSupport")).stream().noneMatch(s -> normalizeSocket(s).equals(normalizeSocket(socket)))) return "cooler does not support the processor socket " + socket + ".";
            int cpuTdp = integer(cpu,"tdpWatts"), rated = integer(part,"ratedTdpW");
            if (cpuTdp > 0 && rated > 0 && rated < cpuTdp) return "cooler is rated for " + rated + "W, below the CPU's " + cpuTdp + "W TDP.";
        } else if (slotKey.equals("casing")) {
            Part board = build.get("motherboard");
            if (board != null && !part.attribute("motherboardSupport").isBlank()
                    && splitValues(part.attribute("motherboardSupport")).stream().noneMatch(f -> normalizeForm(f).equals(normalizeForm(board.attribute("formFactor"))))) return "case does not support the motherboard form factor " + board.attribute("formFactor") + ".";
            Part gpu = build.get("graphics"); int gpuLength = integer(gpu,"lengthMm"), gpuSpace = integer(part,"gpuMaxLengthMm");
            if (gpuLength > 0 && gpuSpace > 0 && gpuLength > gpuSpace) return "case GPU clearance is " + gpuSpace + "mm, but the selected graphics card is " + gpuLength + "mm long.";
            Part cooler = build.get("cpu-cooler"); int coolerHeight = integer(cooler,"coolerHeightMm"), coolerSpace = integer(part,"maxCpuCoolerHeightMm");
            if (coolerHeight > 0 && coolerSpace > 0 && coolerHeight > coolerSpace) return "case cooler clearance is " + coolerSpace + "mm, but the selected cooler is " + coolerHeight + "mm high.";
        } else if (slotKey.equals("casing-fan")) {
            Part pcCase = build.get("casing");
            if (pcCase != null && !pcCase.attribute("supportedFanDiameters").isBlank()
                    && splitValues(pcCase.attribute("supportedFanDiameters")).stream().noneMatch(size -> size.equalsIgnoreCase(part.attribute("fanDiameter")))) return "case does not list a mount for " + part.attribute("fanDiameter") + " fans.";
        }
        return "";
    }
    private int integer(Part part,String key) { if(part==null)return 0; try{return Integer.parseInt(part.attribute(key));}catch(NumberFormatException ignored){return 0;} }
    private String normalizeSocket(String value) { return value.replaceAll("(?i)^AMD\\s+", "").replaceAll("\\s+", "").toUpperCase(Locale.ROOT); }
    private String normalizeForm(String value) { return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "").replace("matx", "microatx"); }
    private String selected(ComboBox<String> box) { return box.getValue() == null ? "" : box.getValue(); }
    private String searchable(Part part) { return (part.name()+" "+part.specs()+" "+String.join(" ",part.attributes().values())).toLowerCase(Locale.ROOT); }
    private Double parsePrice(String value) { if(value==null||value.isBlank())return null; try{return Double.parseDouble(value.replace(",","").trim());}catch(NumberFormatException e){return null;} }
    private Comparator<Part> comparator(String sort) { return switch(sort) { case "Price: low to high" -> Comparator.comparingDouble(Part::price); case "Price: high to low" -> Comparator.comparingDouble(Part::price).reversed(); case "Name: A to Z" -> Comparator.comparing(Part::name,String.CASE_INSENSITIVE_ORDER); default -> Comparator.comparing(Part::name,String.CASE_INSENSITIVE_ORDER); }; }
    private record Facet(String label,String key) {}
    private record FacetControl(Facet spec,ComboBox<String> combo) {}
    private record Profile(String category,String title,List<Facet> facets,boolean compatibility) { String plural() { return title; } }
}
