package com.sadat.pchardware;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reusable compare screen for a component category or two SQLite-saved builds. */
public class ComparisonPageController {
    @FXML private BorderPane root;
    @FXML private Label pageTitle, subtitle, recommendationLabel, statusLabel;
    @FXML private VBox partSelectors, buildSelectors;
    @FXML private ComboBox<Part> partA, partB;
    @FXML private ComboBox<SavedBuild> buildA, buildB;
    @FXML private GridPane comparisonGrid;

    private Runnable onBack = () -> { };
    private boolean comparingBuilds;
    private String slotKey;
    private Map<String, Part> currentBuild = Map.of();

    @FXML private void initialize() { }

    public Node getView() { return root; }
    public void setBackAction(Runnable action) { onBack = action == null ? () -> { } : action; }
    @FXML private void goBack() { onBack.run(); }

    public void showParts(String slot, String title, List<Part> choices, Map<String, Part> build) {
        comparingBuilds = false;
        slotKey = slot;
        currentBuild = Map.copyOf(build);
        pageTitle.setText("Compare " + title);
        subtitle.setText("Choose two " + title.toLowerCase(Locale.ROOT) + " and compare price, specifications, compatibility, and estimated power.");
        partSelectors.setVisible(true);
        partSelectors.setManaged(true);
        buildSelectors.setVisible(false);
        buildSelectors.setManaged(false);
        List<Part> sorted = choices.stream().sorted(Comparator.comparing(Part::name, String.CASE_INSENSITIVE_ORDER)).toList();
        partA.setItems(FXCollections.observableArrayList(sorted));
        partB.setItems(FXCollections.observableArrayList(sorted));
        Part current = build.get(slot);
        partA.getSelectionModel().select(current != null && sorted.contains(current) ? current : (sorted.isEmpty() ? null : sorted.get(0)));
        partB.getSelectionModel().select(sorted.size() > 1 ? sorted.get(1) : (sorted.isEmpty() ? null : sorted.get(0)));
        clearResults("Select two different products, then press Compare.");
    }

    public void showBuilds(List<SavedBuild> builds) {
        comparingBuilds = true;
        pageTitle.setText("Compare saved builds");
        subtitle.setText("Compare each saved component, item prices, estimated system power, and total cost.");
        partSelectors.setVisible(false);
        partSelectors.setManaged(false);
        buildSelectors.setVisible(true);
        buildSelectors.setManaged(true);
        List<SavedBuild> options = List.copyOf(builds);
        buildA.setItems(FXCollections.observableArrayList(options));
        buildB.setItems(FXCollections.observableArrayList(options));
        buildA.getSelectionModel().select(options.isEmpty() ? null : options.get(0));
        buildB.getSelectionModel().select(options.size() > 1 ? options.get(1) : (options.isEmpty() ? null : options.get(0)));
        clearResults(options.size() < 2 ? "Save at least two builds to compare them." : "Select two different saved builds, then press Compare.");
    }

    @FXML private void compare() {
        if (comparingBuilds) compareSavedBuilds(); else compareParts();
    }

    private void compareParts() {
        Part first = partA.getValue(), second = partB.getValue();
        if (first == null || second == null) { clearResults("The catalog does not have enough products for this comparison."); return; }
        if (samePart(first, second)) { clearResults("Choose two different products to compare."); return; }

        Map<String, Part> firstBuild = withCandidate(first), secondBuild = withCandidate(second);
        BuildCompatibility.Result firstCompatibility = BuildCompatibility.check(firstBuild);
        BuildCompatibility.Result secondCompatibility = BuildCompatibility.check(secondBuild);
        int firstWatts = BuildComparisonMetrics.estimatePartWatts(first);
        int secondWatts = BuildComparisonMetrics.estimatePartWatts(second);
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        keys.addAll(first.attributes().keySet()); keys.addAll(second.attributes().keySet());
        List<String> orderedKeys = keys.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();

        resetGrid(first.name(), second.name());
        int row = 1;
        addRow(row++, "Price", money(first.price()), money(second.price()), compareLower(first.price(), second.price()));
        addRow(row++, "Power / capacity", powerDisplay(first, firstWatts), powerDisplay(second, secondWatts),
                firstWatts == 0 || secondWatts == 0 ? "" : compareLower(firstWatts, secondWatts));
        addRow(row++, "Availability", value(first, "availability"), value(second, "availability"), "");
        addRow(row++, "Compatibility with current build", compatibilityText(firstCompatibility), compatibilityText(secondCompatibility),
                compatibilityPreference(firstCompatibility, secondCompatibility));
        for (String key : orderedKeys) {
            String a = value(first, key), b = value(second, key);
            addRow(row++, pretty(key), a.isBlank() ? "—" : a, b.isBlank() ? "—" : b, "");
        }
        String bottleneck = pairingAdvice(firstBuild, secondBuild, first, second);
        recommendationLabel.setText(recommend(first, second, firstCompatibility, secondCompatibility, bottleneck));
        statusLabel.setText("Compatibility is checked against the current build. CPU/GPU bottleneck guidance uses catalog price tiers as a rough hint, not benchmark data.");
    }

