package ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import model.PaymentMethod;
import model.Product;
import model.Sale;
import model.StockStatus;
import model.User;
import service.ReportService;
import util.Formats;
import util.SystemContext;

/** The home screen: today at a glance, a sales trend, stock alerts and recent activity. */
public class DashboardPage extends Page {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

    private final User user = SystemContext.currentUser;
    private final boolean manager = user.canViewReports();

    private final JPanel body = new ScrollColumn(new GridBagLayout());
    private final JScrollPane scroll = UI.scroll(body);
    private final Segmented range = new Segmented("7 days", "14 days", "30 days").onChange(this::refresh);
    private final BarChart trend = new BarChart();
    private final DonutChart mix = new DonutChart();
    private final KpiCard kpiSales = new KpiCard(Icons.Kind.CART, manager ? "Sales today" : "My sales today", Theme.PRIMARY);
    private final KpiCard kpiTx = new KpiCard(Icons.Kind.RECEIPT, manager ? "Transactions today" : "My transactions", Theme.INFO);
    private final KpiCard kpiProfit = new KpiCard(manager ? Icons.Kind.REPORT : Icons.Kind.PRODUCTS,
            user.canViewCosts() ? "Gross profit today" : "Items sold today", new Color(0x8E5BD0));
    private final KpiCard kpiAlerts = new KpiCard(Icons.Kind.BOX_ALERT, "Stock alerts", Theme.ORANGE);
    private JPanel attention = UI.transparent(new BorderLayout());
    private JPanel top = UI.transparent(new BorderLayout());
    private JPanel recent = UI.transparent(new BorderLayout());
    private final JPanel emptyHolder = UI.transparent(new BorderLayout());

    public DashboardPage() {
        range.setSelectedIndex(0);
        trend.setColor(Theme.PRIMARY);
        mix.setFormat(v -> compactMoney(v));
        mix.setCenterCaption("30 days");

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTHWEST;

        c.gridy = 0;
        c.insets = new Insets(0, 0, 0, 0);
        body.add(emptyHolder, c);

        JPanel kpis = UI.transparent(new GridLayout(1, 4, 16, 0));
        kpis.add(kpiSales);
        kpis.add(kpiTx);
        kpis.add(kpiProfit);
        kpis.add(kpiAlerts);
        c.gridy = 1;
        c.insets = new Insets(0, 0, 16, 0);
        body.add(kpis, c);

        JPanel charts = UI.transparent(new GridBagLayout());
        GridBagConstraints cc = new GridBagConstraints();
        cc.gridy = 0;
        cc.fill = GridBagConstraints.BOTH;
        cc.weighty = 1;
        cc.gridx = 0;
        cc.weightx = 2;
        cc.insets = new Insets(0, 0, 0, 16);
        Card salesCard = UI.section(manager ? "Sales trend" : "My sales", range, trend);
        salesCard.setPreferredSize(new Dimension(500, 320)); salesCard.setMinimumSize(new Dimension(0, 320));
        charts.add(salesCard, cc);
        cc.gridx = 1;
        cc.weightx = 1;
        cc.insets = new Insets(0, 0, 0, 0);
        Card mixCard = UI.section("Sales by category", UI.muted("last 30 days"), mix);
        mixCard.setPreferredSize(new Dimension(300, 320)); mixCard.setMinimumSize(new Dimension(0, 320));
        charts.add(mixCard, cc);
        c.gridy = 2;
        c.insets = new Insets(0, 0, 16, 0);
        body.add(charts, c);

        JPanel lists = UI.transparent(new GridLayout(1, 3, 16, 0));
        attention = UI.transparent(new BorderLayout());
        top = UI.transparent(new BorderLayout());
        recent = UI.transparent(new BorderLayout());
        FlatButton viewAll = FlatButton.ghost("View all").compact();
        viewAll.addActionListener(e -> go(user.canAdjustStock() ? "stock" : "products"));
        FlatButton allSales = FlatButton.ghost("All sales").compact();
        allSales.addActionListener(e -> go("sales"));
        Card a = UI.section("Needs attention", viewAll, attention);
        Card t = UI.section(manager ? "Top sellers · 7 days" : "Today's best sellers", null, top);
        Card r = UI.section("Recent sales", allSales, recent);
        for (Card k : new Card[]{a, t, r}) { k.setPreferredSize(new Dimension(300, 380)); k.setMinimumSize(new Dimension(0, 380)); }
        lists.add(a);
        lists.add(t);
        lists.add(r);
        c.gridy = 3;
        c.insets = new Insets(0, 0, 0, 0);
        body.add(lists, c);

        // pin content to the top and let it stretch horizontally
        GridBagConstraints fill = new GridBagConstraints();
        fill.gridx = 0;
        fill.gridy = 4;
        fill.weighty = 1;
        body.add(UI.transparent(null), fill);

        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        add(scroll, BorderLayout.CENTER);
    }

