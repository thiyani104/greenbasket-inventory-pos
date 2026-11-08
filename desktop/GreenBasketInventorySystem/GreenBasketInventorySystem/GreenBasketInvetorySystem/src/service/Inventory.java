package service;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import model.Product;
import model.StockMovement;
import util.FileManager;
import util.Fmt;

/**
 * The product catalogue and its stock ledger.
 * <p>
 * Every quantity change goes through {@link #adjustStock} / {@link #setCount}, which keeps a movement
 * (who, when, why, new balance) so stock can always be explained. Changes are saved immediately; wrap a
 * group of changes in {@link #batch(Runnable)} to save and notify listeners only once.
 */
public class Inventory {

    private static final String PRODUCTS = "products.csv";
    private static final String MOVES = "stock_movements.csv";

    private final FileManager fm;
    private final ActivityLog log;
    private final List<Product> products = new ArrayList<>();
    private final List<StockMovement> movements = new ArrayList<>(); // oldest first
    private final List<Runnable> listeners = new ArrayList<>();
    private Supplier<String> actor = () -> "system";
    private int batchDepth = 0;
    private boolean dirty = false;

    public Inventory(FileManager fm, ActivityLog log) {
        this.fm = fm;
        this.log = log;
        for (String[] r : fm.readCsv(PRODUCTS)) {
            try {
                products.add(Product.fromCsv(r));
            } catch (RuntimeException ignored) { }
        }
        for (String[] r : fm.readCsv(MOVES)) {
            try {
                movements.add(StockMovement.fromCsv(r));
            } catch (RuntimeException ignored) { }
        }
    }

    /** Who is credited in the ledger when a change does not name a user explicitly. */
    public void setActor(Supplier<String> actor) { this.actor = actor; }

    public boolean hasStoredCatalogue() { return java.nio.file.Files.exists(fm.path(PRODUCTS)); }

    // ------------------------------------------------------------ listeners

    public void addListener(Runnable r) { listeners.add(r); }
    public void removeListener(Runnable r) { listeners.remove(r); }

    private void fire() { for (Runnable r : new ArrayList<>(listeners)) r.run(); }

    private void changed() {
        dirty = true;
        if (batchDepth == 0) flush();
    }

    private void flush() {
        if (!dirty) return;
        dirty = false;
        saveProducts();
        fire();
    }

    /** Runs several changes as one unit: a single disk write and a single notification at the end. */
    public void batch(Runnable work) {
        batchDepth++;
        try {
            work.run();
        } finally {
            batchDepth--;
            if (batchDepth == 0) flush();
        }
    }

    private void saveProducts() {
        List<String[]> rows = new ArrayList<>();
        for (Product p : products) rows.add(p.toCsv());
        fm.writeCsv(PRODUCTS, Product.CSV_HEADER, rows);
    }

    // -------------------------------------------------------------- queries

    public int size() { return products.size(); }

    public List<Product> getAllProducts() { return Collections.unmodifiableList(products); }

    public Product findById(int id) {
        for (Product p : products) if (p.getProductId() == id) return p;
        return null;
    }

    public Product findByBarcode(String code) {
        if (code == null || code.isBlank()) return null;
        String c = code.trim();
        for (Product p : products) if (!p.getBarcode().isEmpty() && p.getBarcode().equals(c)) return p;
        return null;
    }

    public Product findByName(String name) {
        if (name == null) return null;
        for (Product p : products) if (p.getProductName().equalsIgnoreCase(name.trim())) return p;
        return null;
    }

    public int nextProductId() {
        return products.stream().mapToInt(Product::getProductId).max().orElse(0) + 1;
    }

    /** Case-insensitive match on name, category, supplier, barcode or id. Blank returns everything. */
    public List<Product> search(String query) {
        String q = query == null ? "" : query.trim().toLowerCase();
        if (q.isEmpty()) return new ArrayList<>(products);
        List<Product> out = new ArrayList<>();
        for (Product p : products) {
            if (p.getProductName().toLowerCase().contains(q) || p.getCategory().toLowerCase().contains(q)
                    || p.getSupplier().toLowerCase().contains(q) || p.getBarcode().contains(q)
                    || String.valueOf(p.getProductId()).equals(q)) out.add(p);
        }
        return out;
    }

