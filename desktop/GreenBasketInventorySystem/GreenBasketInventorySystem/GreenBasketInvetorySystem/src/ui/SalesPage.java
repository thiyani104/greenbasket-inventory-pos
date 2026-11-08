package ui;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import model.Sale;
import model.User;
import ui.kit.*;
import util.FileManager;
import util.Fmt;
import util.SystemContext;

/** Sales history: filter by period, cashier, status or text; open receipts; void a sale (managers); export. */
public class SalesPage extends Page {

    private final boolean mgr;
    private final Kpi k1, k2, k3, k4;
    private final Segmented period = new Segmented(true, "Today", "7 days", "30 days", "All time", "Custom");
    private final DatePicker from = new DatePicker();
    private final DatePicker to = new DatePicker();
    private final JPanel custom = Ui.transparent(new FlowLayout(FlowLayout.LEFT, 8, 0));
    private final Field search = new Field("Search receipt number or customer").icon("search");
    private final Combo<String> cashier = new Combo<>("All cashiers");
    private final Combo<String> status = new Combo<>("All statuses", "Completed", "Voided");
    private final ListTableModel<Sale> model = new ListTableModel<>();
    private Tables.DataTable table;
    private final JLabel selection = Ui.label("", Theme.font(13), Theme.muted);
    private final Btn viewBtn = new Btn("Receipt", "receipt", Btn.Kind.SECONDARY).compact();
    private final Btn voidBtn = new Btn("Void sale", "close", Btn.Kind.GHOST).compact();
    private boolean updating;

