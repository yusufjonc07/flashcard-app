package flashcards.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads vocabulary files.
 *
 * <ul>
 *   <li>{@code .csv}: one {@code front,back} pair per line. Quoted fields ({@code "a, b"}) are
 *       supported and a {@code front,back} header line is skipped.</li>
 *   <li>{@code .txt}: one word per line, optionally followed by a tab, comma, or {@code " - "}
 *       and its meaning. Words without a meaning are returned in {@link Result#untranslated()}
 *       so the controller can translate them.</li>
 * </ul>
 * Blank lines and lines starting with {@code #} are ignored.
 */
public final class CardImporter {

    /** Parsed file: complete cards plus words that still need a translation. */
    public record Result(Map<String, String> cards, List<String> untranslated) {
    }

    private CardImporter() {
    }

    public static Result read(Path path) throws IOException {
        String name = path.getFileName().toString().toLowerCase();
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (name.endsWith(".csv")) {
            return parseCsv(lines);
        }
        if (name.endsWith(".txt")) {
            return parseText(lines);
        }
        throw new IOException("Only .csv and .txt files can be imported");
    }

    public static Result parseCsv(List<String> lines) {
        Map<String, String> cards = new LinkedHashMap<>();
        List<String> untranslated = new ArrayList<>();
        boolean first = true;
        for (String rawLine : lines) {
            String line = stripBom(rawLine).trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            List<String> fields = splitCsvLine(line);
            String front = fields.get(0).trim();
            String back = fields.size() > 1 ? fields.get(1).trim() : "";
            if (first && front.equalsIgnoreCase("front")) {
                first = false;
                continue;
            }
            first = false;
            add(front, back, cards, untranslated);
        }
        return new Result(cards, untranslated);
    }

    public static Result parseText(List<String> lines) {
        Map<String, String> cards = new LinkedHashMap<>();
        List<String> untranslated = new ArrayList<>();
        for (String rawLine : lines) {
            String line = stripBom(rawLine).trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("\\t| - |,", 2);
            add(parts[0].trim(), parts.length > 1 ? parts[1].trim() : "", cards, untranslated);
        }
        return new Result(cards, untranslated);
    }

    private static void add(String front, String back, Map<String, String> cards,
                            List<String> untranslated) {
        if (front.isEmpty() || cards.containsKey(front) || untranslated.contains(front)) {
            return;
        }
        if (back.isEmpty()) {
            untranslated.add(front);
        } else {
            cards.put(front, back);
        }
    }

    private static List<String> splitCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                fields.add(field.toString());
                field.setLength(0);
            } else {
                field.append(c);
            }
        }
        fields.add(field.toString());
        return fields;
    }

    private static String stripBom(String line) {
        return line.startsWith("﻿") ? line.substring(1) : line;
    }
}
