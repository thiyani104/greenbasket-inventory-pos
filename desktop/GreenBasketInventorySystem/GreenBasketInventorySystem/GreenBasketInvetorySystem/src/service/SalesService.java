package service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import model.*;
import util.FileManager;
import util.Fmt;

/** Checkout, voiding and querying sales history. */
public class SalesService {

    private static final String SALES = "sales.csv";
    private static final String ITEMS = "sale_items.csv";

    public static final String[] PAYMENT_METHODS = {"Cash", "Card", "Mobile Pay"};

    private final FileManager fm;
    private final Inventory inventory;
    private final ActivityLog log;
    private final List<Sale> sales = new ArrayList<>();
    private final List<Runnable> listeners = new ArrayList<>();

    public SalesService(FileManager fm, Inventory inventory, ActivityLog log) {
        this.fm = fm;
        this.inventory = inventory;
        this.log = log;
        load();
    }

    private void load() {
        Map<Integer, Sale> byId = new LinkedHashMap<>();
        for (String[] r : fm.readCsv(SALES)) {
            try {
                Sale s = new Sale(Integer.parseInt(r[0]), Fmt.parseDateTime(r[1]), r[2], r[3], r[4],
                        Double.parseDouble(r[5]), Double.parseDouble(r[6]), Double.parseDouble(r[7]),
                        Double.parseDouble(r[8]), Double.parseDouble(r[9]), r[10]);
                byId.put(s.getId(), s);
            } catch (RuntimeException ignored) { }
        }
        for (String[] r : fm.readCsv(ITEMS)) {
            try {
                Sale s = byId.get(Integer.parseInt(r[0]));
                if (s != null) {
                    s.getItems().add(new SaleItem(Integer.parseInt(r[1]), r[2], r[3], Integer.parseInt(r[4]),
                            Double.parseDouble(r[5]), Double.parseDouble(r[6])));
                }
            } catch (RuntimeException ignored) { }
        }
        sales.addAll(byId.values());
        sales.sort(Comparator.comparing(Sale::getTime));
    }

    public void addListener(Runnable r) { listeners.add(r); }
    public void removeListener(Runnable r) { listeners.remove(r); }

    private void fire() { for (Runnable r : new ArrayList<>(listeners)) r.run(); }

    public int nextSaleId() {
        return sales.stream().mapToInt(Sale::getId).max().orElse(1000) + 1;
    }

    /**
     * Completes a sale: re-checks stock, deducts it, and stores the receipt data.
     * Nothing is changed if any line cannot be fulfilled.
     */
    public Sale checkout(Cart cart, String cashier, String customer, String payment, double tendered) {
        if (cart.isEmpty()) throw new IllegalArgumentException("The cart is empty.");
        for (SaleItem line : cart.getLines()) {
            Product p = inventory.findById(line.getProductId());
            if (p == null) throw new IllegalArgumentException("\"" + line.getName() + "\" is no longer in the catalogue.");
            if (p.isExpired()) throw new IllegalArgumentException("\"" + p.getProductName() + "\" has expired.");
            if (line.getQuantity() > p.getQuantity())
                throw new IllegalArgumentException("Only " + p.getQuantity() + " of \"" + p.getProductName() + "\" left in stock.");
        }
        double total = cart.total();
        if ("Cash".equals(payment) && tendered + 0.005 < total)
            throw new IllegalArgumentException("Cash received (" + Fmt.money(tendered) + ") is less than the total (" + Fmt.money(total) + ").");
        if (!"Cash".equals(payment)) tendered = total;

        Sale sale = new Sale(nextSaleId(), LocalDateTime.now(), cashier, customer == null ? "" : customer.trim(),
                payment, cart.subtotal(), cart.discount(), cart.tax(), total, tendered, Sale.COMPLETED);
        for (SaleItem l : cart.getLines()) {
            sale.getItems().add(new SaleItem(l.getProductId(), l.getName(), l.getCategory(),
                    l.getQuantity(), l.getUnitPrice(), l.getUnitCost()));
        }
        inventory.batch(() -> {
            for (SaleItem l : sale.getItems()) {
                inventory.adjustStock(l.getProductId(), -l.getQuantity(), "SALE", "Sale #" + sale.getId(), cashier);
            }
        });
        sales.add(sale);
        fm.appendCsv(SALES, Sale.CSV_HEADER, sale.toCsv());
        for (SaleItem i : sale.getItems()) {
            fm.appendCsv(ITEMS, Sale.ITEM_HEADER, new String[]{String.valueOf(sale.getId()),
                    String.valueOf(i.getProductId()), i.getName(), i.getCategory(),
                    String.valueOf(i.getQuantity()), String.valueOf(i.getUnitPrice()), String.valueOf(i.getUnitCost())});
        }
        log.log(cashier, "SALE", "#" + sale.getId() + " " + Fmt.money(sale.getTotal()) + " (" + sale.itemCount() + " items)");
        fire();
        return sale;
    }

