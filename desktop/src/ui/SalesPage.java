package ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.TableRowSorter;
import model.PaymentMethod;
import model.Sale;
import model.User;
import service.ReportService;
import service.ServiceException;
import util.FileManager;
import util.Formats;
import util.SystemContext;

/** Sales history: find a sale, reprint its receipt, void it (managers) or export the list. */
public class SalesPage extends Page {

    private final User user = SystemContext.currentUser;
    private final boolean canVoid = user.canVoidSales();
    private final FlatTextField search = new FlatTextField(18).placeholder("Receipt number or cashier").searchIcon();
    private final FlatComboBox<Object> payment = new FlatComboBox<>();
    private final FlatComboBox<String> status = new FlatComboBox<>("All sales", "Completed", "Voided");
    private final Segmented range = new Segmented("Today", "7 days", "30 days", "All time").onChange(this::applyFilter);
    private final RowModel<Sale> model = new RowModel<>(
            new String[]{"Receipt", "Date & time", "Cashier", "Items", "Payment", "Total", "Status"},
            new Class<?>[]{String.class, String.class, String.class, Integer.class, String.class, Double.class, String.class},
            (s, c) -> switch (c) {
                case 0 -> s.getReceiptNumber();
                case 1 -> Formats.dateTime(s.getDateTime());
                case 2 -> s.getCashier();
                case 3 -> s.getItemCount();
                case 4 -> s.getPayment().getLabel();
                case 5 -> s.getTotal();
                default -> s.isVoided() ? "Voided" : "Completed";
            });
    private final JTable table = new JTable(model);
    private final JLabel summary = UI.muted(" ");
    private boolean loading;

    public SalesPage() {
        UI.styleTable(table);
        TableRowSorter<RowModel<Sale>> sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);
        UI.widths(table, 110, 150, 110, 60, 110, 110, 110);
        table.getColumnModel().getColumn(0).setCellRenderer(new UI.Cell(SwingConstants.LEFT, true));
        table.getColumnModel().getColumn(1).setCellRenderer(new UI.Cell(SwingConstants.LEFT, true));
        table.getColumnModel().getColumn(5).setCellRenderer(new UI.Cell(SwingConstants.RIGHT) {
            @Override protected String format(Object v) { return v instanceof Number n ? Formats.money(n.doubleValue()) : ""; }
            @Override protected Color colorFor(Object v, int row, JTable t) {
                return model.at(t.convertRowIndexToModel(row)).isVoided() ? Theme.MUTED : Theme.TEXT;
            }
        });
        table.getColumnModel().getColumn(4).setCellRenderer(UI.pillRenderer(v -> {
            String s = v.toString();
            if (s.equals(PaymentMethod.CASH.getLabel())) return new Color[]{Theme.PRIMARY_DARK, Theme.PRIMARY_LIGHT};
            if (s.equals(PaymentMethod.CARD.getLabel())) return new Color[]{new Color(0x1F5FA8), Theme.INFO_LIGHT};
            return new Color[]{new Color(0x6A3FB0), new Color(0xEFE7FA)};
        }));
        table.getColumnModel().getColumn(6).setCellRenderer(UI.pillRenderer(v ->
                v.toString().equals("Voided") ? new Color[]{new Color(0xA82A2A), Theme.DANGER_LIGHT} : new Color[]{Theme.PRIMARY_DARK, Theme.PRIMARY_LIGHT}));

        payment.addItem("All payments");
        for (PaymentMethod m : PaymentMethod.values()) payment.addItem(m);
        range.setSelectedIndex(1);

