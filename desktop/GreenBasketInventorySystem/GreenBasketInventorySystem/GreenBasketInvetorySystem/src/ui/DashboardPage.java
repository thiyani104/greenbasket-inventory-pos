package ui;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import model.Product;
import model.Sale;
import service.ReportService;
import ui.kit.*;
import util.Fmt;
import util.SystemContext;

/** Today at a glance: KPIs, revenue trend, stock alerts, best sellers and recent sales. */
public class DashboardPage extends Page {

    private final JLabel greeting = Ui.h1("");
    private final JLabel subtitle = Ui.muted("");
    private final Kpi k1, k2, k3, k4;
    private final Charts.Area trend = new Charts.Area(Theme.chart[0], "orders");
    private final Charts.Donut mix = new Charts.Donut("30-day revenue");
    private final Charts.Ranks top = new Charts.Ranks("sold", true);
    private final JPanel attention = new JPanel();
    private final ListTableModel<Sale> recentModel = new ListTableModel<Sale>()
            .title("Receipt", 90, s -> "#" + s.getId())
            .dateTime("Time", 130, Sale::getTime)
            .money("Total", 110, Sale::getTotal)
            .badge("Status", 100, Sale::getStatus);
    private final Tables.DataTable recent = Tables.table(recentModel);
    private final JPanel attentionActions = Ui.transparent(new FlowLayout(FlowLayout.RIGHT, 8, 0));

    public DashboardPage(MainFrame host) {
        super(host);
        boolean mgr = host.isManager();
        k1 = new Kpi(mgr ? "Revenue today" : "My sales today", "receipt", Theme.chart[0]);
        k2 = new Kpi(mgr ? "Orders today" : "My orders today", "cart", Theme.chart[1]);
        k3 = new Kpi(mgr ? "Gross profit today" : "Needs restock", mgr ? "chart" : "alert", mgr ? Theme.chart[3] : Theme.chart[2]);
        k4 = new Kpi(mgr ? "Stock value (at cost)" : "Expiring soon", mgr ? "box" : "clock", mgr ? Theme.chart[2] : Theme.chart[4]);

        JPanel body = Ui.transparent(null);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        JPanel actions = Ui.transparent(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        Btn sale = new Btn("New sale", "cart", Btn.Kind.PRIMARY);
        sale.addActionListener(e -> host.go(MainFrame.Nav.POS));
        actions.add(sale);
        if (mgr) {
            Btn add = new Btn("Add product", "plus", Btn.Kind.SECONDARY);
            add.addActionListener(e -> {
                host.go(MainFrame.Nav.INVENTORY);
                ((InventoryPage) host.page(MainFrame.Nav.INVENTORY)).addProduct();
            });
            actions.add(add);
        }
        JPanel head = Ui.transparent(new BorderLayout());
        JPanel titles = Ui.transparent(null);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        greeting.setAlignmentX(0);
        subtitle.setAlignmentX(0);
        titles.add(greeting);
        titles.add(Box.createVerticalStrut(3));
        titles.add(subtitle);
        head.add(titles, BorderLayout.WEST);
        head.add(actions, BorderLayout.EAST);
        body.add(stretch(head, 0, 18));

        JPanel kpis = new JPanel(new GridLayout(1, 4, 18, 0));
        kpis.setOpaque(false);
        kpis.add(k1); kpis.add(k2); kpis.add(k3); kpis.add(k4);
        body.add(stretch(kpis, 0, 18));

        if (mgr) {
            body.add(stretch(split(18, new double[]{0.66, 0.34},
                    Ui.section("Revenue - last 14 days", "Hover a point for the day's total", trend, null),
                    Ui.section("Sales by category", "Last 30 days", mix, null)), 360, 18));
        }

        attention.setLayout(new BoxLayout(attention, BoxLayout.Y_AXIS));
        attention.setOpaque(false);
        Card att = Ui.section("Needs attention", "Stock that needs a decision", attention, null);
        att.add(attentionActions, BorderLayout.SOUTH);

        recent.setRowHeight(40);
        recent.setEmptyText("No sales yet");
        JScrollPane rs = Tables.scroll(recent);
        Btn all = new Btn("View all", Btn.Kind.GHOST).compact();
        all.addActionListener(e -> host.go(MainFrame.Nav.SALES));
        Card rc = Ui.section(mgr ? "Recent sales" : "My recent sales", null, rs, all);
        recent.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { if (e.getClickCount() == 2) host.go(MainFrame.Nav.SALES); }
        });