    /** Adds already-completed sales (e.g. demo history) without touching stock. */
    public void importHistorical(List<Sale> history) {
        for (Sale s : history) {
            sales.add(s);
            fm.appendCsv(SALES, Sale.CSV_HEADER, s.toCsv());
            for (SaleItem i : s.getItems()) {
                fm.appendCsv(ITEMS, Sale.ITEM_HEADER, new String[]{String.valueOf(s.getId()),
                        String.valueOf(i.getProductId()), i.getName(), i.getCategory(),
                        String.valueOf(i.getQuantity()), String.valueOf(i.getUnitPrice()), String.valueOf(i.getUnitCost())});
            }
        }
        sales.sort(Comparator.comparing(Sale::getTime));
        fire();
    }

    /** Cancels a sale and puts its items back on the shelf. */
    public void voidSale(Sale sale, String actor) {
        if (sale.isVoided()) throw new IllegalArgumentException("Sale #" + sale.getId() + " is already voided.");
        inventory.batch(() -> {
            for (SaleItem i : sale.getItems()) {
                if (inventory.findById(i.getProductId()) != null) {
                    inventory.adjustStock(i.getProductId(), i.getQuantity(), "VOID", "Void of sale #" + sale.getId(), actor);
                }
            }
        });
        sale.setStatus(Sale.VOIDED);
        List<String[]> rows = new ArrayList<>();
        for (Sale s : sales) rows.add(s.toCsv());
        fm.writeCsv(SALES, Sale.CSV_HEADER, rows);
        log.log(actor, "SALE_VOIDED", "#" + sale.getId() + " " + Fmt.money(sale.getTotal()));
        fire();
    }

    // -------------------------------------------------------------- queries

    /** Newest first. */
    public List<Sale> all() {
        List<Sale> copy = new ArrayList<>(sales);
        Collections.reverse(copy);
        return copy;
    }

    public List<Sale> between(LocalDate from, LocalDate to, String cashierOrNull) {
        return all().stream().filter(s -> {
            LocalDate d = s.getTime().toLocalDate();
            return (from == null || !d.isBefore(from)) && (to == null || !d.isAfter(to))
                    && (cashierOrNull == null || s.getCashier().equalsIgnoreCase(cashierOrNull));
        }).collect(Collectors.toList());
    }

    public Sale findById(int id) {
        for (Sale s : sales) if (s.getId() == id) return s;
        return null;
    }

    // ------------------------------------------------------------- receipts

    public static String receiptText(Sale s, String storeName) {
        int w = 42;
        StringBuilder sb = new StringBuilder();
        sb.append(center(storeName.toUpperCase(), w)).append('\n');
        sb.append(center("Fresh. Organic. Local.", w)).append('\n');
        sb.append("-".repeat(w)).append('\n');
        sb.append(String.format("Receipt #%d%s%n", s.getId(), s.isVoided() ? "   ** VOIDED **" : ""));
        sb.append("Date    : ").append(s.getTime().format(Fmt.DATE_TIME_SHORT)).append('\n');
        sb.append("Cashier : ").append(s.getCashier()).append('\n');
        if (!s.getCustomer().isBlank()) sb.append("Customer: ").append(s.getCustomer()).append('\n');
        sb.append("-".repeat(w)).append('\n');
        for (SaleItem i : s.getItems()) {
            String name = i.getName().length() > 22 ? i.getName().substring(0, 21) + "…" : i.getName();
            sb.append(String.format("%-22s %3d x %,9.2f%n", name, i.getQuantity(), i.getUnitPrice()));
            sb.append(String.format("%" + w + "s%n", String.format("%,.2f", i.lineTotal())));
        }
        sb.append("-".repeat(w)).append('\n');
        sb.append(line("Subtotal", s.getSubtotal(), w));
        if (s.getDiscount() > 0) sb.append(line("Discount", -s.getDiscount(), w));
        if (s.getTax() > 0) sb.append(line("Tax", s.getTax(), w));
        sb.append("=".repeat(w)).append('\n');
        sb.append(line("TOTAL (" + Fmt.currency() + ")", s.getTotal(), w));
        sb.append("Paid by : ").append(s.getPayment()).append('\n');
        if ("Cash".equals(s.getPayment())) {
            sb.append(line("Cash received", s.getTendered(), w));
            sb.append(line("Change", s.getChange(), w));
        }
        sb.append("-".repeat(w)).append('\n');
        sb.append(center("Thank you for shopping with us!", w)).append('\n');
        return sb.toString();
    }

    private static String line(String label, double v, int w) {
        String val = String.format("%,.2f", v);
        return label + " ".repeat(Math.max(1, w - label.length() - val.length())) + val + "\n";
    }

    private static String center(String s, int w) {
        int pad = Math.max(0, (w - s.length()) / 2);
        return " ".repeat(pad) + s;
    }
}