    @Override public String getTitle() { return "Dashboard"; }

    @Override public String getSubtitle() {
        int h = LocalDateTime.now().getHour();
        String greet = h < 12 ? "Good morning" : h < 17 ? "Good afternoon" : "Good evening";
        String first = user.getFullName().trim().split("\\s+")[0];
        return greet + ", " + first + " · here is how " + SystemContext.settings.getStoreName() + " is doing";
    }

    private void go(String key) {
        if (MainFrame.get() != null) MainFrame.get().showPage(key);
    }

    static String compactMoney(double v) {
        String cur = Formats.getCurrency();
        String n = BarChart.compact(v);
        return cur.isEmpty() ? n : cur + " " + n;
    }

    private List<Sale> scope(List<Sale> sales) {
        if (manager) return sales;
        List<Sale> out = new ArrayList<>();
        for (Sale s : sales) if (s.getCashier().equalsIgnoreCase(user.getUsername())) out.add(s);
        return out;
    }

    @Override
    public void refresh() {
        LocalDate today = LocalDate.now();
        List<Sale> all = scope(SystemContext.sales.getAll());
        List<Sale> todays = scope(SystemContext.sales.between(today, today));
        List<Sale> yesterdays = scope(SystemContext.sales.between(today.minusDays(1), today.minusDays(1)));
        ReportService.Summary now = ReportService.summarize(todays);
        ReportService.Summary before = ReportService.summarize(yesterdays);

        // ---- empty catalogue call to action
        emptyHolder.removeAll();
        if (SystemContext.inventory.size() == 0) {
            Card hero = new Card(new BorderLayout(16, 0)).fill(Theme.PRIMARY_LIGHT).padding(18, 22, 18, 22);
            JPanel txt = UI.transparent(new BorderLayout(0, 3));
            txt.add(UI.label("Your catalogue is empty", Font.BOLD, 16, Theme.PRIMARY_DARK), BorderLayout.NORTH);
            txt.add(UI.label(user.canEditProducts() ? "Add your first product, import a CSV, or load sample data to explore the app."
                    : "Ask the store manager to add products before you start selling.", Font.PLAIN, 13, Theme.TEXT), BorderLayout.CENTER);
            hero.add(txt, BorderLayout.CENTER);
            if (user.canEditProducts()) {
                FlatButton add = FlatButton.primary("Add products").icon(Icons.Kind.PLUS);
                add.addActionListener(e -> go("products"));
                hero.add(add, BorderLayout.EAST);
            }
            emptyHolder.add(hero, BorderLayout.CENTER);
            emptyHolder.setBorder(BorderFactory.createEmptyBorder(0, 0, 16, 0));
        } else {
            emptyHolder.setBorder(BorderFactory.createEmptyBorder());
        }

        // ---- KPIs
        double target = SystemContext.settings.getDailyTarget();
        Double delta = ReportService.change(now.netSales(), before.netSales());
        if (manager && target > 0) {
            kpiSales.set(Formats.money0(now.netSales()), Formats.percent(now.netSales() / target * 100).replace(".0%", "%")
                    + " of " + Formats.money(target) + " target", now.netSales() >= target ? Theme.PRIMARY_DARK : Theme.MUTED);
        } else if (delta != null) {
            kpiSales.set(Formats.money0(now.netSales()), (delta >= 0 ? "▲ " : "▼ ") + Formats.percent(Math.abs(delta)) + " vs yesterday",
                    delta >= 0 ? Theme.PRIMARY_DARK : Theme.DANGER);
        } else {
            kpiSales.set(Formats.money0(now.netSales()), "excl. tax");
        }
        kpiTx.set(String.valueOf(now.transactions()), now.transactions() == 0 ? "no sales yet today"
                : "average basket " + Formats.money(now.averageBasket()));
        if (user.canViewCosts()) {
            double margin = now.netSales() <= 0 ? 0 : now.profit() / now.netSales() * 100;
            kpiProfit.set(Formats.money0(now.profit()), now.netSales() <= 0 ? " " : "margin " + Formats.percent(margin));
        } else {
            kpiProfit.set(String.valueOf(now.items()), "units across " + now.transactions() + " sales");
        }
        int low = SystemContext.inventory.checkLowStockProducts().size();
        int soon = SystemContext.inventory.getExpiringSoon().size();
        int expired = SystemContext.inventory.getExpired().size();
        kpiAlerts.set(String.valueOf(low + soon + expired), low + " low · " + soon + " expiring · " + expired + " expired",
                expired > 0 ? Theme.DANGER : low + soon > 0 ? Theme.WARN : Theme.PRIMARY_DARK);

        // ---- trend chart
        int days = new int[]{7, 14, 30}[range.getSelectedIndex()];
        var byDay = ReportService.netSalesByDay(all, today.minusDays(days - 1), today);
        List<String> labels = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        byDay.forEach((d, v) -> { labels.add(days <= 7 ? d.getDayOfWeek().getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH) : DAY.format(d)); values.add(v); });
        trend.setData(labels, values);

