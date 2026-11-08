package service;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import model.MovementType;
import model.Product;
import model.StockMovement;
import model.StockStatus;
import util.AppSettings;
import util.Csv;
import util.FileManager;
import util.Formats;

/**
 * The catalogue and the stock ledger. Every change to a quantity goes through {@link #changeStock}, which
 * writes a ledger line, so the ledger always explains why a number is what it is.
 */
public class Inventory {

    public enum StatusFilter {
        ALL("All statuses"), OK("In stock"), RESTOCK("Needs restock"), LOW("Low stock"), OUT("Out of stock"),
        EXPIRING("Expiring soon"), EXPIRED("Expired");

        private final String label;
        StatusFilter(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    public record ImportResult(int added, int updated, List<String> errors) {}

    /** A suggested purchase line for the reorder planner. */
    public record ReorderLine(Product product, int suggestedQty, double estimatedCost) {}

    /** Typical supermarket departments offered in the category picker (you can still type any new one). */
    public static final String[] SUGGESTED_CATEGORIES = {
        "Fruits", "Vegetables", "Dairy", "Bakery", "Meat & Poultry", "Fish & Seafood", "Eggs", "Frozen Foods",
        "Rice & Grains", "Pantry", "Spices & Condiments", "Oils & Ghee", "Canned & Packaged", "Breakfast & Cereal",
        "Snacks & Confectionery", "Biscuits & Sweets", "Beverages", "Tea & Coffee", "Juices & Soft Drinks",
        "Baby Care", "Health & Pharmacy", "Personal Care", "Beauty", "Household & Cleaning", "Kitchenware",
        "Pet Supplies", "Stationery", "Electronics", "Other"
    };

    /** Existing categories first, then any standard ones not yet used. */
    public List<String> getCategoryChoices() {
        List<String> out = getAllCategories();
        for (String c : SUGGESTED_CATEGORIES) {
            boolean have = false;
            for (String e : out) if (e.equalsIgnoreCase(c)) have = true;
            if (!have) out.add(c);
        }
        return out;
    }

    private final FileManager files;
    private final AppSettings settings;
    private final List<Product> products = new ArrayList<>();
    private final List<StockMovement> movements = new ArrayList<>();

    public Inventory(FileManager files, AppSettings settings) {
        this.files = files;
        this.settings = settings;
    }

    public void load() {
        products.clear();
        products.addAll(files.loadProducts());
        products.sort(Comparator.comparingInt(Product::getProductId));
        movements.clear();
        movements.addAll(files.loadMovements());
    }

    private void save() { files.saveProducts(products); }

    // ============================================================ lookup

    public int size() { return products.size(); }

    public List<Product> getAllProducts() { return Collections.unmodifiableList(products); }

    public Product findById(int id) {
        for (Product p : products) if (p.getProductId() == id) return p;
        return null;
    }

    public Product findByName(String name) {
        for (Product p : products) if (p.getProductName().equalsIgnoreCase(name.trim())) return p;
        return null;
    }

    public List<String> getAllCategories() {
        TreeSet<String> set = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Product p : products) if (!p.getCategory().isEmpty()) set.add(p.getCategory());
        return new ArrayList<>(set);
    }

    public List<String> getAllSuppliers() {
        TreeSet<String> set = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Product p : products) if (!p.getSupplier().isEmpty()) set.add(p.getSupplier());
        return new ArrayList<>(set);
    }

    /** Free-text search over name, category, supplier and id, plus optional category and status filters. */
    public List<Product> search(String text, String category, StatusFilter filter) {
        String q = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        StatusFilter f = filter == null ? StatusFilter.ALL : filter;
        int warn = settings.getExpiryWarnDays();
        List<Product> out = new ArrayList<>();
        for (Product p : products) {
            if (category != null && !category.isEmpty() && !p.getCategory().equalsIgnoreCase(category)) continue;
            if (!q.isEmpty() && !(p.getProductName().toLowerCase(Locale.ROOT).contains(q)
                    || p.getCategory().toLowerCase(Locale.ROOT).contains(q)
                    || p.getSupplier().toLowerCase(Locale.ROOT).contains(q)
                    || String.valueOf(p.getProductId()).equals(q))) continue;
            if (!matches(p, f, warn)) continue;
            out.add(p);
        }
        return out;
    }

