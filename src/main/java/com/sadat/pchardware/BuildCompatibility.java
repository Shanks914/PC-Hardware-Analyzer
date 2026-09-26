package com.sadat.pchardware;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BuildCompatibility {
    private static final Pattern SOCKET_PATTERN = Pattern.compile("(?i)\\b(AM\\s?\\d+|LGA\\s?-?\\s?\\d{4,5}|TR\\s?\\d+)\\b");
    private static final Pattern RAM_PATTERN = Pattern.compile("(?i)\\bDDR[345]\\b");

    private BuildCompatibility() {}

    public enum State { NEUTRAL, COMPATIBLE, INCOMPATIBLE, UNKNOWN }

    public record Result(State state, String message) {}

    public static Result check(Map<String, Part> build) {
        Part cpu = build.get("processor");
        Part motherboard = build.get("motherboard");
        Part ram = build.get("memory");
        if (cpu == null && motherboard == null) {
            return new Result(State.NEUTRAL, "Select a processor and motherboard to check compatibility.");
        }
        if (cpu == null) return new Result(State.NEUTRAL, "Select a processor to check the motherboard socket.");
        if (motherboard == null) return new Result(State.NEUTRAL, "Select a motherboard to check the processor socket.");

        String cpuSocket = valueOrExtract(cpu, "socket", SOCKET_PATTERN);
        String boardSocket = valueOrExtract(motherboard, "socket", SOCKET_PATTERN);
        if (cpuSocket.isBlank() || boardSocket.isBlank()) {
            return new Result(State.UNKNOWN, "Compatibility cannot be verified: processor or motherboard socket data is missing.");
        }
        if (!normalize(cpuSocket).equals(normalize(boardSocket))) {
            return new Result(State.INCOMPATIBLE, "Not compatible: processor socket " + cpuSocket
                    + " does not match motherboard socket " + boardSocket + ". Choose a board with the same socket.");
        }

        String cpuBrand = cpu.attribute("brand");
        String boardPlatform = motherboard.attribute("platform");
        if (!cpuBrand.isBlank() && !boardPlatform.isBlank()
                && !cpuBrand.equalsIgnoreCase(boardPlatform)) {
            return new Result(State.INCOMPATIBLE, "Not compatible: " + cpuBrand
                    + " processor platform does not match the motherboard’s " + boardPlatform + " platform.");
        }

        if (ram != null) {
            String boardRam = valueOrExtract(motherboard, "ramType", RAM_PATTERN);
            String selectedRam = valueOrExtract(ram, "ramType", RAM_PATTERN);
            if (!boardRam.isBlank() && !selectedRam.isBlank() && !boardRam.equalsIgnoreCase(selectedRam)) {
                return new Result(State.INCOMPATIBLE, "Not compatible: motherboard supports " + boardRam
                        + " but the selected memory is " + selectedRam + ".");
            }
            if (boardRam.isBlank() || selectedRam.isBlank()) {
                return new Result(State.UNKNOWN, "CPU and motherboard sockets match (" + cpuSocket
                        + "), but memory compatibility cannot be verified from the available specifications.");
            }
        }
        return new Result(State.COMPATIBLE, "Compatible: processor and motherboard both use " + cpuSocket
                + (ram == null ? "." : "; selected memory matches " + valueOrExtract(motherboard, "ramType", RAM_PATTERN) + "."));
    }

    private static String valueOrExtract(Part part, String key, Pattern pattern) {
        String value = part.attribute(key);
        if (!value.isBlank()) return value;
        Matcher matcher = pattern.matcher(part.name() + " " + part.specs());
        return matcher.find() ? matcher.group().replaceAll("\\s+", "").toUpperCase(Locale.ROOT) : "";
    }

    private static String normalize(String value) {
        return value.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }
}
