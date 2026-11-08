package service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import model.*;

/** Aggregations over sales and stock. Voided sales are always excluded. */
public class ReportService {

    public static class Line {
        public final String label;
        public final double value;
        public final double extra;
        public Line(String label, double value, double extra) {
            this.label = label;
            this.value = value;
            this.extra = extra;
        }
    }

    public static class Summary {
        public double revenue, profit, discounts, tax, avgOrder;
        public int orders, units;
    }

    private final SalesService sales;

    public ReportService(SalesService sales) {
        this.sales = sales;
    }

    private List<Sale> valid(LocalDate from, LocalDate to, String cashier) {
        return sales.between(from, to, cashier).stream().filter(s -> !s.isVoided()).collect(Collectors.toList());
    }

    public Summary summary(LocalDate from, LocalDate to) {
        Summary r = new Summary();
        for (Sale s : valid(from, to, null)) {
            r.revenue += s.getTotal();
            r.profit += s.profit();
            r.discounts += s.getDiscount();
            r.tax += s.getTax();
            r.units += s.itemCount();
            r.orders++;
        }
        r.avgOrder = r.orders == 0 ? 0 : r.revenue / r.orders;
        return r;
    }

    /** Revenue per day for the {@code days} days ending today (oldest first, zero-filled). */
    public List<Line> dailyRevenue(int days) {
        LocalDate today = LocalDate.now();
        LinkedHashMap<LocalDate, double[]> map = new LinkedHashMap<>();
        for (int i = days - 1; i >= 0; i--) map.put(today.minusDays(i), new double[]{0, 0});
        for (Sale s : valid(today.minusDays(days - 1), today, null)) {
            double[] v = map.get(s.getTime().toLocalDate());
            if (v != null) {
                v[0] += s.getTotal();
                v[1]++;
            }
        }
        List<Line> out = new ArrayList<>();
        for (Map.Entry<LocalDate, double[]> e : map.entrySet()) {
            out.add(new Line(e.getKey().format(util.Fmt.DAY_LABEL), e.getValue()[0], e.getValue()[1]));
        }
        return out;
    }