    public List<String> categories() { return distinct(Product::getCategory); }

    public List<String> suppliers() { return distinct(Product::getSupplier); }

    private List<String> distinct(java.util.function.Function<Product, String> f) {
        TreeMap<String, String> m = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Product p : products) {
            String v = f.apply(p);
            if (v != null && !v.isBlank()) m.putIfAbsent(v, v);
        }
        return new ArrayList<>(m.values());
    }

    /** Low or sold out, most urgent first (sold out, then lowest fill relative to the reorder level). */
    public List<Product> needsRestock() {
        return products.stream().filter(Product::needsRestock)
                .sorted(Comparator.comparingInt((Product p) -> p.isOutOfStock() ? 0 : 1)
                        .thenComparingDouble(p -> p.getReorderLevel() == 0 ? 0 : (double) p.getQuantity() / p.getReorderLevel())
                        .thenComparing(Product::getProductName))
                .collect(Collectors.toList());
    }

    /** Past their date and still on the shelf. */
    public List<Product> expired() {
        return products.stream().filter(p -> p.isExpired() && p.getQuantity() > 0)
                .sorted(Comparator.comparingLong(Product::daysToExpiry)).collect(Collectors.toList());
    }

    /** Within {@code days} of expiry and still on the shelf, soonest first. */
    public List<Product> expiringSoon(int days) {
        return products.stream().filter(p -> p.isExpiringSoon(days) && p.getQuantity() > 0)
                .sorted(Comparator.comparingLong(Product::daysToExpiry)).collect(Collectors.toList());
    }

    public int totalUnits() {
        int n = 0;
        for (Product p : products) n += Math.max(0, p.getQuantity());
        return n;
    }

    public double totalCostValue() {
        double v = 0;
        for (Product p : products) v += p.costValue();
        return v;
    }

    public double totalRetailValue() {
        double v = 0;
        for (Product p : products) v += p.retailValue();
        return v;
    }

    // ------------------------------------------------------ catalogue edits

    private void validate(Product p, Product ignore) {
        if (p.getProductName().isBlank()) throw new IllegalArgumentException("Product name is required.");
        if (p.getCategory().isBlank()) throw new IllegalArgumentException("Category is required.");
        if (p.getPrice() < 0) throw new IllegalArgumentException("Selling price cannot be negative.");
        if (p.getCostPrice() < 0) throw new IllegalArgumentException("Cost price cannot be negative.");
        if (p.getQuantity() < 0) throw new IllegalArgumentException("Stock cannot be negative.");
        if (p.getReorderLevel() < 0) throw new IllegalArgumentException("Reorder level cannot be negative.");
        for (Product o : products) {
            if (o == ignore) continue;
            if (o.getProductName().equalsIgnoreCase(p.getProductName()))
                throw new IllegalArgumentException("A product called \"" + o.getProductName() + "\" already exists.");
            if (!p.getBarcode().isEmpty() && p.getBarcode().equals(o.getBarcode()))
                throw new IllegalArgumentException("Barcode " + p.getBarcode() + " is already used by \"" + o.getProductName() + "\".");
        }
    }

    public Product addProduct(Product p) {
        if (findById(p.getProductId()) != null)
            throw new IllegalArgumentException("A product with ID " + p.getProductId() + " already exists.");
        validate(p, null);
        products.add(p);
        if (p.getQuantity() > 0) {
            record(p, "OPENING", p.getQuantity(), "Opening stock", actor.get());
        }
        log.log(actor.get(), "PRODUCT_ADDED", p.getProductName() + " (#" + p.getProductId() + ")");
        changed();
        return p;
    }

    public Product updateProduct(int id, String name, String category, String supplier, double cost, double price,
                                 int reorderLevel, LocalDate expiry, String barcode) {
        Product p = findById(id);
        if (p == null) throw new IllegalArgumentException("That product no longer exists.");
        Product probe = new Product(id, name, category, supplier, cost, price, p.getQuantity(), reorderLevel, expiry);
        probe.setBarcode(barcode);
        validate(probe, p);
        String before = p.getProductName();
        double oldPrice = p.getPrice();
        p.setProductName(probe.getProductName());
        p.setCategory(probe.getCategory());
        p.setSupplier(probe.getSupplier());
        p.setCostPrice(cost);
        p.setPrice(price);
        p.setReorderLevel(reorderLevel);
        p.setExpiryDate(expiry);
        p.setBarcode(probe.getBarcode());
        log.log(actor.get(), "PRODUCT_UPDATED", before + (oldPrice != price
                ? " (price " + Fmt.money(oldPrice) + " -> " + Fmt.money(price) + ")" : ""));
        changed();
        return p;
    }

    /** Records the latest purchase cost without touching anything else. */
    public void updateCost(int id, double cost) {
        Product p = findById(id);
        if (p == null || cost < 0) return;
        p.setCostPrice(cost);
        changed();
    }

    public boolean deleteProduct(int id) {
        Product p = findById(id);
        if (p == null) return false;
        products.remove(p);
        log.log(actor.get(), "PRODUCT_DELETED", p.getProductName() + " (#" + id + ")");
        changed();
        return true;
    }

    // -------------------------------------------------------- stock changes

    public void adjustStock(int id, int delta, String type, String note) {
        adjustStock(id, delta, type, note, actor.get());
    }

    /** Adds (positive) or removes (negative) units and writes a ledger entry. Stock can never go below zero. */
    public void adjustStock(int id, int delta, String type, String note, String user) {
        Product p = findById(id);
        if (p == null) throw new IllegalArgumentException("That product no longer exists.");
        if (delta == 0) throw new IllegalArgumentException("Enter a quantity greater than zero.");
        int after = p.getQuantity() + delta;
        if (after < 0)
            throw new IllegalArgumentException("Only " + p.getQuantity() + " in stock - cannot remove " + (-delta) + ".");
        p.setQuantity(after);
        record(p, type, delta, note, user);
        changed();
    }

    /** Sets the quantity to a counted value. Returns the difference (0 means nothing changed). */
    public int setCount(int id, int counted, String note) {
        Product p = findById(id);
        if (p == null) throw new IllegalArgumentException("That product no longer exists.");
        if (counted < 0) throw new IllegalArgumentException("Counted quantity cannot be negative.");
        int diff = counted - p.getQuantity();
        if (diff == 0) return 0;
        p.setQuantity(counted);
        record(p, "CORRECTION", diff, note == null || note.isBlank() ? "Stock count" : note, actor.get());
        changed();
        return diff;
    }

    /** Removes every expired unit from stock. Returns the number of units written off. */
    public int writeOffExpired() {
        int[] units = {0};
        batch(() -> {
            for (Product p : expired()) {
                units[0] += p.getQuantity();
                adjustStock(p.getProductId(), -p.getQuantity(), "WRITE_OFF", "Expired " + Fmt.date(p.getExpiryDate()));
            }
        });
        return units[0];
    }

    private void record(Product p, String type, int change, String note, String user) {
        StockMovement m = new StockMovement(LocalDateTime.now(), p.getProductId(), p.getProductName(), type,
                change, p.getQuantity(), user == null ? "system" : user, note);
        movements.add(m);
        fm.appendCsv(MOVES, StockMovement.CSV_HEADER, m.toCsv());
    }

    /** The whole ledger, newest first. */
    public List<StockMovement> getMovements() {
        List<StockMovement> copy = new ArrayList<>(movements);
        Collections.reverse(copy);
        return copy;
    }

    public List<StockMovement> movementsFor(int productId) {
        List<StockMovement> out = new ArrayList<>();
        for (int i = movements.size() - 1; i >= 0; i--)
            if (movements.get(i).getProductId() == productId) out.add(movements.get(i));
        return out;
    }

    // --------------------------------------------------------- import / export

    public void exportCsv(File file) {
        List<String[]> rows = new ArrayList<>();
        for (Product p : products) rows.add(p.toCsv());
        FileManager.writeCsvFile(file.toPath(), Product.CSV_HEADER, rows);
    }

    /**
     * Adds new products and updates existing ones (matched by ID). A changed quantity is booked as a
     * stock-count correction so the ledger stays honest. Returns a human-readable summary.
     */
    public String importCsv(File file) {
        List<String[]> rows;
        try {
            rows = FileManager.readCsvFile(file.toPath());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Could not read that file: " + e.getMessage());
        }
        if (rows.size() < 2) throw new IllegalArgumentException("The file has no product rows.");
        Map<String, Integer> col = new HashMap<>();
        String[] head = rows.get(0);
        for (int i = 0; i < head.length; i++) col.put(head[i].trim().toLowerCase().replace(" ", ""), i);
        Integer cName = first(col, "name", "product", "productname");
        Integer cPrice = first(col, "price", "sellingprice", "retailprice");
        if (cName == null || cPrice == null)
            throw new IllegalArgumentException("The first row must be a header with at least \"name\" and \"price\" columns.");
        Integer cId = first(col, "id", "productid"), cCat = first(col, "category"), cSup = first(col, "supplier"),
                cCost = first(col, "cost", "costprice"), cQty = first(col, "quantity", "qty", "stock", "onhand"),
                cRe = first(col, "reorder", "reorderlevel"), cExp = first(col, "expiry", "expirydate"),
                cBar = first(col, "barcode");

        int[] n = {0, 0, 0, 0}; // added, updated, unchanged, skipped
        List<String> problems = new ArrayList<>();
        batch(() -> {
            for (int i = 1; i < rows.size(); i++) {
                String[] r = rows.get(i);
                try {
                    String name = cell(r, cName);
                    double price = Fmt.parseDouble(cell(r, cPrice), "Price");
                    String cat = cell(r, cCat).isEmpty() ? "General" : cell(r, cCat);
                    String sup = cell(r, cSup);
                    double cost = cell(r, cCost).isEmpty() ? Math.round(price * 0.75 * 100) / 100.0 : Fmt.parseDouble(cell(r, cCost), "Cost");
                    int qty = cell(r, cQty).isEmpty() ? 0 : Fmt.parseInt(cell(r, cQty), "Quantity");
                    int reorder = cell(r, cRe).isEmpty() ? 10 : Fmt.parseInt(cell(r, cRe), "Reorder level");
                    LocalDate exp = Fmt.parseDate(cell(r, cExp));
                    String bar = cell(r, cBar);
                    Product existing = cell(r, cId).isEmpty() ? null : findById(Fmt.parseInt(cell(r, cId), "ID"));
                    if (existing == null && cell(r, cId).isEmpty()) existing = findByName(name);
                    if (existing == null) {
                        int id = cell(r, cId).isEmpty() ? nextProductId() : Fmt.parseInt(cell(r, cId), "ID");
                        Product p = new Product(id, name, cat, sup, cost, price, qty, reorder, exp);
                        p.setBarcode(bar);
                        addProduct(p);
                        n[0]++;
                    } else {
                        boolean same = existing.getProductName().equals(name.trim()) && existing.getCategory().equals(cat)
                                && existing.getSupplier().equals(sup) && existing.getCostPrice() == cost
                                && existing.getPrice() == price && existing.getReorderLevel() == reorder
                                && Objects.equals(existing.getExpiryDate(), exp) && existing.getBarcode().equals(bar)
                                && existing.getQuantity() == qty;
                        if (same) {
                            n[2]++;
                            continue;
                        }
                        updateProduct(existing.getProductId(), name, cat, sup, cost, price, reorder, exp, bar);
                        if (cQty != null && qty != existing.getQuantity()) setCount(existing.getProductId(), qty, "CSV import");
                        n[1]++;
                    }
                } catch (RuntimeException ex) {
                    n[3]++;
                    if (problems.size() < 5) problems.add("Row " + (i + 1) + ": " + ex.getMessage());
                }
            }
        });
        log.log(actor.get(), "PRODUCTS_IMPORTED", file.getName() + ": " + n[0] + " added, " + n[1] + " updated");
        StringBuilder sb = new StringBuilder(n[0] + " added, " + n[1] + " updated, " + n[2] + " unchanged, " + n[3] + " skipped.");
        for (String p : problems) sb.append('\n').append(p);
        if (n[3] > problems.size()) sb.append("\n...and ").append(n[3] - problems.size()).append(" more.");
        return sb.toString();
    }

    private static Integer first(Map<String, Integer> col, String... names) {
        for (String n : names) if (col.containsKey(n)) return col.get(n);
        return null;
    }

    private static String cell(String[] r, Integer i) {
        return i == null || i >= r.length || r[i] == null ? "" : r[i].trim();
    }
}
