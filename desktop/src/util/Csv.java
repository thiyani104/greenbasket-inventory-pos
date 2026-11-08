package util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Minimal RFC-4180 style CSV reader/writer (single-line records). */
public final class Csv {

    private Csv() {}

    public static String escape(Object value) {
        if (value == null) return "";
        String s = value.toString().replace("\r", " ").replace("\n", " ");
        if (s.contains(",") || s.contains("\"")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    public static String row(Object... cols) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cols.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(escape(cols[i]));
        }
        return sb.toString();
    }

    public static String[] parseLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out.toArray(new String[0]);
    }

    /** Reads all non-blank lines as parsed rows (header row included). */
    public static List<String[]> read(Path file) throws IOException {
        List<String[]> rows = new ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.startsWith("﻿")) line = line.substring(1);
            if (line.isBlank()) continue;
            rows.add(parseLine(line));
        }
        return rows;
    }

    public static void write(Path file, List<String[]> rows) throws IOException {
        List<String> lines = new ArrayList<>();
        for (String[] r : rows) lines.add(row((Object[]) r));
        Files.write(file, lines, StandardCharsets.UTF_8);
    }
}