    /** Best sellers by revenue; extra = units sold. */
    public List<Line> topProducts(LocalDate from, LocalDate to, int limit) {
        Map<String, double[]> m = new HashMap<>();
        for (Sale s : valid(from, to, null)) {
            for (SaleItem i : s.getItems()) {
                double[] v = m.computeIfAbsent(i.getName(), k -> new double[2]);
                v[0] += i.lineTotal();
                v[1] += i.getQuantity();
            }
        }
        return m.entrySet().stream().map(e -> new Line(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .sorted((a, b) -> Double.compare(b.value, a.value)).limit(limit).collect(Collectors.toList());
    }

    /** Revenue by product category; extra = units. */
    public List<Line> revenueByCategory(LocalDate from, LocalDate to) {
        Map<String, double[]> m = new HashMap<>();
        for (Sale s : valid(from, to, null)) {
            for (SaleItem i : s.getItems()) {
                double[] v = m.computeIfAbsent(i.getCategory(), k -> new double[2]);
                v[0] += i.lineTotal();
                v[1] += i.getQuantity();
            }
        }
        return m.entrySet().stream().map(e -> new Line(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .sorted((a, b) -> Double.compare(b.value, a.value)).collect(Collectors.toList());
    }

    /** Revenue by cashier; extra = number of orders. */
    public List<Line> byCashier(LocalDate from, LocalDate to) {
        Map<String, double[]> m = new HashMap<>();
        for (Sale s : valid(from, to, null)) {
            double[] v = m.computeIfAbsent(s.getCashier(), k -> new double[2]);
            v[0] += s.getTotal();
            v[1]++;
        }
        return m.entrySet().stream().map(e -> new Line(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .sorted((a, b) -> Double.compare(b.value, a.value)).collect(Collectors.toList());
    }

    /** Products that have not sold at all in the period (and still have stock) - "dead stock". */
    public List<Product> slowMovers(Inventory inv, LocalDate from, LocalDate to) {
        Set<Integer> sold = new HashSet<>();
        for (Sale s : valid(from, to, null)) for (SaleItem i : s.getItems()) sold.add(i.getProductId());
        return inv.getAllProducts().stream().filter(p -> p.getQuantity() > 0 && !sold.contains(p.getProductId()))
                .sorted((a, b) -> Double.compare(b.costValue(), a.costValue())).collect(Collectors.toList());
    }

    /** Revenue per day between two dates (inclusive, zero-filled); extra = orders. */
    public List<Line> revenueByDay(LocalDate from, LocalDate to) {
        LinkedHashMap<LocalDate, double[]> map = new LinkedHashMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) map.put(d, new double[]{0, 0});
        for (Sale s : valid(from, to, null)) {
            double[] v = map.get(s.getTime().toLocalDate());
            if (v != null) {
                v[0] += s.getTotal();
                v[1]++;
            }
        }
        List<Line> out = new ArrayList<>();
        boolean longRange = map.size() > 31;
        for (Map.Entry<LocalDate, double[]> e : map.entrySet()) {
            out.add(new Line(e.getKey().format(longRange ? util.Fmt.DAY_MONTH : util.Fmt.DAY_LABEL), e.getValue()[0], e.getValue()[1]));
        }
        return out;
    }

    /** Revenue by payment method; extra = orders. */
    public List<Line> byPayment(LocalDate from, LocalDate to) {
        Map<String, double[]> m = new HashMap<>();
        for (Sale s : valid(from, to, null)) {
            double[] v = m.computeIfAbsent(s.getPayment(), k -> new double[2]);
            v[0] += s.getTotal();
            v[1]++;
        }
        return m.entrySet().stream().map(e -> new Line(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .sorted((a, b) -> Double.compare(b.value, a.value)).collect(Collectors.toList());
    }

    /** Orders by hour of day (0-23) - shows the rush hours. extra = orders. */
    public List<Line> byHour(LocalDate from, LocalDate to) {
        double[][] h = new double[24][2];
        for (Sale s : valid(from, to, null)) {
            h[s.getTime().getHour()][0] += s.getTotal();
            h[s.getTime().getHour()][1]++;
        }
        List<Line> out = new ArrayList<>();
        for (int i = 0; i < 24; i++) out.add(new Line(String.format("%02d", i), h[i][0], h[i][1]));
        return out;
    }

    public static class CustomerStat {
        public final String name;
        public int visits;
        public double spend;
        public java.time.LocalDateTime last;
        CustomerStat(String name) { this.name = name; }
        public double average() { return visits == 0 ? 0 : spend / visits; }
        public String tier() { return tierFor(spend); }
    }

    public static final double GOLD_FROM = 50_000;
    public static final double SILVER_FROM = 20_000;

    /** Loyalty tier by lifetime spend. */
    public static String tierFor(double spend) {
        return spend >= GOLD_FROM ? "GOLD" : spend >= SILVER_FROM ? "SILVER" : "REGULAR";
    }

    /** Percentage discount a customer of this tier is offered at the till. */
    public static double tierDiscount(String tier) {
        return "GOLD".equals(tier) ? 5 : "SILVER".equals(tier) ? 3 : 0;
    }

    /** Lifetime statistics for one customer name (case-insensitive), or null if they have never bought. */
    public CustomerStat customerStat(String name) {
        if (name == null || name.isBlank()) return null;
        for (CustomerStat c : customers(null, null)) if (c.name.equalsIgnoreCase(name.trim())) return c;
        return null;
    }

    /** Average units sold per day for each product over the last {@code days} days. */
    public Map<Integer, Double> velocity(int days) {
        Map<Integer, Double> m = new HashMap<>();
        LocalDate today = LocalDate.now();
        for (Sale s : valid(today.minusDays(days - 1), today, null)) {
            for (SaleItem i : s.getItems()) m.merge(i.getProductId(), (double) i.getQuantity() / days, Double::sum);
        }
        return m;
    }

    /** Stock value per category: value = at cost, extra = at retail. */
    public List<Line> valuationByCategory(Inventory inv) {
        Map<String, double[]> m = new HashMap<>();
        for (Product p : inv.getAllProducts()) {
            double[] v = m.computeIfAbsent(p.getCategory(), k -> new double[2]);
            v[0] += p.costValue();
            v[1] += p.retailValue();
        }
        return m.entrySet().stream().map(e -> new Line(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .sorted((a, b) -> Double.compare(b.value, a.value)).collect(Collectors.toList());
    }

    /** Revenue and profit per day (inclusive range): value = revenue, extra = profit. */
    public List<Line> profitByDay(LocalDate from, LocalDate to) {
        LinkedHashMap<LocalDate, double[]> map = new LinkedHashMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) map.put(d, new double[2]);
        for (Sale s : valid(from, to, null)) {
            double[] v = map.get(s.getTime().toLocalDate());
            if (v != null) {
                v[0] += s.getTotal();
                v[1] += s.profit();
            }
        }
        List<Line> out = new ArrayList<>();
        for (Map.Entry<LocalDate, double[]> e : map.entrySet()) out.add(new Line(util.Fmt.date(e.getKey()), e.getValue()[0], e.getValue()[1]));
        return out;
    }

    /** Named customers ranked by spend. Anonymous / walk-in sales are not counted. */
    public List<CustomerStat> customers(LocalDate from, LocalDate to) {
        Map<String, CustomerStat> m = new LinkedHashMap<>();
        for (Sale s : valid(from, to, null)) {
            String n = s.getCustomer().trim();
            if (n.isEmpty() || n.equalsIgnoreCase("walk-in")) continue;
            CustomerStat c = m.computeIfAbsent(n.toLowerCase(), k -> new CustomerStat(n));
            c.visits++;
            c.spend += s.getTotal();
            if (c.last == null || s.getTime().isAfter(c.last)) c.last = s.getTime();
        }
        return m.values().stream().sorted((a, b) -> Double.compare(b.spend, a.spend)).collect(Collectors.toList());
    }

    /** Every customer name used so far (for autocomplete at the till). */
    public List<String> customerNames() {
        return customers(null, null).stream().map(c -> c.name).collect(Collectors.toList());
    }

    // ------------------------------------------------ period comparison & breakdowns

    /** The period of identical length that ends the day before {@code from}. Returns {from, to}. */
    public static LocalDate[] previousRange(LocalDate from, LocalDate to) {
        long len = java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1;
        LocalDate prevTo = from.minusDays(1);
        return new LocalDate[]{prevTo.minusDays(len - 1), prevTo};
    }

    /** Summary of the equally long period immediately before {@code from..to}. */
    public Summary previousSummary(LocalDate from, LocalDate to) {
        LocalDate[] r = previousRange(from, to);
        return summary(r[0], r[1]);
    }

    /** Revenue by day of week (Mon..Sun, always 7 entries); extra = orders. */
    public List<Line> byWeekday(LocalDate from, LocalDate to) {
        double[][] d = new double[7][2];
        for (Sale s : valid(from, to, null)) {
            int i = s.getTime().getDayOfWeek().getValue() - 1;
            d[i][0] += s.getTotal();
            d[i][1]++;
        }
        String[] names = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        List<Line> out = new ArrayList<>();
        for (int i = 0; i < 7; i++) out.add(new Line(names[i], d[i][0], d[i][1]));
        return out;
    }

    public static class ProductStat {
        public final int productId;
        public final String name, category;
        public int units;
        public double revenue, profit;
        ProductStat(int id, String name, String category) { this.productId = id; this.name = name; this.category = category; }
        public double margin() { return revenue <= 0 ? 0 : profit / revenue * 100.0; }
    }

    /**
     * Per-product results for the period, best revenue first. Revenue is the line value before
     * discount and tax; profit is net of the sale's discount (shared across its lines by value),
     * so the profits add up to {@link Summary#profit}.
     */
    public List<ProductStat> productPerformance(LocalDate from, LocalDate to) {
        Map<Integer, ProductStat> m = new HashMap<>();
        for (Sale s : valid(from, to, null)) {
            for (SaleItem i : s.getItems()) {
                ProductStat p = m.computeIfAbsent(i.getProductId(), k -> new ProductStat(k, i.getName(), i.getCategory()));
                double share = s.getSubtotal() <= 0 ? 0 : i.lineTotal() / s.getSubtotal();
                p.units += i.getQuantity();
                p.revenue += i.lineTotal();
                p.profit += i.lineTotal() - i.getUnitCost() * i.getQuantity() - s.getDiscount() * share;
            }
        }
        return m.values().stream().sorted((a, b) -> Double.compare(b.revenue, a.revenue)).collect(Collectors.toList());
    }

    public static class CashierStat {
        public final String name;
        public int orders, voided, units;
        public double revenue, profit;
        CashierStat(String name) { this.name = name; }
        public double average() { return orders == 0 ? 0 : revenue / orders; }
    }

    /** Sales per cashier including how many of their sales were voided. Best revenue first. */
    public List<CashierStat> cashierPerformance(LocalDate from, LocalDate to) {
        Map<String, CashierStat> m = new LinkedHashMap<>();
        for (Sale s : sales.between(from, to, null)) {
            CashierStat c = m.computeIfAbsent(s.getCashier().toLowerCase(), k -> new CashierStat(s.getCashier()));
            if (s.isVoided()) {
                c.voided++;
            } else {
                c.orders++;
                c.revenue += s.getTotal();
                c.profit += s.profit();
                c.units += s.itemCount();
            }
        }
        return m.values().stream().sorted((a, b) -> Double.compare(b.revenue, a.revenue)).collect(Collectors.toList());
    }

    public static class Forecast {
        public final Product product;
        public final double perDay;
        public final double daysCover;
        Forecast(Product p, double perDay) {
            this.product = p;
            this.perDay = perDay;
            this.daysCover = p.getQuantity() <= 0 ? 0 : p.getQuantity() / perDay;
        }
        /** OUT, LOW (a week of cover or less) or OK. */
        public String status() { return product.isOutOfStock() ? "OUT" : daysCover <= 7 ? "LOW" : "OK"; }
    }

    /** Products that have been selling in the last {@code days} days, with how long the stock will last; soonest first. */
    public List<Forecast> reorderForecast(Inventory inv, int days) {
        Map<Integer, Double> v = velocity(days);
        List<Forecast> out = new ArrayList<>();
        for (Product p : inv.getAllProducts()) {
            Double perDay = v.get(p.getProductId());
            if (perDay != null && perDay > 0) out.add(new Forecast(p, perDay));
        }
        out.sort(Comparator.comparingDouble((Forecast f) -> f.daysCover).thenComparing(f -> f.product.getProductName()));
        return out;
    }
}