    private static boolean matches(Product p, StatusFilter f, int warn) {
        return switch (f) {
            case ALL -> true;
            case OK -> p.getStatus(warn) == StockStatus.OK;
            case RESTOCK -> !p.isExpired() && p.needsRestock();
            case LOW -> p.isLowStock() && !p.isExpired();
            case OUT -> p.isOutOfStock() && !p.isExpired();
            case EXPIRING -> p.isExpiringWithin(warn);
            case EXPIRED -> p.isExpired();
        };
    }

    /** Items that are out of stock or at / below their reorder level (expired stock is a write-off, not a reorder). */
    public List<Product> checkLowStockProducts() {
        List<Product> out = new ArrayList<>();
        for (Product p : products) if (!p.isExpired() && p.needsRestock()) out.add(p);
        out.sort(Comparator.comparingInt(Product::getQuantity));
        return out;
    }

    public List<Product> getExpiringSoon() {
        List<Product> out = new ArrayList<>();
        for (Product p : products) if (p.isExpiringWithin(settings.getExpiryWarnDays()) && p.getQuantity() > 0) out.add(p);
        out.sort(Comparator.comparing(Product::getExpiryDate));
        return out;
    }

    public List<Product> getExpired() {
        List<Product> out = new ArrayList<>();
        for (Product p : products) if (p.isExpired()) out.add(p);
        out.sort(Comparator.comparing(Product::getExpiryDate));
        return out;
    }

    public double stockValueAtCost() {
        double v = 0;
        for (Product p : products) v += p.stockValueAtCost();
        return Formats.round2(v);
    }

    public double stockValueAtRetail() {
        double v = 0;
        for (Product p : products) v += p.stockValueAtRetail();
        return Formats.round2(v);
    }

    // ============================================================ catalogue changes

    private void validate(Product p, boolean checkQuantity) {
        if (p.getProductName().isBlank()) throw new ServiceException("Product name is required.");
        if (p.getCategory().isBlank()) throw new ServiceException("Category is required.");
        if (!(p.getPrice() > 0) || Double.isInfinite(p.getPrice())) throw new ServiceException("Selling price must be greater than zero.");
        if (p.getCost() < 0 || Double.isNaN(p.getCost())) throw new ServiceException("Cost price cannot be negative.");
        if (p.getCost() > p.getPrice()) {
            throw new ServiceException("The cost price is higher than the selling price. Check the figures.");
        }
        if (checkQuantity && p.getQuantity() < 0) throw new ServiceException("Quantity cannot be negative.");
        if (p.getReorderLevel() < 0) throw new ServiceException("Reorder level cannot be negative.");
        if (p.getProductName().length() > 80) throw new ServiceException("Product name is too long (80 characters max).");
    }

    private int nextId() {
        int max = 0;
        for (Product p : products) max = Math.max(max, p.getProductId());
        return max + 1;
    }

    private int nextMovementId() {
        int max = 0;
        for (StockMovement m : movements) max = Math.max(max, m.getId());
        return max + 1;
    }

    /** Adds a product (id 0 = assign the next free id) and records its opening stock in the ledger. */
    public Product addProduct(Product p, String actor) {
        validate(p, true);
        Product dup = findByName(p.getProductName());
        if (dup != null) throw new ServiceException("A product named \"" + dup.getProductName() + "\" already exists.");
        if (p.getProductId() <= 0 || findById(p.getProductId()) != null) p.setProductId(nextId());
        products.add(p);
        products.sort(Comparator.comparingInt(Product::getProductId));
        save();
        if (p.getQuantity() > 0) record(p, MovementType.INITIAL, p.getQuantity(), "Opening stock", actor);
        return p;
    }

    /** Applies edited details. The stock quantity is deliberately not changed here; that goes through the ledger. */
    public void updateProduct(Product edited) {
        Product p = findById(edited.getProductId());
        if (p == null) throw new ServiceException("That product no longer exists.");
        validate(edited, false);
        Product dup = findByName(edited.getProductName());
        if (dup != null && dup.getProductId() != p.getProductId()) {
            throw new ServiceException("A product named \"" + dup.getProductName() + "\" already exists.");
        }
        p.setProductName(edited.getProductName());
        p.setCategory(edited.getCategory());
        p.setSupplier(edited.getSupplier());
        p.setPrice(edited.getPrice());
        p.setCost(edited.getCost());
        p.setReorderLevel(edited.getReorderLevel());
        p.setExpiryDate(edited.getExpiryDate());
        save();
    }

    public boolean deleteProduct(int id) {
        boolean removed = products.removeIf(p -> p.getProductId() == id);
        if (removed) save();
        return removed;
    }

    // ============================================================ stock ledger

