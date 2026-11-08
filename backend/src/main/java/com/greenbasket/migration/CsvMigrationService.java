package com.greenbasket.migration;

import com.greenbasket.domain.ActivityLog;
import com.greenbasket.domain.Category;
import com.greenbasket.domain.MovementType;
import com.greenbasket.domain.PaymentMethod;
import com.greenbasket.domain.Product;
import com.greenbasket.domain.Role;
import com.greenbasket.domain.Sale;
import com.greenbasket.domain.SaleItem;
import com.greenbasket.domain.StockMovement;
import com.greenbasket.domain.Supplier;
import com.greenbasket.domain.User;
import com.greenbasket.repository.ActivityLogRepository;
import com.greenbasket.repository.AppSettingRepository;
import com.greenbasket.repository.CategoryRepository;
import com.greenbasket.repository.ProductRepository;
import com.greenbasket.repository.SaleRepository;
import com.greenbasket.repository.StockMovementRepository;
import com.greenbasket.repository.SupplierRepository;
import com.greenbasket.repository.UserRepository;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-shot import of the Swing app's CSV data folder (products.csv, movements.csv, sales.csv, users.csv, activity.csv,
 * settings.properties) into PostgreSQL. Runs in a single transaction: either everything is imported or nothing is.
 * Refuses to run on a database that already holds products, sales or users, so it cannot double-import.
 */
@Service
public class CsvMigrationService {

    public record Result(int categories, int suppliers, int products, int movements, int sales, int users, int activity,
                         List<String> warnings) { }

    private final CategoryRepository categories;
    private final SupplierRepository suppliers;
    private final ProductRepository products;
    private final StockMovementRepository movements;
    private final SaleRepository sales;
    private final UserRepository users;
    private final ActivityLogRepository activity;
    private final AppSettingRepository settings;

    public CsvMigrationService(CategoryRepository categories, SupplierRepository suppliers, ProductRepository products,
                               StockMovementRepository movements, SaleRepository sales, UserRepository users,
                               ActivityLogRepository activity, AppSettingRepository settings) {
        this.categories = categories;
        this.suppliers = suppliers;
        this.products = products;
        this.movements = movements;
        this.sales = sales;
        this.users = users;
        this.activity = activity;
        this.settings = settings;
    }

    /** True when there is already data, in which case the importer must not run again. */
    @Transactional(readOnly = true)
    public boolean alreadyPopulated() { return products.count() > 0 || sales.count() > 0 || users.count() > 0; }

