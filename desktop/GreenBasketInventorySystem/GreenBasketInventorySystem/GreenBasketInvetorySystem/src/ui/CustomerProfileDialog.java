package ui;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDateTime;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import model.Sale;
import model.SaleItem;
import service.ReportService;
import ui.kit.*;
import util.Fmt;
import util.SystemContext;

/** One customer's loyalty tier, progress, favourite products and receipts. */
final class CustomerProfileDialog {

    private CustomerProfileDialog() { }

    static void show(Window owner, String customerName) {
        List<Sale> sales = new ArrayList<>();
        for (Sale s : SystemContext.sales.all()) if (s.getCustomer().trim().equalsIgnoreCase(customerName.trim())) sales.add(s);

        double spend = 0;
        int visits = 0, units = 0;
        LocalDateTime first = null, last = null;
        Map<String, double[]> fav = new HashMap<>();
        for (Sale s : sales) {
            if (s.isVoided()) continue;
            visits++;
            spend += s.getTotal();
            units += s.itemCount();
            if (first == null || s.getTime().isBefore(first)) first = s.getTime();
            if (last == null || s.getTime().isAfter(last)) last = s.getTime();
            for (SaleItem i : s.getItems()) {
                double[] v = fav.computeIfAbsent(i.getName(), k -> new double[2]);
                v[0] += i.lineTotal();
                v[1] += i.getQuantity();
            }
        }
        String tier = ReportService.tierFor(spend);
        ModalDialog d = new ModalDialog(owner, customerName, first == null ? "No completed purchases yet"
                : "Customer since " + first.format(Fmt.DATE_TIME_SHORT).split(",")[0]);

        JPanel root = Ui.transparent(null);
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setPreferredSize(new Dimension(720, 560));

        // tier + progress
        JPanel tierCard = new JPanel(new BorderLayout(0, 10));
        tierCard.setBackground(Theme.surfaceAlt);
        tierCard.setBorder(new EmptyBorder(14, 16, 12, 16));
        JPanel th = Ui.transparent(new BorderLayout());
        JPanel tl = Ui.transparent(new FlowLayout(FlowLayout.LEFT, 10, 0));
        tl.add(Ui.label("Loyalty tier", Theme.bold(13), Theme.text));
        tl.add(new Badge(tier));
        th.add(tl, BorderLayout.WEST);
        double disc = ReportService.tierDiscount(tier);
        th.add(Ui.label(disc > 0 ? (int) disc + "% off offered at the till" : "No discount yet", Theme.font(12.5f), Theme.muted), BorderLayout.EAST);
        tierCard.add(th, BorderLayout.NORTH);
        tierCard.add(new TierBar(spend), BorderLayout.CENTER);
        String next;
        if (spend >= ReportService.GOLD_FROM) next = "Top tier reached - thank you for the loyalty!";
        else if (spend >= ReportService.SILVER_FROM) next = Fmt.money(ReportService.GOLD_FROM - spend) + " more to reach Gold";
        else next = Fmt.money(ReportService.SILVER_FROM - spend) + " more to reach Silver";
        tierCard.add(Ui.label(next, Theme.bold(12.5f), Theme.dark ? Theme.primaryHover : Theme.primaryDark), BorderLayout.SOUTH);
        tierCard.setAlignmentX(0);
        tierCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
        root.add(tierCard);
        root.add(Box.createVerticalStrut(14));

        // stats
        JPanel stats = new JPanel(new GridLayout(1, 4, 12, 0));
        stats.setOpaque(false);
        stats.add(stat("Visits", String.valueOf(visits)));
        stats.add(stat("Total spend", Fmt.money(spend)));
        stats.add(stat("Average basket", Fmt.money(visits == 0 ? 0 : spend / visits)));
        stats.add(stat("Items bought", String.valueOf(units)));
        stats.setAlignmentX(0);
        stats.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
        root.add(stats);
        root.add(Box.createVerticalStrut(14));

        // favourites + receipts
        List<ReportService.Line> top = new ArrayList<>();
        for (Map.Entry<String, double[]> e : fav.entrySet()) top.add(new ReportService.Line(e.getKey(), e.getValue()[0], e.getValue()[1]));
        top.sort((a, b) -> Double.compare(b.extra, a.extra));
        if (top.size() > 5) top = new ArrayList<>(top.subList(0, 5));
        Charts.Ranks ranks = new Charts.Ranks("bought", true);
        ranks.setData(top);
        JPanel favBox = Ui.transparent(new BorderLayout(0, 8));
        favBox.add(Ui.label("Favourite products", Theme.bold(13), Theme.text), BorderLayout.NORTH);
        favBox.add(ranks, BorderLayout.CENTER);

        ListTableModel<Sale> m = new ListTableModel<Sale>()
                .title("Receipt", 70, s -> "#" + s.getId())
                .text("Date", 100, s -> s.getTime().format(Fmt.DAY_MONTH))
                .money("Total", 90, Sale::getTotal)
                .badge("Status", 100, Sale::getStatus);
        m.setRows(sales.size() > 30 ? new ArrayList<>(sales.subList(0, 30)) : sales);
        Tables.DataTable t = Tables.table(m);
        t.setRowHeight(38);
        t.setEmptyText("No receipts");
        t.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                Sale s = Tables.selected(t);
                if (e.getClickCount() == 2 && s != null) ReceiptDialog.show(d, s, false);
            }
        });
        JScrollPane sp = Tables.scroll(t);
        sp.setBorder(BorderFactory.createLineBorder(Theme.border));
        JPanel recBox = Ui.transparent(new BorderLayout(0, 8));
        recBox.add(Ui.label("Recent receipts  -  double-click to open", Theme.bold(13), Theme.text), BorderLayout.NORTH);
        recBox.add(sp, BorderLayout.CENTER);

        JPanel lower = new JPanel(new GridLayout(1, 2, 20, 0));
        lower.setOpaque(false);
        lower.add(favBox);
        lower.add(recBox);
        lower.setAlignmentX(0);
        root.add(lower);

        d.setContent(root);
        Btn close = d.addAction(new Btn("Close", Btn.Kind.PRIMARY));
        close.addActionListener(e -> d.dispose());
        d.makeDefault(close);
        d.open();
    }

    private static JComponent stat(String label, String value) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(Theme.surfaceAlt);
        p.setBorder(new EmptyBorder(10, 14, 10, 14));
        JLabel l = Ui.label(label, Theme.font(12), Theme.muted);
        JLabel v = Ui.label(value, Theme.bold(16), Theme.text);
        l.setAlignmentX(0);
        v.setAlignmentX(0);
        p.add(l);
        p.add(Box.createVerticalStrut(3));
        p.add(v);
        return p;
    }

    /** Progress track from zero to the Gold threshold with Silver and Gold markers. */
    private static class TierBar extends JComponent {
        private final double spend;

        TierBar(double spend) { this.spend = spend; }

        @Override public Dimension getPreferredSize() { return new Dimension(400, 38); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = Gfx.aa(g);
            int w = getWidth();
            double max = ReportService.GOLD_FROM;
            Gfx.fillRound(g2, 0, 4, w, 10, 5, Theme.border);
            double frac = Math.min(1, spend / max);
            if (frac > 0) Gfx.fillRound(g2, 0, 4, Math.max(10, w * frac), 10, 5, Theme.primary);
            double[] marks = {ReportService.SILVER_FROM, ReportService.GOLD_FROM};
            String[] names = {"Silver " + Fmt.compact(marks[0]), "Gold " + Fmt.compact(marks[1])};
            Font f = Theme.font(11.5f);
            FontMetrics fm = g2.getFontMetrics(f);
            for (int i = 0; i < 2; i++) {
                int x = (int) Math.min(w - 2, w * marks[i] / max);
                g2.setColor(Theme.surfaceAlt);
                g2.fillRect(x - 1, 2, 2, 14);
                int tw = fm.stringWidth(names[i]);
                Gfx.text(g2, names[i], Math.max(0, Math.min(w - tw, x - tw / (i == 1 ? 1 : 2))), 32, f, Theme.muted);
            }
            Gfx.text(g2, "Rs. 0".replace("Rs.", Fmt.currency()), 0, 32, f, Theme.faint);
            g2.dispose();
        }
    }
}
