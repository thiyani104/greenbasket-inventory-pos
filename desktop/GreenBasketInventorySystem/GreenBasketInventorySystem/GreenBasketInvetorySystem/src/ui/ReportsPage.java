package ui;

import java.awt.*;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import model.Product;
import model.Sale;
import service.ReportService;
import service.ReportService.CashierStat;
import service.ReportService.Forecast;
import service.ReportService.Line;
import service.ReportService.ProductStat;
import ui.kit.*;
import util.FileManager;
import util.Fmt;
import util.SystemContext;

/**
 * Manager analytics: pick a period, see headline KPIs against the previous period, then dig into
 * sales, products, team &amp; payments and stock. Everything on the page is computed once per refresh.
 */
public class ReportsPage extends Page {

    private static final String[] PERIODS = {"Today", "7 days", "30 days", "This month", "90 days", "Custom"};
    private static final String[] TABS = {"Sales", "Products", "Team & payments", "Stock"};
    private static final DateTimeFormatter LONG = DateTimeFormatter.ofPattern("d MMM yyyy");
    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("d MMM");

    // ---- controls
    private final Segmented period = new Segmented(true, PERIODS);
    private final DatePicker fromPicker = new DatePicker();
    private final DatePicker toPicker = new DatePicker();
    private final JPanel custom = Ui.transparent(new FlowLayout(FlowLayout.LEFT, 8, 0));
    private final JLabel rangeLabel = Ui.label(" ", Theme.bold(13), Theme.muted);
    private final Segmented tabs = new Segmented(TABS);
    private final JPanel cards = new JPanel(new CardLayout());

    private final Kpi k1 = new Kpi("Revenue", "receipt", Theme.chart[0]);
    private final Kpi k2 = new Kpi("Gross profit", "chart", Theme.chart[3]);
    private final Kpi k3 = new Kpi("Orders", "cart", Theme.chart[1]);
    private final Kpi k4 = new Kpi("Units sold", "box", Theme.chart[5]);
    private final Kpi k5 = new Kpi("Discounts given", "percent", Theme.chart[2]);

    // ---- sales tab
    private final Charts.Area revenueArea = new Charts.Area(Theme.chart[0], "orders");
    private final Charts.Area profitArea = new Charts.Area(Theme.chart[3], null);
    private final CardLayout areaCards = new CardLayout();
    private final JPanel areaStack = new JPanel(areaCards);
    private final Segmented trendMode = new Segmented(true, "Revenue", "Profit");
    private final Charts.Columns hours = new Charts.Columns(Theme.chart[2], "orders");
    private final Charts.Columns weekdays = new Charts.Columns(Theme.chart[1], "orders");
    private final JLabel hiBestDay = value(), hiBusiest = value(), hiAvgDay = value(), hiLargest = value(), hiVoided = value();

    // ---- products tab
    private final Charts.Ranks top = new Charts.Ranks("sold", true);
    private final Charts.Donut categoryMix = new Charts.Donut("revenue");
    private final ListTableModel<ProductStat> perfModel = new ListTableModel<ProductStat>()
            .title("Product", 190, p -> p.name)
            .muted("Category", 100, p -> p.category)
            .integer("Units", 60, p -> p.units)
            .money("Revenue", 110, p -> p.revenue)
            .money("Profit", 100, p -> p.profit)
            .percent("Margin", 76, p -> p.margin());
    private final ListTableModel<Product> slowModel = new ListTableModel<Product>()
            .title("Product", 160, Product::getProductName)
            .integer("Stock", 56, Product::getQuantity)
            .money("Tied up", 96, Product::costValue);
    private final Tables.DataTable perfTable = Tables.table(perfModel);
    private final Tables.DataTable slowTable = Tables.table(slowModel);