        if (mgr) {
            body.add(stretch(split(18, new double[]{0.34, 0.27, 0.39}, att,
                    Ui.section("Best sellers", "By revenue, last 7 days", top, null), rc), 400, 0));
        } else {
            body.add(stretch(split(18, new double[]{0.5, 0.5}, att, rc), 400, 0));
        }

        add(Ui.pageScroll(body), BorderLayout.CENTER);
    }

    /** Fixes the row's height and lets it grow to the full width. */
    private static JComponent stretch(JComponent c, int height, int gapBelow) {
        JPanel w = Ui.transparent(new BorderLayout());
        w.add(c);
        w.setBorder(new EmptyBorder(0, 0, gapBelow, 0));
        w.setAlignmentX(0);
        if (height > 0) {
            w.setPreferredSize(new Dimension(100, height + gapBelow));
            w.setMinimumSize(new Dimension(100, height + gapBelow));
            w.setMaximumSize(new Dimension(Integer.MAX_VALUE, height + gapBelow));
        } else {
            w.setMaximumSize(new Dimension(Integer.MAX_VALUE, w.getPreferredSize().height));
        }
        return w;
    }

    /** Side-by-side cards sharing the width by the given proportions. */
    private static JPanel split(int gap, double[] weights, JComponent... cards) {
        JPanel p = Ui.transparent(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.BOTH;
        gc.weighty = 1;
        for (int i = 0; i < cards.length; i++) {
            gc.gridx = i;
            gc.weightx = weights[i];
            gc.insets = new Insets(0, 0, 0, i == cards.length - 1 ? 0 : gap);
            cards[i].setPreferredSize(new Dimension((int) (weights[i] * 1000), 100));
            p.add(cards[i], gc);
        }
        return p;
    }

    @Override public void refresh() {
        boolean mgr = host.isManager();
        String first = host.user().getFullName().split("\\s+")[0];
        int h = java.time.LocalTime.now().getHour();
        greeting.setText((h < 12 ? "Good morning" : h < 18 ? "Good afternoon" : "Good evening") + ", " + first);
        subtitle.setText("Here is what is happening at " + SystemContext.settings.getStoreName() + " today.");

        LocalDate today = LocalDate.now();
        List<Product> restock = SystemContext.inventory.needsRestock();
        int warn = SystemContext.settings.getExpiryWarnDays();
        List<Product> expiring = SystemContext.inventory.expiringSoon(warn);
        List<Product> expired = SystemContext.inventory.expired();

        if (mgr) {
            ReportService.Summary t = SystemContext.reports.summary(today, today);
            ReportService.Summary y = SystemContext.reports.summary(today.minusDays(1), today.minusDays(1));
            String delta;
            Kpi.Tone tone = Kpi.Tone.NEUTRAL;
            if (t.revenue <= 0) delta = "No sales yet today";
            else if (y.revenue <= 0) delta = "No sales yesterday";
            else {
                double pct = (t.revenue - y.revenue) / y.revenue * 100;
                delta = String.format("%+.1f%% vs yesterday", pct);
                tone = pct >= 0 ? Kpi.Tone.GOOD : Kpi.Tone.BAD;
            }
            k1.set(Fmt.money(t.revenue), delta, tone);
            k2.set(String.valueOf(t.orders), t.orders == 0 ? "Waiting for the first customer" : t.units + " items sold", Kpi.Tone.NEUTRAL);
            double margin = t.revenue - t.tax <= 0 ? 0 : t.profit / (t.revenue - t.tax) * 100;
            k3.set(Fmt.money(t.profit), t.orders == 0 ? "-" : String.format("%.0f%% margin", margin), Kpi.Tone.GOOD);
            k4.set(Fmt.money(SystemContext.inventory.totalCostValue()),
                    "Retail value " + Fmt.money(SystemContext.inventory.totalRetailValue()), Kpi.Tone.NEUTRAL);
            trend.setData(SystemContext.reports.dailyRevenue(14));
            mix.setData(SystemContext.reports.revenueByCategory(today.minusDays(29), today));
            top.setData(SystemContext.reports.topProducts(today.minusDays(6), today, 6));
            recentModel.setRows(limit(SystemContext.sales.all(), 12));
        } else {
            String me = host.user().getUsername();
            List<Sale> mine = SystemContext.sales.between(today, today, me);
            double rev = 0;
            int orders = 0;
            for (Sale s : mine) if (!s.isVoided()) { rev += s.getTotal(); orders++; }
            k1.set(Fmt.money(rev), orders == 0 ? "No sales yet today" : "Your till today", Kpi.Tone.NEUTRAL);
            k2.set(String.valueOf(orders), orders == 0 ? "Ring up the first one" : Fmt.money(rev / orders) + " average", Kpi.Tone.NEUTRAL);
            k3.set(String.valueOf(restock.size()), restock.isEmpty() ? "Everything is stocked" : "Tell the manager", restock.isEmpty() ? Kpi.Tone.GOOD : Kpi.Tone.WARN);
            k4.set(String.valueOf(expiring.size() + expired.size()), expired.isEmpty() ? "Within " + warn + " days" : expired.size() + " already expired",
                    expiring.isEmpty() && expired.isEmpty() ? Kpi.Tone.GOOD : Kpi.Tone.WARN);
            recentModel.setRows(limit(SystemContext.sales.between(null, null, me), 12));
        }
        buildAttention(restock, expiring, expired);
    }

    private static <T> List<T> limit(List<T> l, int n) { return l.size() <= n ? l : new ArrayList<>(l.subList(0, n)); }

    private void buildAttention(List<Product> restock, List<Product> expiring, List<Product> expired) {
        attention.removeAll();
        attentionActions.removeAll();
        int shown = 0;
        for (Product p : expired) {
            if (shown++ >= 7) break;
            attention.add(attentionRow("EXPIRED", Theme.danger, p.getProductName(), "Expired " + (-p.daysToExpiry()) + " day(s) ago - " + p.getQuantity() + " on shelf"));
        }
        for (Product p : restock) {
            if (shown++ >= 7) break;
            attention.add(attentionRow(p.isOutOfStock() ? "OUT" : "LOW", p.isOutOfStock() ? Theme.danger : Theme.warning, p.getProductName(),
                    p.isOutOfStock() ? "Sold out" : p.getQuantity() + " left (reorder at " + p.getReorderLevel() + ")"));
        }
        for (Product p : expiring) {
            if (shown++ >= 7) break;
            long d = p.daysToExpiry();
            attention.add(attentionRow("EXPIRING", Theme.warning, p.getProductName(), d == 0 ? "Expires today" : "Expires in " + d + " day(s)"));
        }
        int total = expired.size() + restock.size() + expiring.size();
        if (total == 0) {
            JLabel ok = Ui.label("All clear - stock levels and dates look healthy.", Theme.font(13), Theme.success);
            ok.setBorder(new EmptyBorder(14, 0, 14, 0));
            attention.add(ok);
        } else if (total > shown) {
            attention.add(Ui.label("+ " + (total - shown) + " more in Inventory", Theme.font(12), Theme.muted));
        }
        if (host.isManager()) {
            if (!restock.isEmpty()) {
                Btn po = new Btn("Create purchase order", "truck", Btn.Kind.SOFT).compact();
                po.addActionListener(e -> {
                    host.go(MainFrame.Nav.PURCHASING);
                    ((PurchasingPage) host.page(MainFrame.Nav.PURCHASING)).showSuggestions();
                });
                attentionActions.add(po);
            }
            if (!expired.isEmpty()) {
                Btn wo = new Btn("Write off expired", "trash", Btn.Kind.SECONDARY).compact();
                wo.addActionListener(e -> {
                    if (Dialogs.confirm(host, "Write off expired stock",
                            "Remove all " + expired.size() + " expired product(s) from stock? This is recorded in the stock ledger.",
                            "Write off", true)) {
                        int n = SystemContext.inventory.writeOffExpired();
                        host.toast(n + " expired unit(s) written off");
                    }
                });
                attentionActions.add(wo);
            }
        }
        attention.revalidate();
        attention.repaint();
        attentionActions.revalidate();
    }

    private JComponent attentionRow(String badge, Color tone, String name, String detail) {
        JPanel r = new JPanel(new BorderLayout(10, 0));
        r.setOpaque(false);
        r.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.border), new EmptyBorder(9, 0, 9, 0)));
        r.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));
        r.setAlignmentX(0);
        JPanel t = Ui.transparent(null);
        t.setLayout(new BoxLayout(t, BoxLayout.Y_AXIS));
        t.add(Ui.label(name, Theme.bold(13), Theme.text));
        t.add(Ui.label(detail, Theme.font(12), Theme.muted));
        r.add(t, BorderLayout.CENTER);
        Badge b = new Badge(badge);
        r.add(b, BorderLayout.EAST);
        return r;
    }
}