    public SalesPage(MainFrame host) {
        super(host);
        mgr = host.isManager();
        k1 = new Kpi("Revenue", "receipt", Theme.chart[0]);
        k2 = new Kpi("Sales", "cart", Theme.chart[1]);
        k3 = new Kpi("Average sale", "tag", Theme.chart[2]);
        k4 = new Kpi(mgr ? "Gross profit" : "Items sold", mgr ? "chart" : "box", Theme.chart[3]);

        model.title("Receipt", 80, s -> "#" + s.getId());
        model.dateTime("Time", 150, Sale::getTime);
        model.muted("Cashier", 90, Sale::getCashier);
        model.text("Customer", 140, s -> s.getCustomer().isEmpty() ? "-" : s.getCustomer());
        model.text("Payment", 100, Sale::getPayment);
        model.integer("Items", 56, Sale::itemCount);
        model.money("Total", 110, Sale::getTotal);
        if (mgr) model.money("Profit", 100, Sale::profit);
        model.badge("Status", 110, Sale::getStatus);
        table = Tables.table(model);
        table.setEmptyText("No sales match your filters");

        Btn export = new Btn("Export CSV", "download", Btn.Kind.SECONDARY);
        export.addActionListener(e -> export());
        Btn pos = new Btn("New sale", "cart", Btn.Kind.PRIMARY);
        pos.addActionListener(e -> host.go(MainFrame.Nav.POS));
        JComponent head = Ui.header(mgr ? "Sales" : "My sales", "Every receipt, searchable by date, cashier and customer", actions(export, pos));

        JPanel kpis = new JPanel(new GridLayout(1, 4, 16, 0));
        kpis.setOpaque(false);
        kpis.add(k1); kpis.add(k2); kpis.add(k3); kpis.add(k4);
        kpis.setBorder(new EmptyBorder(0, 0, 16, 0));

        // filters: [search ........] [cashier] [status]  /  [period] [custom dates]
        cashier.setPreferredSize(new Dimension(170, 40));
        status.setPreferredSize(new Dimension(160, 40));
        JPanel row1 = Ui.transparent(new BorderLayout(12, 0));
        row1.add(search, BorderLayout.CENTER);
        JPanel combos = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        combos.setOpaque(false);
        if (mgr) combos.add(cashier);
        combos.add(status);
        row1.add(combos, BorderLayout.EAST);
        row1.setBorder(new EmptyBorder(0, 0, 10, 0));

        custom.add(Ui.label("From", Theme.font(12.5f), Theme.muted));
        custom.add(sized(from, 180));
        custom.add(Ui.label("to", Theme.font(12.5f), Theme.muted));
        custom.add(sized(to, 180));
        custom.setVisible(false);
        JPanel row2 = Ui.transparent(new BorderLayout(14, 0));
        JPanel pw = Ui.transparent(new GridBagLayout());
        pw.add(period);
        row2.add(pw, BorderLayout.WEST);
        row2.add(custom, BorderLayout.CENTER);
        row2.setBorder(new EmptyBorder(0, 0, 12, 0));

        JPanel filters = Ui.transparent(new BorderLayout());
        filters.add(row1, BorderLayout.NORTH);
        filters.add(row2, BorderLayout.SOUTH);

        period.setSelected(1);
        period.onChange(i -> { custom.setVisible(i == 4); if (i == 4 && from.getDateOrNull() == null) { from.setDate(LocalDate.now().minusDays(6)); to.setDate(LocalDate.now()); } applyFilters(); });
        from.onChange(this::applyFilters);
        to.onChange(this::applyFilters);
        search.getDocument().addDocumentListener(new SimpleDoc(this::applyFilters));
        cashier.addActionListener(e -> applyFilters());
        status.addActionListener(e -> applyFilters());

        Card card = new Card(new BorderLayout(0, 12), 14);
        card.add(Tables.scroll(table), BorderLayout.CENTER);
        JPanel foot = Ui.transparent(new BorderLayout(10, 0));
        foot.add(selection, BorderLayout.CENTER);
        JPanel fb = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        fb.setOpaque(false);
        fb.add(viewBtn);
        if (mgr) fb.add(voidBtn);
        foot.add(fb, BorderLayout.EAST);
        foot.setPreferredSize(new Dimension(100, 34));
        card.add(foot, BorderLayout.SOUTH);

        table.getSelectionModel().addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) onSelect(); });
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { if (e.getClickCount() == 2 && selected() != null) ReceiptDialog.show(host, selected(), false); }
        });
        viewBtn.addActionListener(e -> { if (selected() != null) ReceiptDialog.show(host, selected(), false); });
        voidBtn.addActionListener(e -> voidSale());

        JPanel body = Ui.transparent(new BorderLayout());
        JPanel top = Ui.transparent(new BorderLayout());
        top.add(kpis, BorderLayout.NORTH);
        top.add(filters, BorderLayout.SOUTH);
        body.add(top, BorderLayout.NORTH);
        body.add(card, BorderLayout.CENTER);
        shell(head, body);
        refresh();
    }

    private static JComponent sized(JComponent c, int w) {
        c.setPreferredSize(new Dimension(w, 40));
        return c;
    }

    private Sale selected() { return Tables.selected(table); }

    @Override public void refresh() {
        updating = true;
        Object keep = cashier.getSelectedItem();
        List<String> names = new ArrayList<>();
        names.add("All cashiers");
        for (User u : SystemContext.auth.getUsers()) names.add(u.getUsername());
        cashier.setModel(new DefaultComboBoxModel<>(names.toArray(new String[0])));
        cashier.setSelectedItem(keep != null && names.contains(keep.toString()) ? keep : "All cashiers");
        updating = false;
        applyFilters();
    }

    private void applyFilters() {
        if (updating) return;
        LocalDate f = null, t = null;
        LocalDate today = LocalDate.now();
        switch (period.getSelected()) {
            case 0: f = today; t = today; break;
            case 1: f = today.minusDays(6); t = today; break;
            case 2: f = today.minusDays(29); t = today; break;
            case 4:
                try {
                    f = from.getDate();
                    t = to.getDate();
                } catch (IllegalArgumentException ex) {
                    return;
                }
                break;
            default: break;
        }
        String who = !mgr ? host.user().getUsername() : cashier.getSelectedIndex() <= 0 ? null : String.valueOf(cashier.getSelectedItem());
        int st = status.getSelectedIndex();
        String q = search.getText().trim().toLowerCase().replace("#", "");
        Integer keep = selected() == null ? null : selected().getId();
        List<Sale> rows = new ArrayList<>();
        double revenue = 0, profit = 0;
        int orders = 0, units = 0;
        for (Sale s : SystemContext.sales.between(f, t, who)) {
            if (st == 1 && s.isVoided()) continue;
            if (st == 2 && !s.isVoided()) continue;
            if (!q.isEmpty() && !(String.valueOf(s.getId()).contains(q) || s.getCustomer().toLowerCase().contains(q)
                    || s.getCashier().toLowerCase().contains(q))) continue;
            rows.add(s);
            if (!s.isVoided()) {
                revenue += s.getTotal();
                profit += s.profit();
                orders++;
                units += s.itemCount();
            }
        }
        model.setRows(rows);
        k1.set(Fmt.money(revenue), "Excluding voided sales", Kpi.Tone.NEUTRAL);
        k2.set(String.valueOf(orders), rows.size() - orders > 0 ? (rows.size() - orders) + " voided" : "All completed", Kpi.Tone.NEUTRAL);
        k3.set(Fmt.money(orders == 0 ? 0 : revenue / orders), orders == 0 ? "-" : units + " items sold", Kpi.Tone.NEUTRAL);
        if (mgr) k4.set(Fmt.money(profit), revenue <= 0 ? "-" : String.format("%.0f%% of revenue", profit / revenue * 100), Kpi.Tone.GOOD);
        else k4.set(String.valueOf(units), "in this period", Kpi.Tone.NEUTRAL);
        if (keep != null) {
            for (int i = 0; i < table.getRowCount(); i++) {
                Sale s = model.at(table.convertRowIndexToModel(i));
                if (s != null && s.getId() == keep) { table.setRowSelectionInterval(i, i); break; }
            }
        }
        onSelect();
    }

    private void onSelect() {
        Sale s = selected();
        viewBtn.setEnabled(s != null);
        voidBtn.setEnabled(s != null && !s.isVoided());
        if (s == null) {
            selection.setText(model.getRowCount() + " sale(s) shown  -  double-click one to open its receipt");
            selection.setFont(Theme.font(13));
            selection.setForeground(Theme.muted);
        } else {
            selection.setText("#" + s.getId() + "  -  " + s.getTime().format(Fmt.DATE_TIME_SHORT) + "  -  " + Fmt.money(s.getTotal()));
            selection.setFont(Theme.bold(13));
            selection.setForeground(Theme.text);
        }
    }

    private void voidSale() {
        Sale s = selected();
        if (s == null || s.isVoided()) return;
        if (Dialogs.confirm(host, "Void sale #" + s.getId(),
                "Void this " + Fmt.money(s.getTotal()) + " sale?\n\nThe " + s.itemCount() + " item(s) go back into stock and the sale no longer counts towards revenue. This cannot be undone.",
                "Void sale", true)) {
            try {
                SystemContext.sales.voidSale(s, host.user().getUsername());
                host.toast("Sale #" + s.getId() + " voided and stock restored");
            } catch (IllegalArgumentException ex) {
                Dialogs.error(host, ex.getMessage());
            }
        }
    }

    private void export() {
        Path p = Pick.save(this, "sales-" + LocalDate.now().toString().replace("-", "") + ".csv", "CSV file", "csv");
        if (p == null) return;
        List<String[]> out = new ArrayList<>();
        for (Sale s : model.rows()) {
            out.add(new String[]{String.valueOf(s.getId()), Fmt.dateTime(s.getTime()), s.getCashier(), s.getCustomer(), s.getPayment(),
                    String.valueOf(s.itemCount()), String.format("%.2f", s.getSubtotal()), String.format("%.2f", s.getDiscount()),
                    String.format("%.2f", s.getTax()), String.format("%.2f", s.getTotal()),
                    mgr ? String.format("%.2f", s.profit()) : "", s.getStatus()});
        }
        try {
            FileManager.writeCsvFile(p, new String[]{"Receipt", "Time", "Cashier", "Customer", "Payment", "Items", "Subtotal",
                    "Discount", "Tax", "Total", "Profit", "Status"}, out);
            host.toast("Exported " + out.size() + " sales");
        } catch (RuntimeException ex) {
            Dialogs.error(host, ex.getMessage());
        }
    }
}