    private void compareSavedBuilds() {
        SavedBuild first = buildA.getValue(), second = buildB.getValue();
        if (first == null || second == null) { clearResults("Save at least two builds to compare them."); return; }
        if (first.id() == second.id()) { clearResults("Choose two different saved builds."); return; }
        Map<String, Part> left = bySlot(first.parts()), right = bySlot(second.parts());
        LinkedHashMap<String, String> categories = new LinkedHashMap<>();
        first.parts().forEach(p -> categories.putIfAbsent(slotFor(p), displayCategory(p)));
        second.parts().forEach(p -> categories.putIfAbsent(slotFor(p), displayCategory(p)));
        List<Part> firstParts = first.parts(), secondParts = second.parts();
        int wattsA = BuildComparisonMetrics.estimateBuildWatts(firstParts);
        int wattsB = BuildComparisonMetrics.estimateBuildWatts(secondParts);
        resetGrid(first.name(), second.name());
        int row = 1;
        addRow(row++, "Estimated total price", money(total(firstParts)), money(total(secondParts)), compareLower(total(firstParts), total(secondParts)));
        addRow(row++, "Estimated system wattage", wattsA + " W", wattsB + " W", compareLower(wattsA, wattsB));
        for (Map.Entry<String, String> category : categories.entrySet()) {
            Part a = left.get(category.getKey()), b = right.get(category.getKey());
            addRow(row++, category.getValue(), a == null ? "Not selected" : a.name(), b == null ? "Not selected" : b.name(), "");
            if (a != null || b != null) {
                addRow(row++, "  Specs", a == null ? "—" : a.specs(), b == null ? "—" : b.specs(), "");
                addRow(row++, "  Price", a == null ? "—" : money(a.price()), b == null ? "—" : money(b.price()),
                        a == null || b == null ? "" : compareLower(a.price(), b.price()));
                addRow(row++, "  Power / capacity", a == null ? "—" : powerDisplay(a, BuildComparisonMetrics.estimatePartWatts(a)),
                        b == null ? "—" : powerDisplay(b, BuildComparisonMetrics.estimatePartWatts(b)), "");
            }
        }
        recommendationLabel.setText("Lower total cost: " + (total(firstParts) <= total(secondParts) ? first.name() : second.name())
                + " · Lower estimated power: " + (wattsA <= wattsB ? first.name() : second.name())
                + ". Choose based on the components and totals that best fit your needs.");
        statusLabel.setText("Power is an approximate catalog-based estimate; PSU capacity is not added to system consumption.");
    }

    private Map<String, Part> withCandidate(Part candidate) {
        LinkedHashMap<String, Part> result = new LinkedHashMap<>(currentBuild);
        result.put(slotKey, candidate);
        return result;
    }

    private String pairingAdvice(Map<String, Part> firstBuild, Map<String, Part> secondBuild, Part first, Part second) {
        if (!slotKey.equals("processor") && !slotKey.equals("graphics")) return "";
        Part cpuA = firstBuild.get("processor"), gpuA = firstBuild.get("graphics");
        Part cpuB = secondBuild.get("processor"), gpuB = secondBuild.get("graphics");
        if (cpuA == null || gpuA == null || cpuB == null || gpuB == null) return "";
        String a = balance(cpuA, gpuA), b = balance(cpuB, gpuB);
        return "CPU/GPU pairing estimate — " + first.name() + ": " + a + "; " + second.name() + ": " + b + ".";
    }