    private StockMovement record(Product p, MovementType type, int change, String note, String actor) {
        StockMovement m = new StockMovement(nextMovementId(), LocalDateTime.now().withNano(0), p.getProductId(),
                p.getProductName(), type, change, p.getQuantity(), actor, note);
        movements.add(m);
        files.appendMovement(m);
        return m;
    }

    private Product require(int id) {
        Product p = findById(id);
        if (p == null) throw new ServiceException("That product no longer exists.");
        return p;
    }

    /** The single place a quantity changes. Throws if the result would be negative. */
    public void changeStock(int productId, int delta, MovementType type, String note, String actor) {
        Product p = require(productId);
        if (p.getQuantity() + delta < 0) {
            throw new ServiceException("Not enough " + p.getProductName() + " in stock.");
        }
        p.setQuantity(p.getQuantity() + delta);
        save();
        record(p, type, delta, note, actor);
    }

    public void receiveStock(int productId, int qty, Double newUnitCost, String note, String actor) {
        if (qty <= 0) throw new ServiceException("Quantity received must be greater than zero.");
        Product p = require(productId);
        if (newUnitCost != null) {
            if (newUnitCost < 0) throw new ServiceException("Unit cost cannot be negative.");
            p.setCost(newUnitCost);
        }
        changeStock(productId, qty, MovementType.RECEIVE, note, actor);
    }

    public void writeOff(int productId, int qty, String note, String actor) {
        if (qty <= 0) throw new ServiceException("Quantity to write off must be greater than zero.");
        Product p = require(productId);
        if (qty > p.getQuantity()) {
            throw new ServiceException("Not enough stock to write off: only " + p.getQuantity() + " of " + p.getProductName() + ".");
        }
        changeStock(productId, -qty, MovementType.WASTAGE, note, actor);
    }

    /** Records a physical count: sets the quantity and logs the difference. */
    public void setCount(int productId, int counted, String note, String actor) {
        if (counted < 0) throw new ServiceException("Counted quantity cannot be negative.");
        Product p = require(productId);
        if (counted == p.getQuantity()) throw new ServiceException("The counted quantity is the same as the current stock.");
        changeStock(productId, counted - p.getQuantity(), MovementType.CORRECTION, note, actor);
    }

    /** Newest first. */
    public List<StockMovement> getMovements() {
        List<StockMovement> copy = new ArrayList<>(movements);
        copy.sort(Comparator.comparing(StockMovement::getDateTime).thenComparingInt(StockMovement::getId).reversed());
        return copy;
    }

    public List<StockMovement> getMovements(int productId) {
        List<StockMovement> out = new ArrayList<>();
        for (StockMovement m : getMovements()) if (m.getProductId() == productId) out.add(m);
        return out;
    }

    // ============================================================ reorder planner

    /**
     * Suggests what to buy: for every product at or below its reorder level, enough to reach twice the level
     * (at least 1 unit more than the level). Sorted by supplier so each supplier gets one order.
     */
    public List<ReorderLine> suggestReorder() {
        List<ReorderLine> out = new ArrayList<>();
        for (Product p : checkLowStockProducts()) {
            int target = Math.max(p.getReorderLevel() * 2, p.getReorderLevel() + 1);
            int qty = Math.max(1, target - p.getQuantity());
            out.add(new ReorderLine(p, qty, Formats.round2(qty * p.getCost())));
        }
        out.sort(Comparator.comparing((ReorderLine l) -> l.product().getSupplier().isEmpty() ? "~" : l.product().getSupplier().toLowerCase())
                .thenComparing(l -> l.product().getProductName()));
        return out;
    }

    // ============================================================ CSV import / export

    public List<String[]> exportRows() {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Name", "Category", "Supplier", "Price", "Cost", "Quantity", "Reorder Level", "Expiry"});
        for (Product p : products) {
            rows.add(new String[]{p.getProductName(), p.getCategory(), p.getSupplier(), String.valueOf(p.getPrice()),
                    String.valueOf(p.getCost()), String.valueOf(p.getQuantity()), String.valueOf(p.getReorderLevel()),
                    p.getExpiryDate() == null ? "" : p.getExpiryDate().toString()});
        }
        return rows;
    }

    private static int colIndex(Map<String, Integer> idx, String... names) {
        for (String n : names) if (idx.containsKey(n)) return idx.get(n);
        return -1;
    }

    private static String cell(String[] r, int i) { return i < 0 || i >= r.length ? "" : r[i].trim(); }

