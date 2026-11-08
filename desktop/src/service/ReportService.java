package service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import model.PaymentMethod;
import model.Product;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import model.Sale;
import model.SaleItem;
import util.Formats;

/** Pure aggregation over a list of sales. Voided sales are always ignored. */
public final class ReportService {

    public record Summary(int transactions, int items, double grossSales, double discounts,
                          double netSales, double tax, double profit, double averageBasket) {}

    public record ProductStat(String name, String category, int quantity, double revenue, double profit) {}

    public record Amount(String label, double value, int count) {}

    private ReportService() {}

    private static List<Sale> valid(List<Sale> sales) {
        List<Sale> out = new ArrayList<>();
        for (Sale s : sales) if (!s.isVoided()) out.add(s);
        return out;
    }

    public static Summary summarize(List<Sale> sales) {
        int tx = 0, items = 0;
        double gross = 0, disc = 0, net = 0, tax = 0, profit = 0;
        for (Sale s : valid(sales)) {
            tx++;
            items += s.getItemCount();
            gross += s.getSubtotal();
            disc += s.getDiscountAmount();
            net += s.getNetSales();
            tax += s.getTaxAmount();
            profit += s.getProfit();
        }
        return new Summary(tx, items, Formats.round2(gross), Formats.round2(disc), Formats.round2(net),
                Formats.round2(tax), Formats.round2(profit), tx == 0 ? 0 : Formats.round2(net / tx));
    }