    // ---- team & payments tab
    private final ListTableModel<CashierStat> cashierModel = new ListTableModel<CashierStat>()
            .title("Cashier", 140, c -> c.name)
            .integer("Orders", 70, c -> c.orders)
            .money("Revenue", 110, c -> c.revenue)
            .money("Avg order", 100, CashierStat::average)
            .integer("Units", 64, c -> c.units)
            .integer("Voided", 70, c -> c.voided);
    private final Tables.DataTable cashierTable = Tables.table(cashierModel);
    private final Charts.Donut payMix = new Charts.Donut("revenue");
    private final Charts.Ranks cashierRanks = new Charts.Ranks("orders", true);
    private double payTotal;
    private final ListTableModel<Line> payModel = new ListTableModel<Line>()
            .title("Method", 120, l -> l.label)
            .integer("Orders", 70, l -> (int) l.extra)
            .money("Revenue", 120, l -> l.value)
            .percent("Share", 80, l -> payTotal <= 0 ? 0.0 : l.value / payTotal * 100);
    private final Tables.DataTable payTable = Tables.table(payModel);

    // ---- stock tab
    private final Kpi s1 = new Kpi("Units in stock", "box", Theme.chart[0]);
    private final Kpi s2 = new Kpi("Stock value (cost)", "tag", Theme.chart[1]);
    private final Kpi s3 = new Kpi("Stock value (retail)", "receipt", Theme.chart[3]);
    private final Kpi s4 = new Kpi("Needs restock", "alert", Theme.chart[2]);
    private final Kpi s5 = new Kpi("Value at risk", "clock", Theme.chart[4]);
    private final ListTableModel<Line> valuationModel = new ListTableModel<Line>()
            .title("Category", 120, l -> l.label)
            .money("Cost value", 104, l -> l.value)
            .money("Retail value", 104, l -> l.extra)
            .money("Potential profit", 120, l -> l.extra - l.value);
    private final ListTableModel<Forecast> forecastModel = new ListTableModel<Forecast>()
            .title("Product", 170, f -> f.product.getProductName())
            .integer("Stock", 56, f -> f.product.getQuantity())
            .text("Sold / day", 80, f -> String.format("%.1f", f.perDay))
            .integer("Days left", 76, f -> (int) Math.min(9999, Math.floor(f.daysCover)))
            .badge("Status", 90, Forecast::status);
    private final Tables.DataTable valuationTable = Tables.table(valuationModel);
    private final Tables.DataTable forecastTable = Tables.table(forecastModel);

    // ---- cached results of the last refresh
    private LocalDate from = LocalDate.now().minusDays(29), to = LocalDate.now();
    private ReportService.Summary cur = new ReportService.Summary(), prev = new ReportService.Summary();
    private List<Line> daily = new ArrayList<>(), dailyProfit = new ArrayList<>(), hourly = new ArrayList<>();
    private List<ProductStat> perf = new ArrayList<>();
    private List<CashierStat> cashiers = new ArrayList<>();
    private List<Line> payments = new ArrayList<>(), categories = new ArrayList<>(), valuation = new ArrayList<>();
    private List<Forecast> forecast = new ArrayList<>();
    private List<Product> slow = new ArrayList<>();
    private Map<Integer, Double> velocity = new java.util.HashMap<>();
    private boolean updating;