        search.setPreferredSize(new java.awt.Dimension(240, 38));
        payment.setPreferredSize(new java.awt.Dimension(150, 38));
        status.setPreferredSize(new java.awt.Dimension(140, 38));
        buildLayout();
    }

    private void buildLayout() {
        javax.swing.JPanel bar = UI.transparent(new BorderLayout(12, 0));
        javax.swing.JPanel left = UI.flow(FlowLayout.LEFT, 10, 0, search, payment);
        if (canVoid) left.add(status);
        bar.add(left, BorderLayout.WEST);
        javax.swing.JPanel actions = UI.flow(FlowLayout.RIGHT, 8, 0, range);
        FlatButton view = FlatButton.primary("View receipt").icon(Icons.Kind.RECEIPT);
        view.addActionListener(e -> viewSelected());
        if (canVoid) {
            FlatButton export = FlatButton.secondary("Export").icon(Icons.Kind.DOWNLOAD);
            export.addActionListener(e -> export());
            FlatButton voidBtn = FlatButton.danger("Void sale");
            voidBtn.addActionListener(e -> voidSelected());
            actions.add(export);
            actions.add(voidBtn);
        }
        actions.add(view);
        bar.add(actions, BorderLayout.EAST);

        Card card = new Card(new BorderLayout(0, 10)).padding(14, 14, 10, 14);
        card.add(UI.tableScroll(table), BorderLayout.CENTER);
        card.add(summary, BorderLayout.SOUTH);
        add(bar, BorderLayout.NORTH);
        add(card, BorderLayout.CENTER);

        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { applyFilter(); }
            public void removeUpdate(DocumentEvent e) { applyFilter(); }
            public void changedUpdate(DocumentEvent e) { applyFilter(); }
        });
        payment.addActionListener(e -> { if (!loading) applyFilter(); });
        status.addActionListener(e -> { if (!loading) applyFilter(); });
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && table.rowAtPoint(e.getPoint()) >= 0) viewSelected();
            }
        });
        table.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) { viewSelected(); e.consume(); }
            }
        });
    }

    @Override public String getTitle() { return canVoid ? "Sales" : "My sales"; }

    @Override public String getSubtitle() {
        return canVoid ? "Every receipt, newest first · double-click to open" : "Receipts you have rung up · double-click to open";
    }

    private LocalDate from() {
        return switch (range.getSelectedIndex()) {
            case 0 -> LocalDate.now();
            case 1 -> LocalDate.now().minusDays(6);
            case 2 -> LocalDate.now().minusDays(29);
            default -> null;
        };
    }

    private List<Sale> filtered() {
        List<Sale> base = canVoid ? SystemContext.sales.between(from(), null)
                : SystemContext.sales.forCashier(user.getUsername(), from(), null);
        String q = search.getText().trim().toLowerCase();
        Object pay = payment.getSelectedItem();
        int st = status.getSelectedIndex();
        List<Sale> out = new ArrayList<>();
        for (Sale s : base) {
            if (!q.isEmpty() && !(s.getReceiptNumber().toLowerCase().contains(q) || s.getCashier().toLowerCase().contains(q)
                    || String.valueOf(s.getId()).equals(q))) continue;
            if (pay instanceof PaymentMethod pm && s.getPayment() != pm) continue;
            if (st == 1 && s.isVoided()) continue;
            if (st == 2 && !s.isVoided()) continue;
            out.add(s);
        }
        return out;
    }

    @Override
    public void refresh() { applyFilter(); }

    private void applyFilter() {
        List<Sale> rows = filtered();
        model.setRows(rows);
        ReportService.Summary s = ReportService.summarize(rows);
        long voided = rows.stream().filter(Sale::isVoided).count();
        summary.setText(rows.size() + " sales  ·  takings " + Formats.money(s.netSales() + s.tax())
                + "  ·  average basket " + Formats.money(s.averageBasket())
                + (voided > 0 ? "  ·  " + voided + " voided (not counted)" : ""));
    }

    private Sale selected() {
        int r = table.getSelectedRow();
        return r < 0 ? null : model.at(table.convertRowIndexToModel(r));
    }

    private void viewSelected() {
        Sale s = selected();
        if (s == null) { Toast.show(this, "Select a sale first", Dialogs.Kind.WARNING); return; }
        ReceiptDialog.show(this, s);
    }

    private void voidSelected() {
        Sale s = selected();
        if (s == null) { Toast.show(this, "Select a sale first", Dialogs.Kind.WARNING); return; }
        if (s.isVoided()) { Toast.show(this, s.getReceiptNumber() + " is already voided", Dialogs.Kind.WARNING); return; }
        String reason = Dialogs.prompt(this, "Void " + s.getReceiptNumber() + "?",
                "The items go back into stock and the sale stops counting in reports. Reason (optional):", "");
        if (reason == null) return;
        try {
            SystemContext.sales.voidSale(s.getId(), user, reason);
            SystemContext.fireChanged();
            applyFilter();
            Toast.success(this, s.getReceiptNumber() + " voided, stock restored");
        } catch (ServiceException ex) {
            Dialogs.error(this, ex.getMessage());
        }
    }

    private void export() {
        Path p = Dialogs.chooseSave(this, "Export sales", "sales-" + Formats.date(LocalDate.now()) + ".csv");
        if (p == null) return;
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Receipt", "Date", "Cashier", "Payment", "Items", "Subtotal", "Discount", "Tax", "Total", "Status"});
        for (Sale s : filtered()) {
            rows.add(new String[]{s.getReceiptNumber(), Formats.dateTime(s.getDateTime()), s.getCashier(), s.getPayment().getLabel(),
                String.valueOf(s.getItemCount()), String.valueOf(s.getSubtotal()), String.valueOf(s.getDiscountAmount()),
                String.valueOf(s.getTaxAmount()), String.valueOf(s.getTotal()), s.isVoided() ? "Voided" : "Completed"});
        }
        try {
            FileManager.exportCsv(p, rows);
            Toast.success(this, "Exported " + (rows.size() - 1) + " sales");
        } catch (ServiceException ex) {
            Dialogs.error(this, ex.getMessage());
        }
    }
}