    private String balance(Part cpu, Part gpu) {
        double cpuPrice = Math.max(1, cpu.price()), gpuPrice = Math.max(1, gpu.price());
        double ratio = gpuPrice / cpuPrice;
        if (ratio > 3.0) return "higher GPU price tier; possible CPU-limited pairing for some workloads";
        if (ratio < 0.55) return "higher CPU price tier; possible GPU-limited pairing for graphics workloads";
        return "CPU/GPU price tiers are reasonably balanced";
    }

    private String recommend(Part first, Part second, BuildCompatibility.Result a, BuildCompatibility.Result b, String pairing) {
        String preferred;
        double firstScore = performanceScore(first), secondScore = performanceScore(second);
        if (a.state() == BuildCompatibility.State.INCOMPATIBLE && b.state() != BuildCompatibility.State.INCOMPATIBLE) preferred = second.name() + " (the first option conflicts with the current build)";
        else if (b.state() == BuildCompatibility.State.INCOMPATIBLE && a.state() != BuildCompatibility.State.INCOMPATIBLE) preferred = first.name() + " (the second option conflicts with the current build)";
        else if (firstScore > secondScore * 1.12) preferred = first.name() + " (stronger listed capacity/performance specifications)";
        else if (secondScore > firstScore * 1.12) preferred = second.name() + " (stronger listed capacity/performance specifications)";
        else if (first.price() != second.price()) {
            boolean complete = a.state() == BuildCompatibility.State.COMPATIBLE && b.state() == BuildCompatibility.State.COMPATIBLE;
            boolean comparableTier = firstScore > 0 && secondScore > 0;
            preferred = (first.price() < second.price() ? first.name() : second.name())
                    + (comparableTier
                    ? (complete ? " (similar listed tier at a lower price; both pass compatibility checks)"
                    : " (similar listed tier at a lower price; some compatibility checks need more selected components)")
                    : " (lower price; compare the feature trade-offs and compatibility notes above)");
        } else preferred = "No clear winner from the available catalog data; compare the specifications and workload fit above.";
        return "Suggested fit: " + preferred + (pairing.isBlank() ? "" : "\n" + pairing);
    }

    private double performanceScore(Part part) {
        return switch (slotKey) {
            case "processor" -> number(part, "cores") * 2.0 + number(part, "threads")
                    + number(part, "boostClockGHz") * 3.0;
            case "graphics" -> number(part, "memorySizeGb") * 3.0 + number(part, "pcieGeneration")
                    + number(part, "powerDrawW") / 75.0;
            case "memory" -> number(part, "capacityGb") * 2.0 + number(part, "speedMhz") / 1000.0;
            case "storage-ssd", "storage-hdd" -> number(part, "capacityGb") / 100.0
                    + number(part, "maxReadMBps") / 1000.0 + number(part, "rpm") / 10000.0;
            case "motherboard" -> number(part, "maxMemoryGb") / 8.0 + number(part, "m2Slots") * 2.0
                    + number(part, "sataPorts") / 2.0 + number(part, "memorySlots");
            case "power" -> number(part, "wattage") / 100.0;
            default -> 0;
        };
    }

    private double number(Part part, String key) {
        try { return Double.parseDouble(part.attribute(key)); }
        catch (NumberFormatException ignored) { return 0; }
    }

    private String compatibilityText(BuildCompatibility.Result result) {
        return switch (result.state()) {
            case INCOMPATIBLE -> "Incompatible — " + result.message();
            case COMPATIBLE -> "Compatible — " + result.message();
            case UNKNOWN, NEUTRAL -> "Not fully verifiable — " + result.message();
        };
    }

    private String compatibilityPreference(BuildCompatibility.Result first, BuildCompatibility.Result second) {
        if (first.state() == BuildCompatibility.State.INCOMPATIBLE && second.state() != BuildCompatibility.State.INCOMPATIBLE) return "Second fits";
        if (second.state() == BuildCompatibility.State.INCOMPATIBLE && first.state() != BuildCompatibility.State.INCOMPATIBLE) return "First fits";
        return "";
    }