    public ReportsPage(MainFrame host) {
        super(host);

        Btn export = new Btn("Export CSV", "download", Btn.Kind.SECONDARY);
        export.addActionListener(e -> exportCsv());
        Btn print = new Btn("Print report", "print", Btn.Kind.SECONDARY);
        print.addActionListener(e -> ReceiptDialog.showText(host, "Sales report",
                textReport(), "report-" + Fmt.date(LocalDate.now()) + ".txt"));
        JComponent head = Ui.header("Reports", "Sales, product, team and stock performance", actions(export, print));

        // period row
        custom.add(Ui.label("From", Theme.font(12.5f), Theme.muted));
        custom.add(sized(fromPicker, 180));
        custom.add(Ui.label("to", Theme.font(12.5f), Theme.muted));
        custom.add(sized(toPicker, 180));
        custom.setVisible(false);
        JPanel pw = Ui.transparent(new GridBagLayout());
        pw.add(period);
        JPanel periodRow = Ui.transparent(new BorderLayout(14, 0));
        periodRow.add(pw, BorderLayout.WEST);
        periodRow.add(custom, BorderLayout.CENTER);
        periodRow.add(rangeLabel, BorderLayout.EAST);
        periodRow.setBorder(new EmptyBorder(0, 0, 14, 0));

        JPanel kpis = new JPanel(new GridLayout(1, 5, 16, 0));
        kpis.setOpaque(false);
        kpis.add(k1); kpis.add(k2); kpis.add(k3); kpis.add(k4); kpis.add(k5);
        kpis.setBorder(new EmptyBorder(0, 0, 16, 0));

        JPanel tabRow = Ui.transparent(new BorderLayout());
        tabRow.add(tabs, BorderLayout.WEST);
        tabRow.setBorder(new EmptyBorder(0, 0, 14, 0));

        JPanel mid = Ui.transparent(new BorderLayout());
        mid.add(kpis, BorderLayout.NORTH);
        mid.add(tabRow, BorderLayout.SOUTH);
        JPanel top = Ui.transparent(new BorderLayout());
        top.add(periodRow, BorderLayout.NORTH);
        top.add(mid, BorderLayout.CENTER);

        cards.setOpaque(false);
        cards.add(buildSales(), "0");
        cards.add(buildProducts(), "1");
        cards.add(buildTeam(), "2");
        cards.add(buildStock(), "3");

        JPanel body = Ui.transparent(new BorderLayout());
        body.add(top, BorderLayout.NORTH);
        body.add(cards, BorderLayout.CENTER);
        shell(head, body);

        period.setSelected(2);
        period.onChange(i -> {
            custom.setVisible(i == 5);
            if (i == 5 && fromPicker.getDateOrNull() == null) {
                fromPicker.setDate(LocalDate.now().minusDays(29));
                toPicker.setDate(LocalDate.now());
            }
            refresh();
        });
        fromPicker.onChange(this::refresh);
        toPicker.onChange(this::refresh);
        tabs.onChange(i -> ((CardLayout) cards.getLayout()).show(cards, String.valueOf(i)));
        trendMode.onChange(i -> areaCards.show(areaStack, i == 0 ? "rev" : "profit"));
        refresh();
    }

    // ------------------------------------------------------------ test hooks

    /** Selects a tab (0 Sales, 1 Products, 2 Team &amp; payments, 3 Stock). */
    public void showTab(int i) {
        tabs.setSelected(i);
        ((CardLayout) cards.getLayout()).show(cards, String.valueOf(i));
    }

    /** Selects a period preset (0 Today ... 5 Custom) and refreshes. */
    public void setPeriod(int i) {
        period.setSelected(i);
        custom.setVisible(i == 5);
        refresh();
    }

    // ------------------------------------------------------------------ build

    private static JLabel value() { return Ui.label("-", Theme.bold(13.5f), Theme.text); }

    private static JComponent sized(JComponent c, int w) {
        c.setPreferredSize(new Dimension(w, 40));
        return c;
    }

    private static JPanel stack() {
        JPanel p = Ui.transparent(null);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        return p;
    }

    private static JComponent scrolled(JPanel stack) {
        stack.add(Box.createVerticalStrut(4));
        return Ui.stackScroll(stack);
    }

    private static Card tableCard(String title, String subtitle, Tables.DataTable t, String empty) {
        t.setRowHeight(40);
        t.setEmptyText(empty);
        return Ui.section(title, subtitle, Tables.scroll(t), null);
    }

    private JComponent buildSales() {
        areaStack.setOpaque(false);
        areaStack.add(revenueArea, "rev");
        areaStack.add(profitArea, "profit");
        Card trend = Ui.section("Revenue trend", "Hover a point for the day's figures", areaStack, trendMode);
        Card byHour = Ui.section("Sales by hour", "When customers shop", hours, null);

        JPanel hi = stack();
        hi.add(statRow("Best day", hiBestDay));
        hi.add(statRow("Busiest hour", hiBusiest));
        hi.add(statRow("Average per day", hiAvgDay));
        hi.add(statRow("Largest sale", hiLargest));
        hi.add(statRow("Voided sales", hiVoided));
        Card highlights = Ui.section("Highlights", "Standouts in this period", hi, null);
        Card dow = Ui.section("Day of week", "Revenue by weekday", weekdays, null);

        JPanel s = stack();
        s.add(Ui.rowOf(Ui.split(18, new double[]{0.62, 0.38}, trend, byHour), 340, 18));
        s.add(Ui.rowOf(Ui.split(18, new double[]{0.5, 0.5}, dow, highlights), 320, 18));
        return scrolled(s);
    }