    /** Net sales per calendar day, zero-filled so charts have no gaps. */
    public static Map<LocalDate, Double> netSalesByDay(List<Sale> sales, LocalDate from, LocalDate to) {
        Map<LocalDate, Double> out = new LinkedHashMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) out.put(d, 0.0);
        for (Sale s : valid(sales)) {
            LocalDate d = s.getDateTime().toLocalDate();
            if (out.containsKey(d)) out.merge(d, s.getNetSales(), Double::sum);
        }
        return out;
    }

    /** Line revenue per category, largest first (before bill-level discounts). */
    public static List<Amount> byCategory(List<Sale> sales) {
        Map<String, double[]> m = new LinkedHashMap<>();
        for (Sale s : valid(sales)) {
            for (SaleItem i : s.getItems()) {
                double[] v = m.computeIfAbsent(i.getCategory(), k -> new double[2]);
                v[0] += i.getLineTotal();
                v[1] += i.getQuantity();
            }
        }
        return toAmounts(m);
    }

    public static List<Amount> byCashier(List<Sale> sales) {
        Map<String, double[]> m = new LinkedHashMap<>();
        for (Sale s : valid(sales)) {
            double[] v = m.computeIfAbsent(s.getCashier(), k -> new double[2]);
            v[0] += s.getNetSales();
            v[1] += 1;
        }
        return toAmounts(m);
    }

    public static List<Amount> byPayment(List<Sale> sales) {
        Map<String, double[]> m = new LinkedHashMap<>();
        for (Sale s : valid(sales)) {
            double[] v = m.computeIfAbsent(s.getPayment().getLabel(), k -> new double[2]);
            v[0] += s.getTotal();
            v[1] += 1;
        }
        return toAmounts(m);
    }

    private static List<Amount> toAmounts(Map<String, double[]> m) {
        List<Amount> out = new ArrayList<>();
        for (Map.Entry<String, double[]> e : m.entrySet()) {
            out.add(new Amount(e.getKey(), Formats.round2(e.getValue()[0]), (int) e.getValue()[1]));
        }
        out.sort(Comparator.comparingDouble(Amount::value).reversed());
        return out;
    }

    public static List<ProductStat> topProducts(List<Sale> sales, int limit) {
        Map<Integer, double[]> m = new LinkedHashMap<>();
        Map<Integer, String[]> names = new LinkedHashMap<>();
        for (Sale s : valid(sales)) {
            for (SaleItem i : s.getItems()) {
                double[] v = m.computeIfAbsent(i.getProductId(), k -> new double[3]);
                v[0] += i.getQuantity();
                v[1] += i.getLineTotal();
                v[2] += i.getLineTotal() - i.getLineCost();
                names.put(i.getProductId(), new String[]{i.getProductName(), i.getCategory()});
            }
        }
        List<ProductStat> out = new ArrayList<>();
        for (Map.Entry<Integer, double[]> e : m.entrySet()) {
            String[] n = names.get(e.getKey());
            out.add(new ProductStat(n[0], n[1], (int) e.getValue()[0],
                    Formats.round2(e.getValue()[1]), Formats.round2(e.getValue()[2])));
        }
        out.sort(Comparator.comparingDouble(ProductStat::revenue).reversed());
        return out.subList(0, Math.min(limit, out.size()));
    }

    /** Flat CSV export of a report: summary, daily sales, then top products. */
    public static List<String[]> toCsv(List<Sale> sales, LocalDate from, LocalDate to) {
        List<String[]> rows = new ArrayList<>();
        Summary s = summarize(sales);
        rows.add(new String[]{"Sales report", Formats.date(from) + " to " + Formats.date(to)});
        rows.add(new String[]{});
        rows.add(new String[]{"Transactions", String.valueOf(s.transactions())});
        rows.add(new String[]{"Items sold", String.valueOf(s.items())});
        rows.add(new String[]{"Gross sales", String.valueOf(s.grossSales())});
        rows.add(new String[]{"Discounts", String.valueOf(s.discounts())});
        rows.add(new String[]{"Net sales (excl. tax)", String.valueOf(s.netSales())});
        rows.add(new String[]{"Tax collected", String.valueOf(s.tax())});
        rows.add(new String[]{"Gross profit", String.valueOf(s.profit())});
        rows.add(new String[]{"Average basket", String.valueOf(s.averageBasket())});
        rows.add(new String[]{});
        rows.add(new String[]{"Date", "Net sales"});
        for (Map.Entry<LocalDate, Double> e : netSalesByDay(sales, from, to).entrySet()) {
            rows.add(new String[]{Formats.date(e.getKey()), String.valueOf(Formats.round2(e.getValue()))});
        }
        rows.add(new String[]{});
        rows.add(new String[]{"Product", "Category", "Units sold", "Revenue", "Gross profit"});
        for (ProductStat p : topProducts(sales, Integer.MAX_VALUE)) {
            rows.add(new String[]{p.name(), p.category(), String.valueOf(p.quantity()),
                String.valueOf(p.revenue()), String.valueOf(p.profit())});
        }
        return rows;
    }

    /** Net sales per hour of the day (index 0-23), used for the "busiest hours" chart. */
    public static double[] netSalesByHour(List<Sale> sales) {
        double[] out = new double[24];
        for (Sale s : valid(sales)) out[s.getDateTime().getHour()] += s.getNetSales();
        for (int i = 0; i < 24; i++) out[i] = Formats.round2(out[i]);
        return out;
    }

    /** Net sales per weekday (index 0 = Monday). */
    public static double[] netSalesByWeekday(List<Sale> sales) {
        double[] out = new double[7];
        for (Sale s : valid(sales)) out[s.getDateTime().getDayOfWeek().getValue() - 1] += s.getNetSales();
        for (int i = 0; i < 7; i++) out[i] = Formats.round2(out[i]);
        return out;
    }

    /** Products that have stock on the shelf but have not sold at all since {@code since}. Biggest tied-up cash first. */
    public static List<Product> deadStock(Iterable<Product> products, List<Sale> sales, LocalDate since) {
        Set<Integer> sold = new HashSet<>();
        for (Sale s : valid(sales)) {
            if (s.getDateTime().toLocalDate().isBefore(since)) continue;
            for (SaleItem i : s.getItems()) sold.add(i.getProductId());
        }
        List<Product> out = new ArrayList<>();
        for (Product p : products) if (p.getQuantity() > 0 && !sold.contains(p.getProductId())) out.add(p);
        out.sort(Comparator.comparingDouble(Product::stockValueAtCost).reversed());
        return out;
    }

    /** Percentage change from {@code before} to {@code now}; null when there is nothing to compare with. */
    public static Double change(double now, double before) {
        if (before <= 0) return null;
        return (now - before) / before * 100.0;
    }

    /**
     * End-of-day cash-up as plain text: takings by payment method and by cashier, voids, and the cash
     * that should be in the drawer. Voided sales are listed but never counted.
     */
    public static String dayClose(List<Sale> daySales, LocalDate day, String storeName, String closedBy) {
        StringBuilder sb = new StringBuilder();
        String rule = "-".repeat(42);
        sb.append(storeName.toUpperCase()).append('\n');
        sb.append("END OF DAY  ").append(Formats.NICE_DATE.format(day)).append('\n');
        sb.append(rule).append('\n');
        Summary sum = summarize(daySales);
        sb.append(row("Transactions", String.valueOf(sum.transactions())));
        sb.append(row("Items sold", String.valueOf(sum.items())));
        sb.append(row("Gross sales", Formats.number(sum.grossSales())));
        sb.append(row("Discounts", "-" + Formats.number(sum.discounts())));
        sb.append(row("Net sales", Formats.number(sum.netSales())));
        sb.append(row("Tax collected", Formats.number(sum.tax())));
        sb.append(row("Total takings", Formats.number(Formats.round2(sum.netSales() + sum.tax()))));
        sb.append(row("Gross profit", Formats.number(sum.profit())));
        sb.append(rule).append('\n').append("TAKINGS BY PAYMENT\n");
        double cash = 0;
        for (Amount a : byPayment(daySales)) {
            sb.append(row(a.label() + " (" + a.count() + ")", Formats.number(a.value())));
            if (a.label().equals(PaymentMethod.CASH.getLabel())) cash = a.value();
        }
        sb.append(rule).append('\n').append("SALES BY CASHIER (net)\n");
        for (Amount a : byCashier(daySales)) sb.append(row(a.label() + " (" + a.count() + ")", Formats.number(a.value())));
        int voids = 0;
        double voidValue = 0;
        for (Sale s : daySales) {
            if (s.isVoided()) { voids++; voidValue += s.getTotal(); }
        }
        sb.append(rule).append('\n');
        sb.append(row("Voided sales", voids + " (" + Formats.number(Formats.round2(voidValue)) + ")"));
        sb.append(row("Cash expected in drawer", Formats.number(cash)));
        sb.append(rule).append('\n');
        sb.append("Closed by ").append(closedBy).append(" at ").append(Formats.DATE_TIME.format(LocalDateTime.now())).append('\n');
        return sb.toString();
    }

    private static String row(String left, String right) {
        int space = Math.max(1, 42 - left.length() - right.length());
        return left + " ".repeat(space) + right + "\n";
    }

    /** Plain-text purchase orders, one block per supplier ("No supplier" last). Lines with quantity 0 are skipped. */
    public static String purchaseOrders(List<Inventory.ReorderLine> lines, String storeName, LocalDate date) {
        int w = 64;
        java.util.Map<String, List<Inventory.ReorderLine>> bySupplier = new LinkedHashMap<>();
        for (Inventory.ReorderLine l : lines) {
            if (l.suggestedQty() <= 0) continue;
            String sup = l.product().getSupplier().isEmpty() ? "No supplier on file" : l.product().getSupplier();
            bySupplier.computeIfAbsent(sup, k -> new ArrayList<>()).add(l);
        }
        StringBuilder sb = new StringBuilder();
        if (bySupplier.isEmpty()) return "Nothing to order.\n";
        double grand = 0;
        int n = 1;
        for (java.util.Map.Entry<String, List<Inventory.ReorderLine>> e : bySupplier.entrySet()) {
            sb.append("PURCHASE ORDER ").append(String.format("PO-%s-%02d", date.toString().replace("-", ""), n++)).append('\n');
            sb.append(storeName).append("  ·  ").append(Formats.date(date)).append('\n');
            sb.append("Supplier: ").append(e.getKey()).append('\n');
            sb.append("=".repeat(w)).append('\n');
            sb.append(String.format("%-30s %6s %12s %12s%n", "Product", "Qty", "Unit cost", "Line total"));
            sb.append("-".repeat(w)).append('\n');
            double total = 0;
            for (Inventory.ReorderLine l : e.getValue()) {
                String name = l.product().getProductName();
                if (name.length() > 30) name = name.substring(0, 29) + "…";
                sb.append(String.format("%-30s %6d %12s %12s%n", name, l.suggestedQty(),
                        Formats.number(l.product().getCost()), Formats.number(l.estimatedCost())));
                total += l.estimatedCost();
            }
            sb.append("-".repeat(w)).append('\n');
            sb.append(String.format("%-30s %6s %12s %12s%n", "Estimated total", "", "", Formats.number(Formats.round2(total))));
            sb.append('\n').append('\n');
            grand += total;
        }
        if (bySupplier.size() > 1) sb.append("All suppliers: ").append(Formats.money(Formats.round2(grand))).append('\n');
        return sb.toString();
    }
}
