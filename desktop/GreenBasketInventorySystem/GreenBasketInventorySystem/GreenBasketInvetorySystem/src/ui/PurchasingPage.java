package ui;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import model.Product;
import model.PurchaseItem;
import model.PurchaseOrder;
import ui.kit.*;
import util.Fmt;
import util.SystemContext;

/** Purchase orders: suggested reorders, raising orders, receiving deliveries into stock. */
public class PurchasingPage extends Page {

    private final Kpi k1, k2, k3, k4;
    private final Segmented filter = new Segmented("Open", "Received", "Cancelled", "All");
    private final Field search = new Field("Search by supplier or order number").icon("search");
    private final ListTableModel<PurchaseOrder> model = new ListTableModel<PurchaseOrder>()
            .title("Order", 80, po -> "#" + po.getId())
            .text("Supplier", 190, PurchaseOrder::getSupplier)
            .dateTime("Created", 150, PurchaseOrder::getCreated)
            .muted("By", 90, PurchaseOrder::getCreatedBy)
            .integer("Lines", 60, po -> po.getItems().size())
            .integer("Units", 70, PurchaseOrder::units)
            .money("Total", 120, PurchaseOrder::total)
            .badge("Status", 110, PurchaseOrder::getStatus)
            .dateTime("Received", 150, PurchaseOrder::getReceived);
    private final Tables.DataTable table = Tables.table(model);
    private final JLabel selection = Ui.label("", Theme.font(13), Theme.muted);
    private final Btn viewBtn = new Btn("View", "eye", Btn.Kind.SECONDARY).compact();
    private final Btn receiveBtn = new Btn("Mark received", "check", Btn.Kind.SOFT).compact();
    private final Btn cancelBtn = new Btn("Cancel order", "close", Btn.Kind.GHOST).compact();
    private final Btn suggest = new Btn("Suggested orders", "alert", Btn.Kind.SECONDARY);