    private static JComponent statRow(String label, JLabel val) {
        JPanel r = Ui.transparent(new BorderLayout(10, 0));
        r.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.border), new EmptyBorder(11, 0, 11, 0)));
        r.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        r.setAlignmentX(0);
        r.add(Ui.label(label, Theme.font(13), Theme.muted), BorderLayout.WEST);
        val.setHorizontalAlignment(SwingConstants.RIGHT);
        r.add(val, BorderLayout.CENTER);
        return r;
    }

    private JComponent buildProducts() {
        Card ranks = Ui.section("Best sellers", "Top 10 by revenue", top, null);
        Card mix = Ui.section("Revenue by category", null, categoryMix, null);
        Card slowCard = tableCard("Slow movers", "In stock, but nothing sold in this period", slowTable, "Everything sold - nice");
        JPanel right = Ui.transparent(new GridLayout(2, 1, 0, 16));
        right.add(mix);
        right.add(slowCard);
        Card perfCard = tableCard("Product performance", "Click a column heading to sort", perfTable, "No sales in this period");

        JPanel s = stack();
        s.add(Ui.rowOf(Ui.split(18, new double[]{0.46, 0.54}, ranks, right), 520, 18));
        s.add(Ui.rowOf(perfCard, 440, 18));
        return scrolled(s);
    }

    private JComponent buildTeam() {
        Card cashierCard = tableCard("Cashier performance", "Completed sales per team member", cashierTable, "No sales in this period");
        Card pay = Ui.section("Payment methods", "Share of revenue", payMix, null);
        Card ranks = Ui.section("Revenue by cashier", null, cashierRanks, null);
        Card payCard = tableCard("Payment breakdown", null, payTable, "No sales in this period");

        JPanel s = stack();
        s.add(Ui.rowOf(Ui.split(18, new double[]{0.62, 0.38}, cashierCard, pay), 330, 18));
        s.add(Ui.rowOf(Ui.split(18, new double[]{0.5, 0.5}, ranks, payCard), 330, 18));
        return scrolled(s);
    }

    private JComponent buildStock() {
        JPanel kp = new JPanel(new GridLayout(1, 5, 16, 0));
        kp.setOpaque(false);
        kp.add(s1); kp.add(s2); kp.add(s3); kp.add(s4); kp.add(s5);
        Card val = tableCard("Stock valuation", "What is on the shelves, by category", valuationTable, "The catalogue is empty");
        Card fc = tableCard("Reorder forecast", "How long stock lasts at the last 30 days' pace", forecastTable, "Nothing has sold recently");

        JPanel s = stack();
        s.add(Ui.rowOf(kp, 112, 18));
        s.add(Ui.rowOf(Ui.split(18, new double[]{0.46, 0.54}, val, fc), 460, 18));
        return scrolled(s);
    }

    // ---------------------------------------------------------------- refresh

    /** Resolves the chosen period; null when a custom range is incomplete or reversed. */
    private LocalDate[] range() {
        LocalDate today = LocalDate.now();
        switch (period.getSelected()) {
            case 0: return new LocalDate[]{today, today};
            case 1: return new LocalDate[]{today.minusDays(6), today};
            case 2: return new LocalDate[]{today.minusDays(29), today};
            case 3: return new LocalDate[]{today.withDayOfMonth(1), today};
            case 4: return new LocalDate[]{today.minusDays(89), today};
            default:
                try {
                    LocalDate f = fromPicker.getDate(), t = toPicker.getDate();
                    if (f == null || t == null) return null;
                    if (f.isAfter(t)) {
                        host.toastError("The start date must not be after the end date.");
                        return null;
                    }
                    return new LocalDate[]{f, t};
                } catch (IllegalArgumentException ex) {
                    return null;
                }
        }
    }

    @Override public void refresh() {
        if (updating) return;
        LocalDate[] r = range();
        if (r == null) return;
        updating = true;
        try {
            from = r[0];
            to = r[1];
            ReportService rs = SystemContext.reports;
            cur = rs.summary(from, to);
            prev = rs.previousSummary(from, to);
            daily = rs.revenueByDay(from, to);
            dailyProfit = rs.profitByDay(from, to);
            hourly = rs.byHour(from, to);
            perf = rs.productPerformance(from, to);
            cashiers = rs.cashierPerformance(from, to);
            payments = rs.byPayment(from, to);
            categories = rs.revenueByCategory(from, to);
            slow = rs.slowMovers(SystemContext.inventory, from, to);
            valuation = rs.valuationByCategory(SystemContext.inventory);
            velocity = rs.velocity(30);
            forecast = rs.reorderForecast(SystemContext.inventory, 30);

            long days = ChronoUnit.DAYS.between(from, to) + 1;
            rangeLabel.setText(days == 1 ? from.format(LONG) : from.format(LONG) + "  -  " + to.format(LONG) + "  (" + days + " days)");
            updateKpis(days);
            updateSales(days);
            updateProducts();
            updateTeam();
            updateStock();
        } finally {
            updating = false;
        }
    }

    private static String money(double v) {
        return Math.abs(v) >= 100_000 ? Fmt.currency() + " " + Fmt.compact(v) : Fmt.money(v);
    }

    private void updateKpis(long days) {
        String vs = days == 1 ? "yesterday" : "previous " + days + " days";
        String delta;
        Kpi.Tone tone = Kpi.Tone.NEUTRAL;
        if (cur.revenue <= 0 && prev.revenue <= 0) delta = "No sales in either period";
        else if (prev.revenue <= 0) delta = "No sales in the " + vs;
        else {
            double pct = (cur.revenue - prev.revenue) / prev.revenue * 100;
            delta = String.format("%+.1f%% vs %s", pct, vs);
            tone = pct >= 0 ? Kpi.Tone.GOOD : Kpi.Tone.BAD;
        }
        k1.set(money(cur.revenue), delta, tone);
        double net = cur.revenue - cur.tax;
        k2.set(money(cur.profit), net <= 0 ? "-" : String.format("%.1f%% margin", cur.profit / net * 100),
                cur.profit >= 0 ? Kpi.Tone.GOOD : Kpi.Tone.BAD);
        k3.set(String.valueOf(cur.orders), cur.orders == 0 ? "No orders" : Fmt.money(cur.avgOrder) + " average", Kpi.Tone.NEUTRAL);
        k4.set(String.valueOf(cur.units), cur.orders == 0 ? "-" : String.format("%.1f per order", (double) cur.units / cur.orders), Kpi.Tone.NEUTRAL);
        k5.set(money(cur.discounts), "Tax collected " + money(cur.tax), Kpi.Tone.NEUTRAL);
    }

    private void updateSales(long days) {
        revenueArea.setData(daily);
        List<Line> pl = new ArrayList<>();
        for (int i = 0; i < dailyProfit.size() && i < daily.size(); i++)
            pl.add(new Line(daily.get(i).label, dailyProfit.get(i).extra, 0));
        profitArea.setData(pl);
        hours.setData(hourly);
        weekdays.setData(SystemContext.reports.byWeekday(from, to));

        Line best = null;
        for (int i = 0; i < dailyProfit.size(); i++) {
            Line l = dailyProfit.get(i);
            if (l.value > 0 && (best == null || l.value > best.value)) best = l;
        }
        hiBestDay.setText(best == null ? "-" : LocalDate.parse(best.label).format(LONG) + "  -  " + Fmt.money(best.value));
        Line busy = null;
        for (Line l : hourly) if (l.extra > 0 && (busy == null || l.extra > busy.extra)) busy = l;
        hiBusiest.setText(busy == null ? "-" : busy.label + ":00  -  " + (int) busy.extra + " orders");
        hiAvgDay.setText(cur.orders == 0 ? "-" : Fmt.money(cur.revenue / days));
        double largest = 0;
        int voided = 0;
        double voidedValue = 0;
        for (Sale s : SystemContext.sales.between(from, to, null)) {
            if (s.isVoided()) {
                voided++;
                voidedValue += s.getTotal();
            } else largest = Math.max(largest, s.getTotal());
        }
        hiLargest.setText(largest <= 0 ? "-" : Fmt.money(largest));
        hiVoided.setText(voided == 0 ? "None" : voided + "  -  " + Fmt.money(voidedValue));
    }

    private void updateProducts() {
        List<Line> t = new ArrayList<>();
        for (int i = 0; i < Math.min(10, perf.size()); i++) {
            ProductStat p = perf.get(i);
            t.add(new Line(p.name, p.revenue, p.units));
        }
        top.setData(t);
        categoryMix.setData(categories);
        perfModel.setRows(perf);
        slowModel.setRows(slow);
    }

    private void updateTeam() {
        cashierModel.setRows(cashiers);
        payTotal = 0;
        for (Line l : payments) payTotal += l.value;
        payMix.setData(payments);
        payModel.setRows(payments);
        List<Line> cr = new ArrayList<>();
        for (CashierStat c : cashiers) if (c.orders > 0) cr.add(new Line(c.name, c.revenue, c.orders));
        cashierRanks.setData(cr);
    }

    private void updateStock() {
        int warn = SystemContext.settings.getExpiryWarnDays();
        double risk = 0;
        for (Product p : SystemContext.inventory.expired()) risk += p.costValue();
        for (Product p : SystemContext.inventory.expiringSoon(warn)) risk += p.costValue();
        int restock = SystemContext.inventory.needsRestock().size();
        s1.set(String.valueOf(SystemContext.inventory.totalUnits()), SystemContext.inventory.size() + " products", Kpi.Tone.NEUTRAL);
        s2.set(money(SystemContext.inventory.totalCostValue()), "What you paid", Kpi.Tone.NEUTRAL);
        double pot = SystemContext.inventory.totalRetailValue() - SystemContext.inventory.totalCostValue();
        s3.set(money(SystemContext.inventory.totalRetailValue()), "Potential profit " + money(pot), Kpi.Tone.NEUTRAL);
        s4.set(String.valueOf(restock), restock == 0 ? "Everything is stocked" : "Low or sold out", restock == 0 ? Kpi.Tone.GOOD : Kpi.Tone.WARN);
        s5.set(money(risk), risk <= 0 ? "Nothing expiring" : "Expired or expiring in " + warn + " days", risk <= 0 ? Kpi.Tone.GOOD : Kpi.Tone.WARN);
        valuationModel.setRows(valuation);
        forecastModel.setRows(forecast);
    }

    // ----------------------------------------------------------------- export

    private void exportCsv() {
        int tab = tabs.getSelected();
        String[] names = {"sales", "products", "team", "stock"};
        Path p = Pick.save(this, "report-" + names[tab] + "-" + Fmt.date(LocalDate.now()).replace("-", "") + ".csv", "CSV file", "csv");
        if (p == null) return;
        String[] header;
        List<String[]> rows = new ArrayList<>();
        switch (tab) {
            case 0:
                header = new String[]{"Date", "Orders", "Revenue", "Profit"};
                for (int i = 0; i < dailyProfit.size(); i++) {
                    rows.add(new String[]{dailyProfit.get(i).label, String.valueOf((int) daily.get(i).extra),
                            two(dailyProfit.get(i).value), two(dailyProfit.get(i).extra)});
                }
                break;
            case 1:
                header = new String[]{"Product", "Category", "Units", "Revenue", "Profit", "Margin %"};
                for (ProductStat s : perf)
                    rows.add(new String[]{s.name, s.category, String.valueOf(s.units), two(s.revenue), two(s.profit), two(s.margin())});
                break;
            case 2:
                header = new String[]{"Cashier", "Orders", "Revenue", "Average order", "Units", "Voided sales", "Profit"};
                for (CashierStat c : cashiers)
                    rows.add(new String[]{c.name, String.valueOf(c.orders), two(c.revenue), two(c.average()),
                            String.valueOf(c.units), String.valueOf(c.voided), two(c.profit)});
                break;
            default:
                header = new String[]{"ID", "Product", "Category", "Supplier", "Stock", "Cost", "Price", "Cost value",
                        "Retail value", "Sold per day (30d)", "Days of cover"};
                for (Product pr : SystemContext.inventory.getAllProducts()) {
                    double v = velocity.getOrDefault(pr.getProductId(), 0.0);
                    rows.add(new String[]{String.valueOf(pr.getProductId()), pr.getProductName(), pr.getCategory(), pr.getSupplier(),
                            String.valueOf(pr.getQuantity()), two(pr.getCostPrice()), two(pr.getPrice()), two(pr.costValue()),
                            two(pr.retailValue()), two(v), v <= 0 ? "" : String.valueOf((int) Math.floor(pr.getQuantity() / v))});
                }
        }
        try {
            FileManager.writeCsvFile(p, header, rows);
            host.toast("Exported " + rows.size() + " rows");
        } catch (RuntimeException ex) {
            Dialogs.error(host, ex.getMessage());
        }
    }

    private static String two(double v) { return String.format(java.util.Locale.ROOT, "%.2f", v); }

    // ------------------------------------------------------------ text report

    private static final int W = 60;

    private static String line(String label, String val) {
        return label + " ".repeat(Math.max(1, W - label.length() - val.length())) + val + "\n";
    }

    private static String clip(String s, int n) { return s.length() <= n ? s : s.substring(0, n - 1) + "~"; }

    private static String rule(char c) { return String.valueOf(c).repeat(W) + "\n"; }

    private String textReport() {
        StringBuilder sb = new StringBuilder();
        String store = SystemContext.settings.getStoreName().toUpperCase();
        sb.append(" ".repeat(Math.max(0, (W - store.length()) / 2))).append(store).append('\n');
        String title = "SALES REPORT";
        sb.append(" ".repeat((W - title.length()) / 2)).append(title).append('\n');
        String per = from.equals(to) ? from.format(LONG) : from.format(LONG) + " - " + to.format(LONG);
        sb.append(" ".repeat(Math.max(0, (W - per.length()) / 2))).append(per).append('\n');
        sb.append(rule('=')).append("SUMMARY\n").append(rule('-'));
        sb.append(line("Revenue", Fmt.money(cur.revenue)));
        sb.append(line("Gross profit", Fmt.money(cur.profit)));
        sb.append(line("Orders", String.valueOf(cur.orders)));
        sb.append(line("Average order", Fmt.money(cur.avgOrder)));
        sb.append(line("Units sold", String.valueOf(cur.units)));
        sb.append(line("Discounts given", Fmt.money(cur.discounts)));
        sb.append(line("Tax collected", Fmt.money(cur.tax)));
        if (prev.revenue > 0)
            sb.append(line("Revenue vs previous period", String.format("%+.1f%%", (cur.revenue - prev.revenue) / prev.revenue * 100)));

        sb.append('\n').append("TOP 10 PRODUCTS\n").append(rule('-'));
        if (perf.isEmpty()) sb.append("No sales in this period.\n");
        for (int i = 0; i < Math.min(10, perf.size()); i++) {
            ProductStat p = perf.get(i);
            sb.append(String.format("%-30s %5d %,20.2f%n", clip(p.name, 30), p.units, p.revenue));
        }
        sb.append('\n').append("REVENUE BY CATEGORY\n").append(rule('-'));
        for (Line l : categories) sb.append(line(clip(l.label, 36), Fmt.money(l.value)));
        if (categories.isEmpty()) sb.append("No sales in this period.\n");
        sb.append('\n').append("PAYMENT METHODS\n").append(rule('-'));
        for (Line l : payments) sb.append(line(l.label + " (" + (int) l.extra + " orders)", Fmt.money(l.value)));
        if (payments.isEmpty()) sb.append("No sales in this period.\n");
        sb.append('\n').append("CASHIERS\n").append(rule('-'));
        for (CashierStat c : cashiers)
            sb.append(line(clip(c.name, 22) + " (" + c.orders + " orders" + (c.voided > 0 ? ", " + c.voided + " voided" : "") + ")", Fmt.money(c.revenue)));
        if (cashiers.isEmpty()) sb.append("No sales in this period.\n");
        sb.append(rule('=')).append("Printed ").append(LocalDate.now().format(LONG)).append('\n');
        return sb.toString();
    }
}
