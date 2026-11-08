package com.greenbasket.service;

import com.greenbasket.domain.Product;
import com.greenbasket.domain.Sale;
import com.greenbasket.domain.SaleItem;
import com.greenbasket.repository.ProductRepository;
import com.greenbasket.repository.SaleRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReportService {

    public record Bucket(String label, BigDecimal revenue, BigDecimal profit, long units) { }

    public record Summary(LocalDate from, LocalDate to, long transactions, long voidedTransactions, long units,
                          BigDecimal grossSales, BigDecimal discounts, BigDecimal tax, BigDecimal netRevenue,
                          BigDecimal costOfGoods, BigDecimal profit, BigDecimal averageBasket,
                          List<Bucket> byPayment, List<Bucket> byCategory, List<Bucket> byCashier,
                          List<Bucket> topProducts, List<Bucket> byHour, List<Bucket> byWeekday, List<Bucket> byDay) { }

    public record Dashboard(Summary today, Summary yesterday, BigDecimal dailyTarget, BigDecimal targetProgressPercent,
                            long productCount, long lowStock, long outOfStock, long expiringSoon, long expired,
                            BigDecimal stockValueAtCost, BigDecimal stockValueAtRetail, List<Bucket> last7Days) { }

    public record ReorderSuggestion(Long productId, String name, String supplier, int quantity, int reorderLevel,
                                    int suggestedOrder, BigDecimal unitCost, BigDecimal estimatedCost) { }

    public record DeadStock(Long productId, String name, String category, int quantity, BigDecimal valueAtCost, LocalDate lastSold) { }

    private final SaleRepository sales;
    private final ProductRepository products;
    private final SettingsService settings;

    public ReportService(SaleRepository sales, ProductRepository products, SettingsService settings) {
        this.sales = sales;
        this.products = products;
        this.settings = settings;
    }

    public Summary summary(LocalDate from, LocalDate to) {
        LocalDate f = from == null ? LocalDate.now() : from;
        LocalDate t = to == null ? f : to;
        if (t.isBefore(f)) throw ApiException.badRequest("'to' cannot be before 'from'.");
        if (f.plusDays(731).isBefore(t)) throw ApiException.badRequest("Choose a range of at most two years.");
        List<Sale> all = sales.findAllWithItemsBetween(f.atStartOfDay(), t.plusDays(1).atStartOfDay());
        return build(f, t, all);
    }

    private Summary build(LocalDate f, LocalDate t, List<Sale> all) {
        List<Sale> valid = all.stream().filter(s -> !s.isVoided()).toList();
        long voided = all.size() - valid.size();
        BigDecimal gross = sum(valid, Sale::subtotal), disc = sum(valid, Sale::discountAmount), tax = sum(valid, Sale::taxAmount);
        BigDecimal total = sum(valid, Sale::total), cogs = sum(valid, Sale::costOfGoods), profit = sum(valid, Sale::profit);
        long units = valid.stream().mapToLong(Sale::unitCount).sum();
        BigDecimal avg = valid.isEmpty() ? BigDecimal.ZERO : total.divide(BigDecimal.valueOf(valid.size()), 2, RoundingMode.HALF_UP);

        Map<String, Acc> pay = new LinkedHashMap<>(), cat = new LinkedHashMap<>(), cash = new LinkedHashMap<>(),
                prod = new LinkedHashMap<>(), day = new LinkedHashMap<>();
        Acc[] hour = new Acc[24];
        Acc[] wk = new Acc[7];
        for (int i = 0; i < 24; i++) hour[i] = new Acc();
        for (int i = 0; i < 7; i++) wk[i] = new Acc();
        for (LocalDate d = f; !d.isAfter(t) && d.isBefore(f.plusDays(400)); d = d.plusDays(1)) day.put(d.toString(), new Acc());

        for (Sale s : valid) {
            BigDecimal net = s.total(), pr = s.profit();
            pay.computeIfAbsent(s.getPaymentMethod().name(), k -> new Acc()).add(net, pr, s.unitCount());
            cash.computeIfAbsent(s.getCashierName(), k -> new Acc()).add(net, pr, s.unitCount());
            hour[s.getCreatedAt().getHour()].add(net, pr, s.unitCount());
            wk[s.getCreatedAt().getDayOfWeek().getValue() - 1].add(net, pr, s.unitCount());
            Acc da = day.get(s.getCreatedAt().toLocalDate().toString());
            if (da != null) da.add(net, pr, s.unitCount());
            BigDecimal gs = s.subtotal();
            for (SaleItem i : s.getItems()) {
                // spread the sale-level discount across lines proportionally so category/product revenue adds up to net sales
                BigDecimal share = gs.signum() == 0 ? BigDecimal.ZERO : i.lineTotal().divide(gs, 8, RoundingMode.HALF_UP);
                BigDecimal lineNet = i.lineTotal().subtract(s.discountAmount().multiply(share));
                BigDecimal lineProfit = lineNet.subtract(i.lineCost());
                cat.computeIfAbsent(i.getCategoryName(), k -> new Acc()).add(lineNet, lineProfit, i.getQuantity());
                prod.computeIfAbsent(i.getProductName(), k -> new Acc()).add(lineNet, lineProfit, i.getQuantity());
            }
        }
        List<Bucket> hours = new ArrayList<>(), weekdays = new ArrayList<>();
        for (int i = 0; i < 24; i++) hours.add(hour[i].bucket(String.format("%02d:00", i)));
        for (DayOfWeek d : DayOfWeek.values()) {
            weekdays.add(wk[d.getValue() - 1].bucket(d.name().charAt(0) + d.name().substring(1, 3).toLowerCase()));
        }
        return new Summary(f, t, valid.size(), voided, units, gross, disc, tax, total, cogs, profit, avg,
                buckets(pay, false), buckets(cat, true), buckets(cash, true), top(prod, 10), hours, weekdays, buckets(day, false));
    }

    public Dashboard dashboard() {
        LocalDate today = LocalDate.now();
        Summary t = summary(today, today);
        Summary y = summary(today.minusDays(1), today.minusDays(1));
        List<Bucket> week = summary(today.minusDays(6), today).byDay();
        BigDecimal target = settings.dailyTarget();
        BigDecimal pct = target.signum() <= 0 ? BigDecimal.ZERO
                : t.netRevenue().multiply(BigDecimal.valueOf(100)).divide(target, 1, RoundingMode.HALF_UP);
        int warn = settings.expiryWarnDays();
        List<Product> active = products.findByActiveTrueOrderByNameKeyAsc();
        long low = 0, out = 0, expiring = 0, expired = 0;
        for (Product p : active) {
            switch (p.statusFor(warn)) {
                case EXPIRED -> expired++;
                case OUT -> out++;
                case LOW -> low++;
                case EXPIRING -> expiring++;
                default -> { }
            }
        }
        return new Dashboard(t, y, target, pct, active.size(), low, out, expiring, expired,
                orZero(products.stockValueAtCost()), orZero(products.stockValueAtRetail()), week);
    }

    public List<ReorderSuggestion> reorderSuggestions() {
        List<ReorderSuggestion> out = new ArrayList<>();
        for (Product p : products.findNeedingReorder()) {
            int target = Math.max(p.getReorderLevel() * 2, 1);
            int order = Math.max(target - p.getQuantity(), 1);
            out.add(new ReorderSuggestion(p.getId(), p.getName(), p.getSupplier() == null ? "" : p.getSupplier().getName(),
                    p.getQuantity(), p.getReorderLevel(), order, p.getCost(), p.getCost().multiply(BigDecimal.valueOf(order))));
        }
        return out;
    }

    /** Stocked products with no sale in the last {@code days} days. */
    public List<DeadStock> deadStock(int days) {
        int d = Math.min(Math.max(days, 1), 365);
        LocalDateTime since = LocalDate.now().minusDays(d).atStartOfDay();
        Map<Long, LocalDate> lastSold = new LinkedHashMap<>();
        Set<Long> recent = new HashSet<>();
        for (Sale s : sales.findAllWithItemsBetween(LocalDate.now().minusDays(365).atStartOfDay(), LocalDate.now().plusDays(1).atStartOfDay())) {
            if (s.isVoided()) continue;
            for (SaleItem i : s.getItems()) {
                Long pid = i.getProduct().getId();
                lastSold.merge(pid, s.getCreatedAt().toLocalDate(), (a, b) -> a.isAfter(b) ? a : b);
                if (!s.getCreatedAt().isBefore(since)) recent.add(pid);
            }
        }
        return products.findByActiveTrueOrderByNameKeyAsc().stream()
                .filter(p -> p.getQuantity() > 0 && !recent.contains(p.getId()))
                .map(p -> new DeadStock(p.getId(), p.getName(), p.getCategory().getName(), p.getQuantity(),
                        p.getCost().multiply(BigDecimal.valueOf(p.getQuantity())), lastSold.get(p.getId())))
                .sorted(Comparator.comparing(DeadStock::valueAtCost).reversed())
                .toList();
    }

    // ------------------------------------------------------------------ helpers

    private static BigDecimal orZero(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    private static BigDecimal sum(List<Sale> l, java.util.function.Function<Sale, BigDecimal> f) {
        return l.stream().map(f).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static List<Bucket> buckets(Map<String, Acc> m, boolean sortByRevenue) {
        List<Bucket> out = new ArrayList<>();
        m.forEach((k, v) -> out.add(v.bucket(k)));
        if (sortByRevenue) out.sort(Comparator.comparing(Bucket::revenue).reversed());
        return out;
    }

    private static List<Bucket> top(Map<String, Acc> m, int n) {
        return buckets(m, true).stream().limit(n).toList();
    }

    private static final class Acc {
        BigDecimal revenue = BigDecimal.ZERO, profit = BigDecimal.ZERO;
        long units;

        void add(BigDecimal r, BigDecimal p, long u) {
            revenue = revenue.add(r);
            profit = profit.add(p);
            units += u;
        }

        Bucket bucket(String label) {
            return new Bucket(label, revenue.setScale(2, RoundingMode.HALF_UP), profit.setScale(2, RoundingMode.HALF_UP), units);
        }
    }
}