    public PurchasingPage(MainFrame host) {
        super(host);
        k1 = new Kpi("Open orders", "truck", Theme.chart[1]);
        k2 = new Kpi("Value on order", "tag", Theme.chart[2]);
        k3 = new Kpi("Received this month", "check", Theme.chart[0]);
        k4 = new Kpi("Products to reorder", "alert", Theme.chart[4]);

        Btn neu = new Btn("New order", "plus", Btn.Kind.PRIMARY);
        neu.addActionListener(e -> newOrder());
        suggest.addActionListener(e -> showSuggestions());
        JComponent head = Ui.header("Purchasing", "Order stock from suppliers and book deliveries into inventory", actions(suggest, neu));

        JPanel kpis = new JPanel(new GridLayout(1, 4, 16, 0));
        kpis.setOpaque(false);
        kpis.add(k1); kpis.add(k2); kpis.add(k3); kpis.add(k4);
        kpis.setBorder(new EmptyBorder(0, 0, 16, 0));

        JPanel filters = Ui.transparent(new BorderLayout(12, 0));
        filters.setBorder(new EmptyBorder(0, 0, 12, 0));
        filters.add(search, BorderLayout.CENTER);
        JPanel fw = Ui.transparent(new GridBagLayout());
        fw.add(filter);
        filters.add(fw, BorderLayout.EAST);
        filter.onChange(i -> applyFilter());
        search.getDocument().addDocumentListener(new SimpleDoc(this::applyFilter));

        table.setEmptyText("No purchase orders here");
        Card card = new Card(new BorderLayout(0, 12), 14);
        card.add(Tables.scroll(table), BorderLayout.CENTER);
        JPanel foot = Ui.transparent(new BorderLayout(10, 0));
        foot.add(selection, BorderLayout.CENTER);
        JPanel fb = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        fb.setOpaque(false);
        fb.add(viewBtn);
        fb.add(receiveBtn);
        fb.add(cancelBtn);
        foot.add(fb, BorderLayout.EAST);
        foot.setPreferredSize(new Dimension(100, 34));
        card.add(foot, BorderLayout.SOUTH);

        table.getSelectionModel().addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) onSelect(); });
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && selected() != null) PurchaseDialogs.view(host, selected());
            }
        });
        viewBtn.addActionListener(e -> { if (selected() != null) PurchaseDialogs.view(host, selected()); });
        receiveBtn.addActionListener(e -> receive());
        cancelBtn.addActionListener(e -> cancel());

        JPanel body = Ui.transparent(new BorderLayout());
        JPanel top = Ui.transparent(new BorderLayout());
        top.add(kpis, BorderLayout.NORTH);
        top.add(filters, BorderLayout.SOUTH);
        body.add(top, BorderLayout.NORTH);
        body.add(card, BorderLayout.CENTER);
        shell(head, body);
        refresh();
    }

    private PurchaseOrder selected() { return Tables.selected(table); }

    @Override public void refresh() {
        List<PurchaseOrder> all = SystemContext.purchases.all();
        int open = 0, units = 0, monthCount = 0;
        double value = 0, monthValue = 0;
        YearMonth now = YearMonth.now();
        for (PurchaseOrder po : all) {
            if (po.isOpen()) { open++; units += po.units(); value += po.total(); }
            if (PurchaseOrder.RECEIVED.equals(po.getStatus()) && po.getReceived() != null && YearMonth.from(po.getReceived()).equals(now)) {
                monthCount++;
                monthValue += po.total();
            }
        }
        int toReorder = SystemContext.purchases.suggestions().values().stream().mapToInt(List::size).sum();
        k1.set(String.valueOf(open), units + " units on the way", Kpi.Tone.NEUTRAL);
        k2.set(Fmt.money(value), open == 0 ? "Nothing outstanding" : "Not yet received", Kpi.Tone.NEUTRAL);
        k3.set(String.valueOf(monthCount), Fmt.money(monthValue) + " received", Kpi.Tone.GOOD);
        k4.set(String.valueOf(toReorder), toReorder == 0 ? "All covered" : "Low or sold out, not on order", toReorder == 0 ? Kpi.Tone.GOOD : Kpi.Tone.WARN);
        suggest.setText(toReorder == 0 ? "Suggested orders" : "Suggested orders (" + toReorder + ")");
        applyFilter();
    }

    private void applyFilter() {
        Integer keep = selected() == null ? null : selected().getId();
        String q = search.getText().trim().toLowerCase();
        List<PurchaseOrder> rows = new ArrayList<>();
        for (PurchaseOrder po : SystemContext.purchases.all()) {
            int f = filter.getSelected();
            if (f == 0 && !PurchaseOrder.ORDERED.equals(po.getStatus())) continue;
            if (f == 1 && !PurchaseOrder.RECEIVED.equals(po.getStatus())) continue;
            if (f == 2 && !PurchaseOrder.CANCELLED.equals(po.getStatus())) continue;
            if (!q.isEmpty() && !(po.getSupplier().toLowerCase().contains(q) || String.valueOf(po.getId()).contains(q.replace("#", "")))) continue;
            rows.add(po);
        }
        model.setRows(rows);
        if (keep != null) {
            for (int i = 0; i < table.getRowCount(); i++) {
                PurchaseOrder po = model.at(table.convertRowIndexToModel(i));
                if (po != null && po.getId() == keep) { table.setRowSelectionInterval(i, i); break; }
            }
        }
        onSelect();
    }

    private void onSelect() {
        PurchaseOrder po = selected();
        viewBtn.setEnabled(po != null);
        receiveBtn.setEnabled(po != null && po.isOpen());
        cancelBtn.setEnabled(po != null && po.isOpen());
        if (po == null) {
            selection.setText(model.getRowCount() + " order(s)  -  select one to view or receive it");
            selection.setFont(Theme.font(13));
            selection.setForeground(Theme.muted);
        } else {
            selection.setText("#" + po.getId() + "  -  " + po.getSupplier() + "  -  " + po.units() + " units  -  " + Fmt.money(po.total()));
            selection.setFont(Theme.bold(13));
            selection.setForeground(Theme.text);
        }
    }

    // ---------------------------------------------------------------- actions

    public void newOrder() {
        PurchaseDialogs.create(host, null, null, null);
    }

    /** Opens a new order with this product already chosen. */
    public void newOrderFor(Product p) {
        PurchaseDialogs.create(host, null, null, p);
    }

    /** Called from the dashboard and the bell: raise orders for everything that is low. */
    public void showSuggestions() {
        PurchaseDialogs.suggestions(host);
    }

    private void receive() {
        PurchaseOrder po = selected();
        if (po == null || !po.isOpen()) return;
        if (!Dialogs.confirm(host, "Receive delivery",
                "Add " + po.units() + " units from " + po.getSupplier() + " (order #" + po.getId() + ") to stock?\n\nCost prices of the products are updated to this order's prices.",
                "Mark received", false)) return;
        try {
            SystemContext.purchases.receive(po, host.user().getUsername());
            host.toast("Delivery booked - stock updated");
        } catch (IllegalArgumentException ex) {
            Dialogs.error(host, ex.getMessage());
        }
    }

    private void cancel() {
        PurchaseOrder po = selected();
        if (po == null || !po.isOpen()) return;
        if (Dialogs.confirm(host, "Cancel order", "Cancel purchase order #" + po.getId() + " to " + po.getSupplier() + "?", "Cancel order", true)) {
            SystemContext.purchases.cancel(po, host.user().getUsername());
            host.toastInfo("Order #" + po.getId() + " cancelled");
        }
    }
}