    private void resetGrid(String first, String second) {
        comparisonGrid.getChildren().clear();
        addHeader(0, "Specification", "Comparison");
        addHeader(1, first, "Product A");
        addHeader(2, second, "Product B");
        addHeader(3, "Better fit", "Result");
    }

    private void addHeader(int column, String title, String fallback) {
        Label label = new Label(title == null || title.isBlank() ? fallback : title);
        label.getStyleClass().add("compare-table-header");
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(label, javafx.scene.layout.Priority.ALWAYS);
        comparisonGrid.add(label, column, 0);
    }

    private void addRow(int row, String metric, String a, String b, String better) {
        addCell(metric, "compare-metric", 0, row);
        addCell(a, "compare-value", 1, row);
        addCell(b, "compare-value", 2, row);
        addCell(better, "compare-result", 3, row);
    }

    private void addCell(String text, String style, int col, int row) {
        Label label = new Label(text == null || text.isBlank() ? "—" : text);
        label.getStyleClass().add(style);
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        comparisonGrid.add(label, col, row);
    }

    private void clearResults(String message) {
        comparisonGrid.getChildren().clear();
        recommendationLabel.setText(message);
        statusLabel.setText("");
    }

    private Map<String, Part> bySlot(List<Part> parts) {
        LinkedHashMap<String, Part> map = new LinkedHashMap<>();
        parts.forEach(part -> map.put(slotFor(part), part));
        return map;
    }

    private String slotFor(Part part) {
        return switch (part.category().toLowerCase(Locale.ROOT)) {
            case "cpu", "processor" -> "processor";
            case "motherboard", "mobo" -> "motherboard";
            case "ram", "memory" -> "memory";
            case "ssd" -> "storage-ssd";
            case "hdd" -> "storage-hdd";
            case "storage" -> part.specs().toLowerCase(Locale.ROOT).contains("hdd") ? "storage-hdd" : "storage-ssd";
            case "gpu", "graphics" -> "graphics";
            case "psu", "power supply" -> "power";
            case "cpu cooler" -> "cpu-cooler";
            case "casing" -> "casing";
            case "casing fan" -> "casing-fan";
            default -> part.category().toLowerCase(Locale.ROOT);
        };
    }

    private String displayCategory(Part part) {
        return switch (slotFor(part)) {
            case "processor" -> "Processor";
            case "motherboard" -> "Motherboard";
            case "memory" -> "Desktop RAM";
            case "storage-ssd" -> "SSD";
            case "storage-hdd" -> "HDD";
            case "graphics" -> "GPU";
            case "power" -> "Power Supply";
            case "cpu-cooler" -> "CPU Cooler";
            case "casing-fan" -> "Casing Fan";
            default -> part.category();
        };
    }

    private boolean samePart(Part a, Part b) { return a.category().equalsIgnoreCase(b.category()) && a.name().equalsIgnoreCase(b.name()); }
    private double total(List<Part> parts) { return parts.stream().mapToDouble(Part::price).sum(); }
    private String money(double amount) { return String.format("৳%,.0f", amount); }
    private String powerDisplay(Part part, int estimate) {
        if (part.category().equalsIgnoreCase("PSU")) {
            Matcher rating = Pattern.compile("(?i)(\\d{2,4})\\s*W\\b").matcher(part.name() + " " + part.specs());
            return rating.find() ? rating.group(1) + " W rated capacity" : "Capacity in specifications";
        }
        if (part.category().equalsIgnoreCase("UPS")) return "UPS capacity; not system draw";
        return estimate + " W estimated draw";
    }
    private String compareLower(double a, double b) { return a < b ? "First" : a > b ? "Second" : "Equal"; }
    private String compareLower(int a, int b) { return a < b ? "First" : a > b ? "Second" : "Equal"; }
    private String value(Part part, String key) { return part.attributes().getOrDefault(key, ""); }
    private String pretty(String key) {
        String spaced = key.replaceAll("([a-z])([A-Z])", "$1 $2").replaceAll("([A-Z])([A-Z][a-z])", "$1 $2");
        return spaced.isEmpty() ? spaced : spaced.substring(0, 1).toUpperCase(Locale.ROOT) + spaced.substring(1);
    }
}
