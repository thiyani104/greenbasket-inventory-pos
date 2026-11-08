package util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import model.LogEntry;
import model.MovementType;
import model.PaymentMethod;
import model.Product;
import model.Role;
import model.Sale;
import model.SaleItem;
import model.SalesAssistant;
import model.StockMovement;
import model.StoreManager;
import model.User;
import service.ServiceException;

/**
 * All disk access lives here. Data is kept as plain CSV files in one folder so it can be opened in Excel,
 * backed up by copying the folder, and recovered by hand. Writes are atomic (temp file + rename) and a
 * damaged line never stops the app from starting.
 */
public class FileManager {

    private static final String[] DATA_FILES = {
        "products.csv", "movements.csv", "sales.csv", "users.csv", "activity.csv", "settings.properties"
    };

    private final Path dir;

    public FileManager(Path dir) {
        this.dir = dir;
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new ServiceException("Cannot create the data folder " + dir + ": " + e.getMessage());
        }
    }

    public Path getDir() { return dir; }

    public Path file(String name) { return dir.resolve(name); }

    public boolean exists(String name) { return Files.exists(file(name)); }

    // ============================================================ low level

    private void writeAtomic(String name, List<String> lines) {
        Path target = file(name);
        Path tmp = file(name + ".tmp");
        try {
            Files.write(tmp, lines, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new ServiceException("Could not save " + name + ": " + e.getMessage());
        }
    }

    private List<String[]> readRows(String name) {
        if (!exists(name)) return new ArrayList<>();
        try {
            List<String[]> rows = Csv.read(file(name));
            if (!rows.isEmpty()) rows.remove(0); // header
            return rows;
        } catch (IOException e) {
            throw new ServiceException("Could not read " + name + ": " + e.getMessage());
        }
    }

    private void append(String name, String header, String line) {
        Path p = file(name);
        try {
            if (!Files.exists(p)) Files.writeString(p, header + "\n", StandardCharsets.UTF_8);
            Files.writeString(p, line + "\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new ServiceException("Could not write " + name + ": " + e.getMessage());
        }
    }

    private static String col(String[] r, int i) { return i < r.length ? r[i].trim() : ""; }

    private static double num(String[] r, int i) { return Double.parseDouble(col(r, i)); }

    private static int integer(String[] r, int i) { return Integer.parseInt(col(r, i)); }

    private static LocalDateTime dateTime(String s) { return s.isEmpty() ? null : LocalDateTime.parse(s); }

    // ============================================================ settings

    public AppSettings loadSettings() {
        Properties p = new Properties();
        if (exists("settings.properties")) {
            try (Reader r = Files.newBufferedReader(file("settings.properties"), StandardCharsets.UTF_8)) {
                p.load(r);
            } catch (IOException ignored) {
                // fall back to defaults
            }
        }
        AppSettings s = AppSettings.from(p);
        Formats.setCurrency(s.getCurrency());
        return s;
    }

    public void saveSettings(AppSettings s) {
        try (Writer w = Files.newBufferedWriter(file("settings.properties"), StandardCharsets.UTF_8)) {
            s.toProperties().store(w, "Green Basket settings");
        } catch (IOException e) {
            throw new ServiceException("Could not save settings: " + e.getMessage());
        }
    }

    // ============================================================ products

    public List<Product> loadProducts() {
        List<Product> out = new ArrayList<>();
        for (String[] r : readRows("products.csv")) {
            try {
                String name = col(r, 1);
                if (name.isEmpty()) continue;
                String exp = col(r, 8);
                out.add(new Product(integer(r, 0), name, col(r, 2), col(r, 3), num(r, 4), num(r, 5),
                        integer(r, 6), integer(r, 7), exp.isEmpty() ? null : LocalDate.parse(exp)));
            } catch (RuntimeException e) {
                // skip damaged line
            }
        }
        return out;
    }

    public void saveProducts(List<Product> products) {
        List<String> lines = new ArrayList<>();
        lines.add("id,name,category,supplier,price,cost,quantity,reorder,expiry");
        for (Product p : products) {
            lines.add(Csv.row(p.getProductId(), p.getProductName(), p.getCategory(), p.getSupplier(), p.getPrice(),
                    p.getCost(), p.getQuantity(), p.getReorderLevel(), p.getExpiryDate() == null ? "" : p.getExpiryDate()));
        }
        writeAtomic("products.csv", lines);
    }

    // ============================================================ stock ledger

    private static final String MOVEMENT_HEADER = "id,datetime,productId,productName,type,change,balance,user,note";

    public List<StockMovement> loadMovements() {
        List<StockMovement> out = new ArrayList<>();
        for (String[] r : readRows("movements.csv")) {
            try {
                out.add(new StockMovement(integer(r, 0), LocalDateTime.parse(col(r, 1)), integer(r, 2), col(r, 3),
                        MovementType.valueOf(col(r, 4)), integer(r, 5), integer(r, 6), col(r, 7), col(r, 8)));
            } catch (RuntimeException e) {
                // skip damaged line
            }
        }
        return out;
    }

    public void appendMovement(StockMovement m) {
        append("movements.csv", MOVEMENT_HEADER, Csv.row(m.getId(), m.getDateTime(), m.getProductId(), m.getProductName(),
                m.getType().name(), m.getChange(), m.getBalanceAfter(), m.getUser(), m.getNote()));
    }

    // ============================================================ sales

    public List<Sale> loadSales() {
        Map<Integer, List<String[]>> grouped = new LinkedHashMap<>();
        for (String[] r : readRows("sales.csv")) {
            try {
                grouped.computeIfAbsent(integer(r, 0), k -> new ArrayList<>()).add(r);
            } catch (RuntimeException e) {
                // skip damaged line
            }
        }
        List<Sale> out = new ArrayList<>();
        for (Map.Entry<Integer, List<String[]>> e : grouped.entrySet()) {
            try {
                String[] h = e.getValue().get(0);
                List<SaleItem> items = new ArrayList<>();
                for (String[] r : e.getValue()) {
                    items.add(new SaleItem(integer(r, 10), col(r, 11), col(r, 12), num(r, 13), num(r, 14), integer(r, 15)));
                }
                Sale s = new Sale(e.getKey(), LocalDateTime.parse(col(h, 1)), col(h, 2), PaymentMethod.valueOf(col(h, 3)),
                        items, num(h, 4), num(h, 5), num(h, 6));
                if (Boolean.parseBoolean(col(h, 7))) s.markVoided(col(h, 8), dateTime(col(h, 9)));
                out.add(s);
            } catch (RuntimeException ex) {
                // skip damaged sale
            }
        }
        return out;
    }

    public void saveSales(List<Sale> sales) {
        List<String> lines = new ArrayList<>();
        lines.add("saleId,datetime,cashier,payment,discountPercent,taxRate,tendered,voided,voidedBy,voidedAt,"
                + "productId,productName,category,unitPrice,unitCost,quantity");
        for (Sale s : sales) {
            for (SaleItem i : s.getItems()) {
                lines.add(Csv.row(s.getId(), s.getDateTime(), s.getCashier(), s.getPayment().name(), s.getDiscountPercent(),
                        s.getTaxRate(), s.getTendered(), s.isVoided(), s.getVoidedBy(),
                        s.getVoidedAt() == null ? "" : s.getVoidedAt(),
                        i.getProductId(), i.getProductName(), i.getCategory(), i.getUnitPrice(), i.getUnitCost(), i.getQuantity()));
            }
        }
        writeAtomic("sales.csv", lines);
    }

    // ============================================================ users

    public List<User> loadUsers() {
        List<User> out = new ArrayList<>();
        for (String[] r : readRows("users.csv")) {
            try {
                Role role = Role.valueOf(col(r, 3));
                User u = role == Role.MANAGER
                        ? new StoreManager(integer(r, 0), col(r, 1), col(r, 2), col(r, 4))
                        : new SalesAssistant(integer(r, 0), col(r, 1), col(r, 2), col(r, 4));
                u.setActive(Boolean.parseBoolean(col(r, 5)));
                u.setCreatedAt(dateTime(col(r, 6)));
                u.setLastLogin(dateTime(col(r, 7)));
                out.add(u);
            } catch (RuntimeException e) {
                // skip damaged line
            }
        }
        return out;
    }

    public void saveUsers(List<User> users) {
        List<String> lines = new ArrayList<>();
        lines.add("id,username,fullName,role,passwordHash,active,createdAt,lastLogin");
        for (User u : users) {
            lines.add(Csv.row(u.getUserId(), u.getUsername(), u.getFullName(), u.getRole().name(), u.getPasswordHash(),
                    u.isActive(), u.getCreatedAt(), u.getLastLogin() == null ? "" : u.getLastLogin()));
        }
        writeAtomic("users.csv", lines);
    }

    // ============================================================ audit log

    public List<LogEntry> loadLog() {
        List<LogEntry> out = new ArrayList<>();
        for (String[] r : readRows("activity.csv")) {
            try {
                out.add(new LogEntry(LocalDateTime.parse(col(r, 0)), col(r, 1), col(r, 2), col(r, 3)));
            } catch (RuntimeException e) {
                // skip damaged line
            }
        }
        return out;
    }

    public void appendLog(LogEntry e) {
        append("activity.csv", "datetime,user,action,details", Csv.row(e.getDateTime(), e.getUser(), e.getAction(), e.getDetails()));
    }

    // ============================================================ exports

    public static void exportCsv(Path target, List<String[]> rows) {
        try {
            // BOM so Excel opens UTF-8 (currency symbols, accents) correctly
            List<String> lines = new ArrayList<>();
            for (String[] r : rows) lines.add(Csv.row((Object[]) r));
            if (!lines.isEmpty()) lines.set(0, "﻿" + lines.get(0));
            Files.write(target, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ServiceException("Could not write " + target.getFileName() + ": " + e.getMessage());
        }
    }

    // ============================================================ backup / restore

    /** Zips every data file into the given archive. Returns the number of files stored. */
    public int backupTo(Path zip) {
        int n = 0;
        try (OutputStream os = Files.newOutputStream(zip); ZipOutputStream out = new ZipOutputStream(os)) {
            for (String name : DATA_FILES) {
                if (!exists(name)) continue;
                out.putNextEntry(new ZipEntry(name));
                Files.copy(file(name), out);
                out.closeEntry();
                n++;
            }
        } catch (IOException e) {
            throw new ServiceException("Backup failed: " + e.getMessage());
        }
        return n;
    }

    /** Replaces the data files with the contents of a backup archive. Only known file names are accepted. */
    public int restoreFrom(Path zip) {
        Map<String, byte[]> found = new LinkedHashMap<>();
        try (InputStream is = Files.newInputStream(zip); ZipInputStream in = new ZipInputStream(is)) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) {
                String name = e.getName();
                for (String known : DATA_FILES) {
                    if (known.equals(name)) found.put(name, in.readAllBytes());
                }
            }
        } catch (IOException e) {
            throw new ServiceException("Could not read the backup: " + e.getMessage());
        }
        if (!found.containsKey("products.csv") && !found.containsKey("users.csv")) {
            throw new ServiceException("That file does not look like a Green Basket backup.");
        }
        try {
            for (String known : DATA_FILES) Files.deleteIfExists(file(known));
            for (Map.Entry<String, byte[]> e : found.entrySet()) Files.write(file(e.getKey()), e.getValue());
        } catch (IOException e) {
            throw new ServiceException("Restore failed: " + e.getMessage());
        }
        return found.size();
    }

    /** Keeps one automatic backup per day in data/backups and prunes to the newest {@code keep}. */
    public void autoBackup(int keep) {
        try {
            Path folder = dir.resolve("backups");
            Files.createDirectories(folder);
            Path today = folder.resolve("auto-" + Formats.date(LocalDate.now()) + ".zip");
            if (!Files.exists(today) && exists("products.csv")) backupTo(today);
            try (Stream<Path> s = Files.list(folder)) {
                List<Path> all = new ArrayList<>(s.filter(p -> p.getFileName().toString().startsWith("auto-")).sorted().toList());
                for (int i = 0; i < all.size() - keep; i++) Files.deleteIfExists(all.get(i));
            }
        } catch (IOException | RuntimeException ignored) {
            // a failed convenience backup must never stop the app
        }
    }
}
