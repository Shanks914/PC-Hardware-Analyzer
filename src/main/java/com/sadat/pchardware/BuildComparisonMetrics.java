package com.sadat.pchardware;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shared estimates used by side-by-side component and saved-build comparisons. */
public final class BuildComparisonMetrics {
    private static final Pattern WATTS = Pattern.compile("(?i)(\\d{1,4})\\s*W\\b");

    private BuildComparisonMetrics() { }

    public static int estimateBuildWatts(List<Part> parts) {
        if (parts.isEmpty()) return 0;
        int watts = 50; // motherboard, chipset and baseline system draw
        for (Part part : parts) watts += estimatePartWatts(part);
        return ((watts + 24) / 25) * 25;
    }

    public static int estimatePartWatts(Part part) {
        String category = part.category().toLowerCase(Locale.ROOT);
        if (category.contains("psu") || category.contains("ups")) return 0;
        int stated = numeric(part.attribute("powerDrawW"));
        if (stated == 0) stated = numeric(part.attribute("tdpWatts"));
        if (stated == 0 && (category.contains("cpu") || category.contains("processor") || category.contains("ram")
                || category.contains("memory") || category.contains("fan")) && !category.contains("cooler")) {
            stated = firstNumber(part.specs(), part.name());
        }
        if (category.contains("cooler")) return stated > 0 ? stated : 5;
        if (category.contains("fan")) return stated > 0 ? stated : 3;
        if (category.contains("cpu") || category.contains("processor")) return stated > 0 ? stated : 95;
        if (category.equals("gpu") || category.contains("graphics")) return stated > 0 ? stated : 250;
        if (category.contains("ram") || category.contains("memory")) return stated > 0 ? stated : 10;
        if (category.contains("ssd") || category.contains("hdd") || category.contains("storage")) return stated > 0 ? stated : 10;
        if (category.contains("motherboard")) return stated > 0 ? stated : 40;
        if (category.contains("monitor")) return stated > 0 ? stated : 30;
        return stated;
    }

    private static int firstNumber(String... candidates) {
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) continue;
            Matcher matcher = WATTS.matcher(candidate);
            if (matcher.find()) {
                try { return Integer.parseInt(matcher.group(1)); }
                catch (NumberFormatException ignored) { }
            }
            if (candidate.matches("\\d{1,4}")) {
                try { return Integer.parseInt(candidate); }
                catch (NumberFormatException ignored) { }
            }
        }
        return 0;
    }

    private static int numeric(String value) {
        try { return value == null || value.isBlank() ? 0 : Integer.parseInt(value.trim()); }
        catch (NumberFormatException ignored) { return 0; }
    }
}