    /**
     * Imports products from CSV. Matching rows (by name) are updated; their quantity is reconciled through
     * the ledger. Bad rows are skipped and reported with their line number.
     */
    public ImportResult importCsv(Path file, String actor) {
        List<String[]> rows;
        try {
            rows = Csv.read(file);
        } catch (java.io.IOException e) {
            throw new ServiceException("Could not read the file: " + e.getMessage());
        }
        if (rows.isEmpty()) throw new ServiceException("The file is empty.");
        Map<String, Integer> idx = new HashMap<>();
        String[] header = rows.get(0);
        for (int i = 0; i < header.length; i++) idx.put(header[i].trim().toLowerCase(Locale.ROOT), i);
        int cName = colIndex(idx, "name", "product", "product name");
        int cCat = colIndex(idx, "category");
        int cPrice = colIndex(idx, "price", "selling price");
        if (cName < 0 || cCat < 0 || cPrice < 0) {
            throw new ServiceException("Missing required column(s). The first row must contain: Name, Category, Price.");
        }
        int cSup = colIndex(idx, "supplier");
        int cCost = colIndex(idx, "cost", "cost price");
        int cQty = colIndex(idx, "quantity", "qty", "stock");
        int cRe = colIndex(idx, "reorder level", "reorder", "reorder_level");
        int cExp = colIndex(idx, "expiry", "expiry date", "expires");

        int added = 0, updated = 0;
        List<String> errors = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            String[] r = rows.get(i);
            int line = i + 1;
            try {
                String name = cell(r, cName);
                double price = Double.parseDouble(cell(r, cPrice).replace(",", ""));
                String costText = cell(r, cCost);
                double cost = costText.isEmpty() ? 0 : Double.parseDouble(costText.replace(",", ""));
                String qtyText = cell(r, cQty);
                Integer qty = qtyText.isEmpty() ? null : Integer.parseInt(qtyText);
                String reText = cell(r, cRe);
                int reorder = reText.isEmpty() ? settings.getDefaultReorderLevel() : Integer.parseInt(reText);
                String expText = cell(r, cExp);
                LocalDate expiry = expText.isEmpty() ? null : LocalDate.parse(expText);
                Product existing = findByName(name);
                if (existing == null) {
                    addProduct(new Product(0, name, cell(r, cCat), cell(r, cSup), price, cost, qty == null ? 0 : qty, reorder, expiry), actor);
                    added++;
                } else {
                    Product edit = existing.copy();
                    edit.setCategory(cell(r, cCat));
                    if (cSup >= 0) edit.setSupplier(cell(r, cSup));
                    edit.setPrice(price);
                    if (cCost >= 0 && !costText.isEmpty()) edit.setCost(cost);
                    if (cRe >= 0 && !reText.isEmpty()) edit.setReorderLevel(reorder);
                    if (cExp >= 0) edit.setExpiryDate(expiry);
                    updateProduct(edit);
                    if (qty != null && qty != existing.getQuantity()) {
                        if (qty < 0) throw new ServiceException("Quantity cannot be negative.");
                        changeStock(existing.getProductId(), qty - existing.getQuantity(), MovementType.CORRECTION, "CSV import", actor);
                    }
                    updated++;
                }
            } catch (NumberFormatException e) {
                errors.add("Line " + line + ": a number is not valid (" + cell(r, cName) + ")");
            } catch (DateTimeParseException e) {
                errors.add("Line " + line + ": expiry date must look like 2026-12-31 (" + cell(r, cName) + ")");
            } catch (ServiceException e) {
                errors.add("Line " + line + ": " + e.getMessage());
            }
        }
        return new ImportResult(added, updated, errors);
    }

    /** One-off migration of the original products.txt (id,name,category,price,quantity). Cost is unknown there, so it starts at 0. */
    public int importLegacy(Path file, String actor) {
        int n = 0;
        try {
            for (String line : java.nio.file.Files.readAllLines(file, java.nio.charset.StandardCharsets.UTF_8)) {
                if (line.isBlank()) continue;
                String[] c = Csv.parseLine(line);
                if (c.length < 5) continue;
                try {
                    double price = Double.parseDouble(c[3].trim());
                    addProduct(new Product(Integer.parseInt(c[0].trim()), c[1], c[2], "", price, 0,
                            Integer.parseInt(c[4].trim()), settings.getDefaultReorderLevel(), null), actor);
                    n++;
                } catch (RuntimeException ignored) {
                    // skip a line that cannot be read
                }
            }
        } catch (java.io.IOException e) {
            throw new ServiceException("Could not read " + file.getFileName() + ": " + e.getMessage());
        }
        return n;
    }
}