        // ---- category donut
        List<String> cl = new ArrayList<>();
        List<Double> cv = new ArrayList<>();
        for (ReportService.Amount a : ReportService.byCategory(scope(SystemContext.sales.between(today.minusDays(29), today)))) {
            cl.add(a.label());
            cv.add(a.value());
        }
        mix.setData(cl, cv);

        fillAttention();
        fillTop(manager ? scope(SystemContext.sales.between(today.minusDays(6), today)) : todays);
        fillRecent(all);
        body.revalidate();
        body.repaint();
    }

    private void fillAttention() {
        List<ListRow> rows = new ArrayList<>();
        int warn = SystemContext.settings.getExpiryWarnDays();
        List<Product> items = new ArrayList<>();
        items.addAll(SystemContext.inventory.getExpired());
        items.addAll(SystemContext.inventory.checkLowStockProducts());
        items.addAll(SystemContext.inventory.getExpiringSoon());
        List<Product> seen = new ArrayList<>();
        for (Product p : items) {
            if (seen.contains(p)) continue;
            seen.add(p);
            if (rows.size() >= 6) break;
            StockStatus st = p.getStatus(warn);
            Color[] col = UI.statusColors(st);
            String sub;
            if (st == StockStatus.EXPIRED) sub = "Expired " + Formats.date(p.getExpiryDate()) + " · " + p.getQuantity() + " on shelf";
            else if (st == StockStatus.EXPIRING) sub = "Expires in " + p.daysToExpiry() + (p.daysToExpiry() == 1 ? " day" : " days") + " · " + p.getQuantity() + " left";
            else sub = p.getQuantity() + " left · reorder at " + p.getReorderLevel();
            rows.add(new ListRow(p.getProductName(), sub, UI.statusBadge(st), () -> go(user.canAdjustStock() ? "stock" : "products")));
            if (col == null) continue;
        }
        attention.removeAll();
        attention.add(ListRow.stack("Everything is well stocked. Nothing needs attention.", rows), BorderLayout.NORTH);
        attention.revalidate();
        attention.repaint();
    }

    private void fillTop(List<Sale> sales) {
        List<ListRow> rows = new ArrayList<>();
        int rank = 1;
        for (ReportService.ProductStat p : ReportService.topProducts(sales, 6)) {
            rows.add(new ListRow(rank++ + ".  " + p.name(), p.quantity() + (p.quantity() == 1 ? " unit" : " units") + " · " + p.category(),
                    ListRow.value(Formats.money(p.revenue()), Theme.TEXT), null));
        }
        top.removeAll();
        top.add(ListRow.stack("No sales in this period yet.", rows), BorderLayout.NORTH);
        top.revalidate();
        top.repaint();
    }

    private void fillRecent(List<Sale> all) {
        List<ListRow> rows = new ArrayList<>();
        for (int i = 0; i < Math.min(6, all.size()); i++) {
            Sale s = all.get(i);
            String when = s.getDateTime().toLocalDate().equals(LocalDate.now())
                    ? "Today " + s.getDateTime().toLocalTime().toString().substring(0, 5)
                    : Formats.dateTime(s.getDateTime());
            JPanel right = UI.transparent(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            if (s.isVoided()) right.add(new UI.Badge("Voided", Theme.DANGER, Theme.DANGER_LIGHT));
            right.add(ListRow.value(Formats.money(s.getTotal()), s.isVoided() ? Theme.MUTED : Theme.TEXT));
            rows.add(new ListRow(s.getReceiptNumber() + "  ·  " + s.getItemCount() + (s.getItemCount() == 1 ? " item" : " items"),
                    when + " · " + s.getCashier() + " · " + s.getPayment().getLabel(), right, () -> ReceiptDialog.show(this, s)));
        }
        recent.removeAll();
        recent.add(ListRow.stack("No sales recorded yet.", rows), BorderLayout.NORTH);
        recent.revalidate();
        recent.repaint();
    }
}
