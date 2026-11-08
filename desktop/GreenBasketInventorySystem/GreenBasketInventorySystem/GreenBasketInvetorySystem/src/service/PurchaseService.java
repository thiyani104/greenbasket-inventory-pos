package service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import model.*;
import util.FileManager;
import util.Fmt;

/** Purchase orders: raise them (or auto-suggest them from low stock), receive them into stock, cancel them. */
public class PurchaseService {

    private static final String ORDERS = "purchase_orders.csv";
    private static final String ITEMS = "purchase_items.csv";

    private final FileManager fm;
    private final Inventory inventory;
    private final ActivityLog log;
    private final List<PurchaseOrder> orders = new ArrayList<>();
    private final List<Runnable> listeners = new ArrayList<>();

    public PurchaseService(FileManager fm, Inventory inventory, ActivityLog log) {
        this.fm = fm;
        this.inventory = inventory;
        this.log = log;
        Map<Integer, PurchaseOrder> byId = new LinkedHashMap<>();
        for (String[] r : fm.readCsv(ORDERS)) {
            try {
                PurchaseOrder po = new PurchaseOrder(Integer.parseInt(r[0]), Fmt.parseDateTime(r[1]), r[2], r[3], r[4],
                        r[5].isBlank() ? null : Fmt.parseDateTime(r[5]), r.length > 6 ? r[6] : "");
                byId.put(po.getId(), po);
            } catch (RuntimeException ignored) { }
        }
        for (String[] r : fm.readCsv(ITEMS)) {
            try {
                PurchaseOrder po = byId.get(Integer.parseInt(r[0]));
                if (po != null) po.getItems().add(new PurchaseItem(Integer.parseInt(r[1]), r[2],
                        Integer.parseInt(r[3]), Double.parseDouble(r[4])));
            } catch (RuntimeException ignored) { }
        }
        orders.addAll(byId.values());
    }

    public void addListener(Runnable r) { listeners.add(r); }
    public void removeListener(Runnable r) { listeners.remove(r); }

    private void fire() { for (Runnable r : new ArrayList<>(listeners)) r.run(); }

    private void save() {
        List<String[]> rows = new ArrayList<>(), items = new ArrayList<>();
        for (PurchaseOrder po : orders) {
            rows.add(po.toCsv());
            for (PurchaseItem i : po.getItems()) {
                items.add(new String[]{String.valueOf(po.getId()), String.valueOf(i.getProductId()), i.getName(),
                        String.valueOf(i.getQuantity()), String.valueOf(i.getUnitCost())});
            }
        }
        fm.writeCsv(ORDERS, PurchaseOrder.CSV_HEADER, rows);
        fm.writeCsv(ITEMS, PurchaseOrder.ITEM_HEADER, items);
    }

    /** Newest first. */
    public List<PurchaseOrder> all() {
        List<PurchaseOrder> copy = new ArrayList<>(orders);
        Collections.reverse(copy);
        return copy;
    }

    public List<PurchaseOrder> open() {
        return all().stream().filter(PurchaseOrder::isOpen).collect(Collectors.toList());
    }

    public PurchaseOrder findById(int id) {
        for (PurchaseOrder po : orders) if (po.getId() == id) return po;
        return null;
    }

    /** Units already on order (not yet received) for a product. */
    public int pendingQuantity(int productId) {
        int n = 0;
        for (PurchaseOrder po : orders) {
            if (!po.isOpen()) continue;
            for (PurchaseItem i : po.getItems()) if (i.getProductId() == productId) n += i.getQuantity();
        }
        return n;
    }

    public PurchaseOrder create(String supplier, List<PurchaseItem> lines, String user, String note) {
        if (supplier == null || supplier.isBlank()) throw new IllegalArgumentException("Choose or type a supplier.");
        if (lines == null || lines.isEmpty()) throw new IllegalArgumentException("Add at least one product to the order.");
        for (PurchaseItem i : lines) {
            if (inventory.findById(i.getProductId()) == null)
                throw new IllegalArgumentException("\"" + i.getName() + "\" is no longer in the catalogue.");
            if (i.getQuantity() <= 0) throw new IllegalArgumentException("Quantity for \"" + i.getName() + "\" must be at least 1.");
            if (i.getUnitCost() < 0) throw new IllegalArgumentException("Cost for \"" + i.getName() + "\" cannot be negative.");
        }
        int id = orders.stream().mapToInt(PurchaseOrder::getId).max().orElse(5000) + 1;
        PurchaseOrder po = new PurchaseOrder(id, LocalDateTime.now(), user, supplier.trim(), PurchaseOrder.ORDERED, null, note);
        po.getItems().addAll(lines);
        orders.add(po);
        save();
        log.log(user, "PO_CREATED", "PO #" + id + " to " + po.getSupplier() + " - " + Fmt.money(po.total()));
        fire();
        return po;
    }

    /** Books the delivery into stock (one ledger entry per line) and records the latest cost price. */
    public void receive(PurchaseOrder po, String user) {
        if (!po.isOpen()) throw new IllegalArgumentException("PO #" + po.getId() + " is already " + po.getStatus().toLowerCase() + ".");
        inventory.batch(() -> {
            for (PurchaseItem i : po.getItems()) {
                if (inventory.findById(i.getProductId()) == null) continue; // product deleted since ordering
                inventory.adjustStock(i.getProductId(), i.getQuantity(), "RESTOCK", "PO #" + po.getId() + " from " + po.getSupplier(), user);
                inventory.updateCost(i.getProductId(), i.getUnitCost());
            }
        });
        po.markReceived();
        save();
        log.log(user, "PO_RECEIVED", "PO #" + po.getId() + " (" + po.units() + " units)");
        fire();
    }

    public void cancel(PurchaseOrder po, String user) {
        if (!po.isOpen()) throw new IllegalArgumentException("Only open orders can be cancelled.");
        po.markCancelled();
        save();
        log.log(user, "PO_CANCELLED", "PO #" + po.getId());
        fire();
    }

    /**
     * Suggested reorder lines grouped by supplier. Targets three times the reorder level, and
     * accounts for stock that is already on order so nothing is ordered twice.
     */
    public Map<String, List<PurchaseItem>> suggestions() {
        Map<String, List<PurchaseItem>> out = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Product p : inventory.needsRestock()) {
            int target = Math.max(p.getReorderLevel() * 3, p.getReorderLevel() + 10);
            int qty = target - p.getQuantity() - pendingQuantity(p.getProductId());
            if (qty <= 0) continue;
            String sup = p.getSupplier().isBlank() ? "Unassigned" : p.getSupplier();
            out.computeIfAbsent(sup, k -> new ArrayList<>())
                    .add(new PurchaseItem(p.getProductId(), p.getProductName(), qty, p.getCostPrice()));
        }
        return out;
    }
}
