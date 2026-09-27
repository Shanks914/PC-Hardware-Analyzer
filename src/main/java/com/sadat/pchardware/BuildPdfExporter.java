package com.sadat.pchardware;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/** Writes a compact, dependency-free PDF quote for a saved PC build. */
public final class BuildPdfExporter {
    private static final int LINES_PER_PAGE = 46;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    private BuildPdfExporter() { }

    public static void export(Path destination, SavedBuild build) throws IOException {
        List<String> lines = makeLines(build);
        List<List<String>> pages = new ArrayList<>();
        for (int start = 0; start < lines.size(); start += LINES_PER_PAGE) {
            pages.add(lines.subList(start, Math.min(start + LINES_PER_PAGE, lines.size())));
        }

        List<byte[]> objects = new ArrayList<>();
        objects.add(ascii("<< /Type /Catalog /Pages 2 0 R >>")); // 1
        objects.add(new byte[0]); // 2, filled after page references are known
        objects.add(ascii("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>")); // 3
        List<Integer> pageObjectIds = new ArrayList<>();
        for (List<String> pageLines : pages) {
            int pageId = objects.size() + 1;
            int contentId = pageId + 1;
            pageObjectIds.add(pageId);
            byte[] stream = makePageStream(pageLines);
            objects.add(ascii("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 842] "
                    + "/Resources << /Font << /F1 3 0 R >> >> /Contents " + contentId + " 0 R >>"));
            ByteArrayOutputStream contentObject = new ByteArrayOutputStream();
            contentObject.writeBytes(ascii("<< /Length " + stream.length + " >>\nstream\n"));
            contentObject.writeBytes(stream);
            contentObject.writeBytes(ascii("\nendstream"));
            objects.add(contentObject.toByteArray());
        }
        String kids = pageObjectIds.stream().map(id -> id + " 0 R").reduce((a, b) -> a + " " + b).orElse("");
        objects.set(1, ascii("<< /Type /Pages /Kids [" + kids + "] /Count " + pageObjectIds.size() + " >>"));

        ByteArrayOutputStream pdf = new ByteArrayOutputStream();
        pdf.writeBytes(ascii("%PDF-1.4\n"));
        List<Integer> offsets = new ArrayList<>();
        offsets.add(0);
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(pdf.size());
            pdf.writeBytes(ascii((i + 1) + " 0 obj\n"));
            pdf.writeBytes(objects.get(i));
            pdf.writeBytes(ascii("\nendobj\n"));
        }
        int xrefOffset = pdf.size();
        pdf.writeBytes(ascii("xref\n0 " + (objects.size() + 1) + "\n0000000000 65535 f \n"));
        for (int i = 1; i < offsets.size(); i++) {
            pdf.writeBytes(ascii(String.format("%010d 00000 n \n", offsets.get(i))));
        }
        pdf.writeBytes(ascii("trailer\n<< /Size " + (objects.size() + 1) + " /Root 1 0 R >>\nstartxref\n"
                + xrefOffset + "\n%%EOF\n"));
        Files.write(destination, pdf.toByteArray());
    }

    private static List<String> makeLines(SavedBuild build) {
        List<String> lines = new ArrayList<>();
        lines.add("PC HARDWARE ANALYZER - BUILD QUOTE");
        lines.add("Build: " + build.name());
        lines.add("Generated: " + LocalDateTime.now().format(DATE_FORMAT));
        lines.add("");
        lines.add("COMPONENTS");
        lines.add("------------------------------------------------------------");
        double total = 0;
        for (Part part : build.parts()) {
            lines.addAll(wrap(part.category() + ": " + part.name(), 86));
            lines.add("  Price: BDT " + String.format("%,.0f", part.price()));
            total += part.price();
            if (!part.specs().isBlank()) lines.addAll(wrap("  " + part.specs(), 86));
            for (var attribute : new TreeMap<>(part.attributes()).entrySet()) {
                if (!attribute.getValue().isBlank()) {
                    lines.addAll(wrap("  " + attribute.getKey() + ": " + attribute.getValue(), 86));
                }
            }
            lines.add("");
        }
        lines.add("------------------------------------------------------------");
        lines.add("TOTAL: BDT " + String.format("%,.0f", total));
        lines.add("");
        lines.add("Generated from the saved build in PC Hardware Analyzer.");
        return lines;
    }

    private static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        String remaining = text;
        while (remaining.length() > width) {
            int split = remaining.lastIndexOf(' ', width);
            if (split < 1) split = width;
            lines.add(remaining.substring(0, split));
            remaining = "  " + remaining.substring(split).stripLeading();
        }
        lines.add(remaining);
        return lines;
    }

    private static byte[] makePageStream(List<String> lines) {
        StringBuilder stream = new StringBuilder("BT\n/F1 10 Tf\n50 790 Td\n");
        for (int i = 0; i < lines.size(); i++) {
            if (i == 0) stream.append("/F1 15 Tf\n");
            else if (i == 1) stream.append("/F1 12 Tf\n");
            else stream.append("/F1 10 Tf\n");
            stream.append('(').append(pdfString(lines.get(i))).append(") Tj\n0 -16 Td\n");
        }
        stream.append("ET");
        return ascii(stream.toString());
    }

    private static String pdfString(String value) {
        StringBuilder escaped = new StringBuilder();
        for (char character : value.toCharArray()) {
            char safe = character >= 32 && character <= 126 ? character : '?';
            if (safe == '(' || safe == ')' || safe == '\\') escaped.append('\\');
            escaped.append(safe);
        }
        return escaped.toString();
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.ISO_8859_1);
    }
}
