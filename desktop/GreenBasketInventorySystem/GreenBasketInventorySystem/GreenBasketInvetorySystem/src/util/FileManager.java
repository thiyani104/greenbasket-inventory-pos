package util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * All disk access for the application: a data folder of UTF-8 CSV and properties files.
 * CSV handling follows RFC 4180 (quotes, commas and line breaks inside fields survive a round trip),
 * and whole-file writes are atomic (written to a temporary file, then moved into place).
 */
public class FileManager {

    private static final String BOM = "﻿";

    private final Path dir;

    public FileManager(Path dir) {
        this.dir = dir.toAbsolutePath();
        try {
            Files.createDirectories(this.dir);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create data folder " + this.dir + ": " + e.getMessage(), e);
        }
    }

    public Path dir() { return dir; }

    public Path path(String name) { return dir.resolve(name); }

    // ------------------------------------------------------------------ CSV

    /** Data rows of a CSV file inside the data folder (header skipped); empty when the file does not exist. */
    public List<String[]> readCsv(String name) {
        Path p = path(name);
        if (!Files.exists(p)) return new ArrayList<>();
        List<String[]> all = readCsvFile(p);
        if (!all.isEmpty()) all.remove(0);
        return all;
    }

    public void writeCsv(String name, String[] header, List<String[]> rows) {
        writeAtomic(path(name), csvText(header, rows));
    }

    public void appendCsv(String name, String[] header, String[] row) {
        Path p = path(name);
        try {
            StringBuilder sb = new StringBuilder();
            if (!Files.exists(p) || Files.size(p) == 0) sb.append(csvLine(header));
            sb.append(csvLine(row));
            Files.write(p, sb.toString().getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write " + name + ": " + e.getMessage(), e);
        }
    }

    /** Every row of any CSV file, header included. A leading byte-order mark is ignored. */
    public static List<String[]> readCsvFile(Path file) {
        String text;
        try {
            text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + file.getFileName() + ": " + e.getMessage(), e);
        }
        if (text.startsWith(BOM)) text = text.substring(1);
        return parseCsv(text);
    }

    /** Writes a CSV for the user (with a BOM so Excel opens accented text correctly). */
    public static void writeCsvFile(Path file, String[] header, List<String[]> rows) {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.write(file, (BOM + csvText(header, rows)).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save " + file.getFileName() + ": " + e.getMessage(), e);
        }
    }

    public static void writeText(Path file, String text) {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.write(file, text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save " + file.getFileName() + ": " + e.getMessage(), e);
        }
    }

    static String csvText(String[] header, List<String[]> rows) {
        StringBuilder sb = new StringBuilder(csvLine(header));
        for (String[] r : rows) sb.append(csvLine(r));
        return sb.toString();
    }

    static String csvLine(String[] fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) sb.append(',');
            String f = fields[i] == null ? "" : fields[i];
            if (f.contains(",") || f.contains("\"") || f.contains("\n") || f.contains("\r")) {
                sb.append('"').append(f.replace("\"", "\"\"")).append('"');
            } else {
                sb.append(f);
            }
        }
        return sb.append('\n').toString();
    }

    /** RFC 4180 parser: quoted fields, doubled quotes, embedded line breaks. Blank lines are skipped. */
    static List<String[]> parseCsv(String text) {
        List<String[]> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false, any = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else quoted = false;
                } else cur.append(c);
            } else if (c == '"') {
                quoted = true;
                any = true;
            } else if (c == ',') {
                row.add(cur.toString());
                cur.setLength(0);
                any = true;
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                if (any || cur.length() > 0) {
                    row.add(cur.toString());
                    rows.add(row.toArray(new String[0]));
                }
                row = new ArrayList<>();
                cur.setLength(0);
                any = false;
            } else {
                cur.append(c);
                any = true;
            }
        }
        if (any || cur.length() > 0) {
            row.add(cur.toString());
            rows.add(row.toArray(new String[0]));
        }
        return rows;
    }

    // ----------------------------------------------------------- properties

    public Properties loadProperties(String name) {
        Properties p = new Properties();
        Path f = path(name);
        if (Files.exists(f)) {
            try (InputStream in = Files.newInputStream(f)) {
                p.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new UncheckedIOException("Could not read " + name + ": " + e.getMessage(), e);
            }
        }
        return p;
    }

    public void saveProperties(String name, Properties props) {
        Path tmp = path(name + ".tmp");
        try (OutputStream out = Files.newOutputStream(tmp)) {
            props.store(new java.io.OutputStreamWriter(out, StandardCharsets.UTF_8), "Green Basket settings");
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save " + name + ": " + e.getMessage(), e);
        }
        move(tmp, path(name));
    }

    // -------------------------------------------------------------- helpers

    private void writeAtomic(Path target, String text) {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.write(tmp, text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write " + target.getFileName() + ": " + e.getMessage(), e);
        }
        move(tmp, target);
    }

    private static void move(Path from, Path to) {
        try {
            try {
                Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save " + to.getFileName() + ": " + e.getMessage(), e);
        }
    }
}
