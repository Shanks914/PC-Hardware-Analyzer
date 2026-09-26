package com.sadat.pchardware;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BuildCompatibility {
    private static final Pattern SOCKET_PATTERN = Pattern.compile("(?i)\\b(AM\\s?\\d+|LGA\\s?-?\\s?\\d{4,5}|TR\\s?\\d+)\\b");
    private static final Pattern RAM_PATTERN = Pattern.compile("(?i)\\bDDR[345]\\b");
    private static final Pattern CAPACITY_PATTERN = Pattern.compile("(?i)(\\d+)\\s*GB\\b");
    private static final Pattern MODULES_PATTERN = Pattern.compile("(?i)(\\d+)\\s*[x×]");

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
            int memoryCapacity = numberOrExtract(ram, "capacityGb", CAPACITY_PATTERN);
            int motherboardCapacity = numberOrExtract(motherboard, "maxMemoryGb", CAPACITY_PATTERN);
            if (memoryCapacity > 0 && motherboardCapacity > 0 && memoryCapacity > motherboardCapacity) {
                return new Result(State.INCOMPATIBLE, "Not compatible: selected RAM kit is " + memoryCapacity
                        + "GB but the motherboard supports up to " + motherboardCapacity + "GB.");
            }
            int moduleCount = numberOrExtract(ram, "modules", MODULES_PATTERN);
            int slotCount = numberOrExtract(motherboard, "memorySlots", Pattern.compile("(?i)(\\d+)\\s*(?:memory\\s+)?slots?\\b"));
            if (moduleCount > 0 && slotCount > 0 && moduleCount > slotCount) {
                return new Result(State.INCOMPATIBLE, "Not compatible: RAM kit uses " + moduleCount
                        + " modules but the motherboard has only " + slotCount + " memory slots.");
            }
        }
        Part ssd = build.get("storage-ssd");
        if (ssd != null) {
            String interfaces = motherboard.attribute("storageInterfaces").toUpperCase(Locale.ROOT);
            String ssdInterface = ssd.attribute("interface");
            if (!interfaces.isBlank() && !ssdInterface.isBlank()) {
                boolean supported = switch (ssdInterface.toUpperCase(Locale.ROOT)) {
                    case "SATA" -> interfaces.contains("SATA") && (!ssd.attribute("formFactor").startsWith("M.2")
                            || parseInt(motherboard.attribute("m2Slots")) > 0);
                    case "PCIE NVME" -> interfaces.contains("NVME") && parseInt(motherboard.attribute("m2Slots")) > 0;
                    case "USB" -> interfaces.contains("USB");
                    default -> false;
                };
                if (!supported) return new Result(State.INCOMPATIBLE,
                        "Not compatible: the selected motherboard does not support the SSD interface/slot requirements (" + ssdInterface + ").");
            }
        }
        Part hdd = build.get("storage-hdd");
        if (hdd != null && !motherboard.attribute("storageInterfaces").isBlank()) {
            String interfaces = motherboard.attribute("storageInterfaces").toUpperCase(Locale.ROOT);
            if (!interfaces.contains("SATA") || !hdd.attribute("interface").equalsIgnoreCase("SATA")) {
                return new Result(State.INCOMPATIBLE, "Not compatible: the selected motherboard does not list SATA support for the HDD.");
            }
            int sataDevices = (ssd != null && ssd.attribute("interface").equalsIgnoreCase("SATA") ? 1 : 0) + 1;
            int sataPorts = parseInt(motherboard.attribute("sataPorts"));
            if (sataPorts > 0 && sataDevices > sataPorts) {
                return new Result(State.INCOMPATIBLE, "Not compatible: selected SATA drives exceed the motherboard’s " + sataPorts + " SATA ports.");
            }
        }
        return new Result(State.COMPATIBLE, "Compatible: processor and motherboard both use " + cpuSocket
                + (ram == null ? "" : "; selected memory matches " + valueOrExtract(motherboard, "ramType", RAM_PATTERN))
                + (ssd == null ? "" : "; selected SSD interface is supported.")
                + (hdd == null ? "" : "; selected HDD has a supported SATA connection."));
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

    private static int numberOrExtract(Part part, String key, Pattern pattern) {
        String value = part.attribute(key);
        if (!value.isBlank()) {
            try { return Integer.parseInt(value); }
            catch (NumberFormatException ignored) { return 0; }
        }
        Matcher matcher = pattern.matcher(part.specs());
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    private static int parseInt(String value) {
        try { return Integer.parseInt(value); } catch (NumberFormatException ignored) { return 0; }
    }
}