    @Transactional
    public Result importFrom(Path dir, boolean allowIfUsersExist) {
        if (products.count() > 0 || sales.count() > 0) throw new IllegalStateException("The database already contains products or sales; refusing to import.");
        if (!allowIfUsersExist && users.count() > 0) throw new IllegalStateException("The database already contains users; refusing to import.");
        List<String> warn = new ArrayList<>();

        // ---------------------------------------------------------------- users
        int userCount = 0;
        Map<String, User> userByName = new HashMap<>();
        for (User existing : users.findAll()) userByName.put(existing.getUsernameKey(), existing);
        for (String[] r : rows(dir, "users.csv", warn)) {
            try {
                String name = col(r, 1);
                if (userByName.containsKey(User.keyOf(name))) { warn.add("users.csv: '" + name + "' already exists, skipped"); continue; }
                User u = new User(name, col(r, 2), Role.valueOf(col(r, 3)), "{pbkdf2legacy}" + col(r, 4));
                u.setActive(Boolean.parseBoolean(col(r, 5)));
                u.setCreatedAt(dateTime(col(r, 6)));
                u.setLastLogin(dateTime(col(r, 7)));
                users.save(u);
                userByName.put(u.getUsernameKey(), u);
                userCount++;
            } catch (RuntimeException e) {
                warn.add("users.csv: skipped a damaged line (" + e.getMessage() + ")");
            }
        }

        // ---------------------------------------------------------------- products
        Map<String, Category> cats = new HashMap<>();
        Map<String, Supplier> sups = new HashMap<>();
        Map<Integer, Product> byLegacyId = new LinkedHashMap<>();
        for (String[] r : rows(dir, "products.csv", warn)) {
            try {
                String name = col(r, 1);
                if (name.isEmpty()) continue;
                String cat = col(r, 2).isEmpty() ? "Uncategorised" : col(r, 2);
                Category c = cats.computeIfAbsent(Category.keyOf(cat),
                        k -> categories.findByNameKey(k).orElseGet(() -> categories.save(new Category(cat))));
                Supplier s = null;
                if (!col(r, 3).isEmpty()) {
                    String sn = col(r, 3);
                    s = sups.computeIfAbsent(Supplier.keyOf(sn), k -> suppliers.findByNameKey(k).orElseGet(() -> suppliers.save(new Supplier(sn))));
                }
                BigDecimal price = money(r, 4), cost = money(r, 5);
                if (price.signum() <= 0) { warn.add("products.csv: '" + name + "' has no valid price, skipped"); continue; }
                if (cost.compareTo(price) > 0) {
                    warn.add("products.csv: '" + name + "' cost above price, cost set to price");
                    cost = price;
                }
                String exp = col(r, 8);
                Product p = new Product(name, c, s, price, cost, Math.max(0, Integer.parseInt(col(r, 6))),
                        Math.max(0, Integer.parseInt(col(r, 7))), exp.isEmpty() ? null : LocalDate.parse(exp));
                if (products.findByNameKey(Product.keyOf(name)).isPresent() || byLegacyId.values().stream().anyMatch(o -> o.getNameKey().equals(p.getNameKey()))) {
                    warn.add("products.csv: duplicate name '" + name + "', skipped");
                    continue;
                }
                byLegacyId.put(Integer.parseInt(col(r, 0)), products.save(p));
            } catch (RuntimeException e) {
                warn.add("products.csv: skipped a damaged line (" + e.getMessage() + ")");
            }
        }

        // ---------------------------------------------------------------- stock ledger
        int mv = 0;
        for (String[] r : rows(dir, "movements.csv", warn)) {
            try {
                Product p = byLegacyId.get(Integer.parseInt(col(r, 2)));
                if (p == null) continue;
                StockMovement m = new StockMovement(p, MovementType.valueOf(col(r, 4)), Integer.parseInt(col(r, 5)),
                        Math.max(0, Integer.parseInt(col(r, 6))), userByName.get(User.keyOf(col(r, 7))),
                        col(r, 7).isEmpty() ? "legacy" : col(r, 7), col(r, 8));
                m.setCreatedAt(LocalDateTime.parse(col(r, 1)));
                movements.save(m);
                mv++;
            } catch (RuntimeException e) {
                warn.add("movements.csv: skipped a damaged line (" + e.getMessage() + ")");
            }
        }

        // ---------------------------------------------------------------- sales (one CSV row per receipt line)
        Map<Integer, List<String[]>> grouped = new LinkedHashMap<>();
        for (String[] r : rows(dir, "sales.csv", warn)) {
            try { grouped.computeIfAbsent(Integer.parseInt(col(r, 0)), k -> new ArrayList<>()).add(r); }
            catch (RuntimeException e) { warn.add("sales.csv: skipped a damaged line"); }
        }
        int saleCount = 0;
        for (Map.Entry<Integer, List<String[]>> e : grouped.entrySet()) {
            try {
                String[] h = e.getValue().get(0);
                Sale s = new Sale(userByName.get(User.keyOf(col(h, 2))), col(h, 2), PaymentMethod.valueOf(col(h, 3)),
                        money(h, 4), money(h, 5), money(h, 6));
                s.setCreatedAt(LocalDateTime.parse(col(h, 1)));
                int added = 0;
                for (String[] r : e.getValue()) {
                    Product p = byLegacyId.get(Integer.parseInt(col(r, 10)));
                    if (p == null) { warn.add("sales.csv: sale " + e.getKey() + " refers to a missing product, line skipped"); continue; }
                    s.addItem(SaleItem.snapshot(p, col(r, 11), col(r, 12), money(r, 13), money(r, 14), Integer.parseInt(col(r, 15))));
                    added++;
                }
                if (added == 0) continue;
                s = sales.saveAndFlush(s);
                s.setReceiptNo(String.format("GB-%06d", e.getKey()));       // keep the receipt numbers customers already hold
                if (Boolean.parseBoolean(col(h, 7))) {
                    s.markVoided(col(h, 8), "Imported from desktop app");
                    LocalDateTime when = dateTime(col(h, 9));
                    if (when != null) s.setVoidedAt(when);
                }
                saleCount++;
            } catch (RuntimeException ex) {
                warn.add("sales.csv: skipped sale " + e.getKey() + " (" + ex.getMessage() + ")");
            }
        }

        // ---------------------------------------------------------------- activity
        int act = 0;
        for (String[] r : rows(dir, "activity.csv", warn)) {
            try {
                ActivityLog a = new ActivityLog(col(r, 1), col(r, 2), col(r, 3));
                a.setCreatedAt(LocalDateTime.parse(col(r, 0)));
                activity.save(a);
                act++;
            } catch (RuntimeException e) {
                warn.add("activity.csv: skipped a damaged line");
            }
        }

        // ---------------------------------------------------------------- settings
        Path props = dir.resolve("settings.properties");
        if (Files.exists(props)) {
            try (InputStream in = Files.newInputStream(props)) {
                Properties p = new Properties();
                p.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
                for (String key : List.of("store.name", "store.address", "currency", "tax.rate", "expiry.warn.days",
                        "default.reorder", "receipt.footer", "daily.target")) {
                    String v = p.getProperty(key);          // the desktop app used the same key names
                    if (v != null && !v.isBlank()) settings.findById(key).ifPresent(s -> s.setValue(v.trim()));
                }
            } catch (IOException e) {
                warn.add("settings.properties could not be read: " + e.getMessage());
            }
        }

        return new Result(cats.size(), sups.size(), byLegacyId.size(), mv, saleCount, userCount, act, warn);
    }

    // ------------------------------------------------------------------ CSV helpers

    private static List<String[]> rows(Path dir, String file, List<String> warn) {
        Path f = dir.resolve(file);
        List<String[]> out = new ArrayList<>();
        if (!Files.exists(f)) return out;
        try {
            List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
            for (int i = 1; i < lines.size(); i++) {                    // line 0 is the header
                if (!lines.get(i).isBlank()) out.add(parseLine(lines.get(i)));
            }
        } catch (IOException e) {
            warn.add(file + " could not be read: " + e.getMessage());
        }
        return out;
    }

    static String[] parseLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') { cur.append('"'); i++; } else quoted = false;
                } else cur.append(c);
            } else if (c == '"') quoted = true;
            else if (c == ',') { out.add(cur.toString()); cur.setLength(0); }
            else cur.append(c);
        }
        out.add(cur.toString());
        return out.toArray(new String[0]);
    }

    private static String col(String[] r, int i) { return i < r.length ? r[i].trim() : ""; }

    private static BigDecimal money(String[] r, int i) {
        String v = col(r, i);
        return v.isEmpty() ? BigDecimal.ZERO : new BigDecimal(v).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static LocalDateTime dateTime(String s) { return s == null || s.isEmpty() ? null : LocalDateTime.parse(s); }
}
