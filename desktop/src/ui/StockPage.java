package ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.DefaultCellEditor;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import model.MovementType;
import model.Product;
import model.Sale;
import model.SaleItem;
import model.StockMovement;
import model.StockStatus;
import service.Inventory;
import service.ReportService;
import service.ServiceException;
import util.Csv;
import util.FileManager;
import util.Formats;
import util.SystemContext;

/** Everything about keeping the shelves right: alerts, what to reorder, the stock ledger and slow movers. */
public class StockPage extends Page {

    private static final String[] TABS = {"Alerts", "Reorder plan", "Stock ledger", "Slow movers"};

    private final Segmented tabs = new Segmented(TABS).onChange(this::showTab);
    private final CardLayout cards = new CardLayout();
    private final JPanel stage = UI.transparent(cards);
    private final JPanel actions = UI.flow(FlowLayout.RIGHT, 8, 0);

    // alerts
    private final RowModel<Product> alertModel = new RowModel<>(
            new String[]{"Product", "Category", "Supplier", "Stock", "Reorder at", "Expiry", "Status"},
            new Class<?>[]{String.class, String.class, String.class, Integer.class, Integer.class, java.time.LocalDate.class, StockStatus.class},
            (p, c) -> switch (c) {
                case 0 -> p.getProductName();
                case 1 -> p.getCategory();
                case 2 -> p.getSupplier().isEmpty() ? "—" : p.getSupplier();
                case 3 -> p.getQuantity();
                case 4 -> p.getReorderLevel();
                case 5 -> p.getExpiryDate();
                default -> p.getStatus(SystemContext.settings.getExpiryWarnDays());
            });
    private final JTable alertTable = new JTable(alertModel);
    private final JLabel alertSummary = UI.muted(" ");

    // reorder plan
    private final Map<Integer, Integer> qtyOverride = new HashMap<>();
    private final RowModel<Inventory.ReorderLine> planModel = new RowModel<Inventory.ReorderLine>(
            new String[]{"Supplier", "Product", "On hand", "Reorder at", "Order qty", "Unit cost", "Line cost"},
            new Class<?>[]{String.class, String.class, Integer.class, Integer.class, Integer.class, Double.class, Double.class},
            (l, c) -> switch (c) {
                case 0 -> l.product().getSupplier().isEmpty() ? "— none —" : l.product().getSupplier();
                case 1 -> l.product().getProductName();
                case 2 -> l.product().getQuantity();
                case 3 -> l.product().getReorderLevel();
                case 4 -> effectiveQty(l);
                case 5 -> l.product().getCost();
                default -> effectiveQty(l) * l.product().getCost();
            }).editable(Set.of(4), (l, v) -> {
                try {
                    int q = Integer.parseInt(String.valueOf(v[1]).trim());
                    qtyOverride.put(l.product().getProductId(), Math.max(0, q));
                } catch (NumberFormatException ignored) {
                    // keep the old value
                }
                updatePlanSummary();
            });
    private final JTable planTable = new JTable(planModel);
    private final JLabel planSummary = UI.muted(" ");

    // ledger
    private final FlatTextField ledgerSearch = new FlatTextField(18).placeholder("Search product, note or user").searchIcon();
    private final FlatComboBox<Object> ledgerType = new FlatComboBox<>();
    private final Segmented ledgerRange = new Segmented("Today", "7 days", "30 days", "All").onChange(this::loadLedger);
    private final RowModel<StockMovement> ledgerModel = new RowModel<>(
            new String[]{"When", "Product", "Type", "Change", "Balance", "By", "Note"},
            new Class<?>[]{String.class, String.class, MovementType.class, Integer.class, Integer.class, String.class, String.class},
            (m, c) -> switch (c) {
                case 0 -> Formats.dateTime(m.getDateTime());
                case 1 -> m.getProductName();
                case 2 -> m.getType();
                case 3 -> m.getChange();
                case 4 -> m.getBalanceAfter();
                case 5 -> m.getUser();
                default -> m.getNote();
            });
    private final JTable ledgerTable = new JTable(ledgerModel);
    private final JLabel ledgerSummary = UI.muted(" ");
    private boolean loading;

    // slow movers
    private final Segmented slowRange = new Segmented("14 days", "30 days", "60 days").onChange(this::loadSlow);
    private record Slow(Product product, LocalDate lastSold) {}
    private final RowModel<Slow> slowModel = new RowModel<>(
            new String[]{"Product", "Category", "In stock", "Cash tied up", "Last sold", "Idle"},
            new Class<?>[]{String.class, String.class, Integer.class, Double.class, LocalDate.class, String.class},
            (s, c) -> switch (c) {
                case 0 -> s.product().getProductName();
                case 1 -> s.product().getCategory();
                case 2 -> s.product().getQuantity();
                case 3 -> s.product().stockValueAtCost();
                case 4 -> s.lastSold();
                default -> s.lastSold() == null ? "never sold"
                        : java.time.temporal.ChronoUnit.DAYS.between(s.lastSold(), LocalDate.now()) + " days";
            });
    private final JTable slowTable = new JTable(slowModel);
    private final JLabel slowSummary = UI.muted(" ");

    public StockPage() {
        stage.add(alertsPanel(), "0");
        stage.add(planPanel(), "1");
        stage.add(ledgerPanel(), "2");
        stage.add(slowPanel(), "3");

        JPanel bar = UI.transparent(new BorderLayout(12, 0));
        bar.add(UI.flow(FlowLayout.LEFT, 0, 0, tabs), BorderLayout.WEST);
        bar.add(actions, BorderLayout.EAST);
        add(bar, BorderLayout.NORTH);
        add(stage, BorderLayout.CENTER);

        ledgerType.addItem("All types");
        for (MovementType t : MovementType.values()) ledgerType.addItem(t);
        ledgerType.addActionListener(e -> { if (!loading) loadLedger(); });
        ledgerSearch.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { loadLedger(); }
            public void removeUpdate(DocumentEvent e) { loadLedger(); }
            public void changedUpdate(DocumentEvent e) { loadLedger(); }
        });
        showTab();
    }

    @Override public String getTitle() { return "Stock & Orders"; }

    @Override public String getSubtitle() { return "Alerts, reorder planning, the stock ledger and slow-moving stock"; }

    // ============================================================ tabs

    private void showTab() {
        int i = tabs.getSelectedIndex();
        cards.show(stage, String.valueOf(i));
        actions.removeAll();
        switch (i) {
            case 0 -> {
                FlatButton recv = FlatButton.primary("Receive stock").icon(Icons.Kind.PLUS);
                recv.addActionListener(e -> stockAction(alertTable, alertModel, 0));
                FlatButton off = FlatButton.secondary("Write off");
                off.addActionListener(e -> stockAction(alertTable, alertModel, 1));
                FlatButton count = FlatButton.secondary("Stock count");
                count.addActionListener(e -> stockAction(alertTable, alertModel, 2));
                actions.add(count);
                actions.add(off);
                actions.add(recv);
            }
            case 1 -> {
                FlatButton recv = FlatButton.secondary("Mark delivery received");
                recv.addActionListener(e -> receiveLine());
                FlatButton csv = FlatButton.secondary("Export CSV").icon(Icons.Kind.DOWNLOAD);
                csv.addActionListener(e -> exportPlan());
                FlatButton po = FlatButton.primary("Purchase orders").icon(Icons.Kind.RECEIPT);
                po.addActionListener(e -> showOrders());
                actions.add(recv);
                actions.add(csv);
                actions.add(po);
            }
            case 2 -> {
                FlatButton csv = FlatButton.secondary("Export CSV").icon(Icons.Kind.DOWNLOAD);
                csv.addActionListener(e -> exportLedger());
                actions.add(csv);
            }
            default -> { }
        }
        actions.revalidate();
        actions.repaint();
        refresh();
    }

    @Override
    public void refresh() {
        switch (tabs.getSelectedIndex()) {
            case 0 -> loadAlerts();
            case 1 -> loadPlan();
            case 2 -> loadLedger();
            default -> loadSlow();
        }
    }

    private Card tableCard(JTable table, JLabel summary, java.awt.Component topBar) {
        UI.styleTable(table);
        Card card = new Card(new BorderLayout(0, 10)).padding(14, 14, 10, 14);
        if (topBar != null) card.add(topBar, BorderLayout.NORTH);
        card.add(UI.tableScroll(table), BorderLayout.CENTER);
        card.add(summary, BorderLayout.SOUTH);
        return card;
    }

    private static UI.Cell right() { return new UI.Cell(SwingConstants.RIGHT); }

    // ============================================================ alerts

    private JPanel alertsPanel() {
        Card card = tableCard(alertTable, alertSummary, null);
        UI.widths(alertTable, 220, 110, 150, 70, 80, 100, 130);
        alertTable.getColumnModel().getColumn(5).setCellRenderer(UI.dateCell());
        alertTable.getColumnModel().getColumn(6).setCellRenderer(UI.statusRenderer());
        alertTable.getColumnModel().getColumn(3).setCellRenderer(new UI.Cell(SwingConstants.RIGHT) {
            @Override protected Color colorFor(Object v, int row, JTable t) {
                return v instanceof Integer q && q <= 0 ? Theme.DANGER : Theme.TEXT;
            }
        });
        alertTable.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && alertTable.rowAtPoint(e.getPoint()) >= 0) stockAction(alertTable, alertModel, 0);
            }
        });
        JPanel p = UI.transparent(new BorderLayout());
        p.add(card, BorderLayout.CENTER);
        return p;
    }

    private void loadAlerts() {
        List<Product> rows = new ArrayList<>();
        for (Product p : SystemContext.inventory.getExpired()) rows.add(p);
        for (Product p : SystemContext.inventory.checkLowStockProducts()) if (!rows.contains(p)) rows.add(p);
        for (Product p : SystemContext.inventory.getExpiringSoon()) if (!rows.contains(p)) rows.add(p);
        int warn = SystemContext.settings.getExpiryWarnDays();
        rows.sort(Comparator.comparingInt((Product p) -> p.getStatus(warn).ordinal()).thenComparing(Product::getProductName));
        alertModel.setRows(rows);
        long low = rows.stream().filter(p -> p.getStatus(warn) == StockStatus.LOW || p.getStatus(warn) == StockStatus.OUT).count();
        long exp = rows.stream().filter(p -> p.getStatus(warn) == StockStatus.EXPIRING).count();
        long gone = rows.stream().filter(p -> p.getStatus(warn) == StockStatus.EXPIRED).count();
        alertSummary.setText(rows.isEmpty() ? "All clear: nothing is low, expiring or expired."
                : rows.size() + " items need attention  ·  " + low + " to reorder  ·  " + exp + " expiring soon  ·  " + gone
                + " expired (write these off)");
    }

    private void stockAction(JTable table, RowModel<Product> model, int mode) {
        int r = table.getSelectedRow();
        if (r < 0) { Toast.show(this, "Select a product first", Dialogs.Kind.WARNING); return; }
        Product p = model.at(table.convertRowIndexToModel(r));
        if (StockAdjustDialog.open(this, p, mode)) {
            SystemContext.fireChanged();
            refresh();
            Toast.success(this, "Stock updated");
        }
    }

    // ============================================================ reorder plan

    private JPanel planPanel() {
        Card card = tableCard(planTable, planSummary, null);
        UI.widths(planTable, 150, 220, 80, 90, 100, 100, 110);
        planTable.getColumnModel().getColumn(5).setCellRenderer(UI.moneyCell());
        planTable.getColumnModel().getColumn(6).setCellRenderer(UI.moneyCell());
        planTable.getColumnModel().getColumn(4).setCellRenderer(new UI.Cell(SwingConstants.RIGHT) {
            @Override protected Color colorFor(Object v, int row, JTable t) { return Theme.PRIMARY_DARK; }
        });
        FlatTextField editor = new FlatTextField(6);
        editor.setHorizontalAlignment(SwingConstants.RIGHT);
        planTable.getColumnModel().getColumn(4).setCellEditor(new DefaultCellEditor(editor));
        JPanel p = UI.transparent(new BorderLayout());
        p.add(card, BorderLayout.CENTER);
        return p;
    }

    private int effectiveQty(Inventory.ReorderLine l) {
        return qtyOverride.getOrDefault(l.product().getProductId(), l.suggestedQty());
    }

    private void loadPlan() {
        List<Inventory.ReorderLine> lines = SystemContext.inventory.suggestReorder();
        qtyOverride.keySet().removeIf(id -> lines.stream().noneMatch(l -> l.product().getProductId() == id));
        planModel.setRows(lines);
        updatePlanSummary();
    }

    private List<Inventory.ReorderLine> effectiveLines() {
        List<Inventory.ReorderLine> out = new ArrayList<>();
        for (Inventory.ReorderLine l : planModel.rows()) {
            int q = effectiveQty(l);
            out.add(new Inventory.ReorderLine(l.product(), q, Formats.round2(q * l.product().getCost())));
        }
        return out;
    }

    private void updatePlanSummary() {
        double total = 0;
        int lines = 0;
        for (Inventory.ReorderLine l : effectiveLines()) {
            if (l.suggestedQty() > 0) lines++;
            total += l.estimatedCost();
        }
        planSummary.setText(planModel.rows().isEmpty()
                ? "Nothing to reorder right now."
                : lines + " lines  ·  estimated cost " + Formats.money(total)
                + "  ·  suggested quantity tops each product up to twice its reorder level; click an Order qty to change it");
    }

    private void showOrders() {
        if (planModel.rows().isEmpty()) { Toast.show(this, "Nothing to order right now", Dialogs.Kind.INFO); return; }
        String text = ReportService.purchaseOrders(effectiveLines(), SystemContext.settings.getStoreName(), LocalDate.now());
        TextReportDialog.show(this, "Purchase orders", text, "purchase-orders-" + Formats.date(LocalDate.now()) + ".txt", 640);
        SystemContext.log.add(SystemContext.actor(), "Purchase orders", "Generated for " + planModel.rows().size() + " products");
    }

    private void receiveLine() {
        int r = planTable.getSelectedRow();
        if (r < 0) { Toast.show(this, "Select a line first", Dialogs.Kind.WARNING); return; }
        Inventory.ReorderLine l = planModel.at(planTable.convertRowIndexToModel(r));
        int q = effectiveQty(l);
        if (q <= 0) { Toast.show(this, "The order quantity is 0", Dialogs.Kind.WARNING); return; }
        if (!Dialogs.confirm(this, "Receive delivery?", "Add " + q + " × " + l.product().getProductName()
                + " to stock as a delivery. You can fix the quantity afterwards with a stock count.", "Receive", false)) return;
        try {
            SystemContext.inventory.receiveStock(l.product().getProductId(), q, null, "Delivery from reorder plan", SystemContext.actor());
            SystemContext.log.add(SystemContext.actor(), "Stock change", l.product().getProductName() + " · +" + q + " received");
            qtyOverride.remove(l.product().getProductId());
            SystemContext.fireChanged();
            refresh();
            Toast.success(this, q + " × " + l.product().getProductName() + " received");
        } catch (ServiceException ex) {
            Dialogs.error(this, ex.getMessage());
        }
    }

    private void exportPlan() {
        if (planModel.rows().isEmpty()) { Toast.show(this, "Nothing to export", Dialogs.Kind.INFO); return; }
        Path p = Dialogs.chooseSave(this, "Export reorder plan", "reorder-plan-" + Formats.date(LocalDate.now()) + ".csv");
        if (p == null) return;
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Supplier", "Product", "On hand", "Reorder level", "Order quantity", "Unit cost", "Line cost"});
        for (Inventory.ReorderLine l : effectiveLines()) {
            rows.add(new String[]{l.product().getSupplier(), l.product().getProductName(), String.valueOf(l.product().getQuantity()),
                String.valueOf(l.product().getReorderLevel()), String.valueOf(l.suggestedQty()),
                String.valueOf(l.product().getCost()), String.valueOf(l.estimatedCost())});
        }
        try {
            FileManager.exportCsv(p, rows);
            Toast.success(this, "Reorder plan exported");
        } catch (ServiceException ex) {
            Dialogs.error(this, ex.getMessage());
        }
    }

    // ============================================================ ledger

    private JPanel ledgerPanel() {
        JPanel top = UI.transparent(new BorderLayout(10, 0));
        ledgerSearch.setPreferredSize(new java.awt.Dimension(260, 38));
        ledgerType.setPreferredSize(new java.awt.Dimension(160, 38));
        top.add(UI.flow(FlowLayout.LEFT, 10, 0, ledgerSearch, ledgerType), BorderLayout.WEST);
        top.add(UI.flow(FlowLayout.RIGHT, 0, 0, ledgerRange), BorderLayout.EAST);
        Card card = tableCard(ledgerTable, ledgerSummary, top);
        ledgerRange.setSelectedIndex(1);
        UI.widths(ledgerTable, 130, 220, 120, 70, 80, 90, 260);
        ledgerTable.getColumnModel().getColumn(2).setCellRenderer(UI.pillRenderer(v -> typeColors((MovementType) v)));
        ledgerTable.getColumnModel().getColumn(3).setCellRenderer(new UI.Cell(SwingConstants.RIGHT) {
            @Override protected String format(Object v) { return v instanceof Integer i ? (i > 0 ? "+" + i : String.valueOf(i)) : ""; }
            @Override protected Color colorFor(Object v, int row, JTable t) {
                return v instanceof Integer i && i < 0 ? Theme.DANGER : Theme.PRIMARY_DARK;
            }
        });
        ledgerTable.getColumnModel().getColumn(0).setCellRenderer(new UI.Cell(SwingConstants.LEFT, true));
        JPanel p = UI.transparent(new BorderLayout());
        p.add(card, BorderLayout.CENTER);
        return p;
    }

    private static Color[] typeColors(MovementType t) {
        return switch (t) {
            case RECEIVE -> new Color[]{Theme.PRIMARY_DARK, Theme.PRIMARY_LIGHT};
            case SALE -> new Color[]{new Color(0x1F5FA8), Theme.INFO_LIGHT};
            case VOID_RETURN -> new Color[]{new Color(0xA4460F), Theme.ORANGE_LIGHT};
            case WASTAGE -> new Color[]{new Color(0xA82A2A), Theme.DANGER_LIGHT};
            case CORRECTION -> new Color[]{new Color(0x9A6200), Theme.WARN_LIGHT};
            default -> new Color[]{Theme.MUTED, new Color(0xEBF0EC)};
        };
    }

    private List<StockMovement> filteredLedger() {
        String q = ledgerSearch.getText().trim().toLowerCase();
        Object type = ledgerType.getSelectedItem();
        LocalDate from = switch (ledgerRange.getSelectedIndex()) {
            case 0 -> LocalDate.now();
            case 1 -> LocalDate.now().minusDays(6);
            case 2 -> LocalDate.now().minusDays(29);
            default -> null;
        };
        List<StockMovement> out = new ArrayList<>();
        for (StockMovement m : SystemContext.inventory.getMovements()) {
            if (from != null && m.getDateTime().toLocalDate().isBefore(from)) continue;
            if (type instanceof MovementType mt && m.getType() != mt) continue;
            if (!q.isEmpty() && !(m.getProductName().toLowerCase().contains(q) || m.getNote().toLowerCase().contains(q)
                    || m.getUser().toLowerCase().contains(q))) continue;
            out.add(m);
        }
        return out;
    }

    private void loadLedger() {
        loading = true;
        List<StockMovement> rows = filteredLedger();
        loading = false;
        ledgerModel.setRows(rows.size() > 2000 ? rows.subList(0, 2000) : rows);
        int in = 0, out = 0;
        for (StockMovement m : rows) { if (m.getChange() > 0) in += m.getChange(); else out += -m.getChange(); }
        ledgerSummary.setText(rows.size() + " movements  ·  " + in + " units in  ·  " + out + " units out"
                + (rows.size() > 2000 ? "  ·  showing the newest 2,000 (export for all)" : ""));
    }

    private void exportLedger() {
        Path p = Dialogs.chooseSave(this, "Export stock ledger", "stock-ledger-" + Formats.date(LocalDate.now()) + ".csv");
        if (p == null) return;
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"When", "Product", "Type", "Change", "Balance after", "By", "Note"});
        for (StockMovement m : filteredLedger()) {
            rows.add(new String[]{Formats.dateTime(m.getDateTime()), m.getProductName(), m.getType().getLabel(),
                String.valueOf(m.getChange()), String.valueOf(m.getBalanceAfter()), m.getUser(), m.getNote()});
        }
        try {
            FileManager.exportCsv(p, rows);
            Toast.success(this, "Exported " + (rows.size() - 1) + " movements");
        } catch (ServiceException ex) {
            Dialogs.error(this, ex.getMessage());
        }
    }

    // ============================================================ slow movers

    private JPanel slowPanel() {
        JPanel top = UI.transparent(new BorderLayout(10, 0));
        top.add(UI.flow(FlowLayout.LEFT, 0, 0, UI.muted("Products with stock on the shelf that have not sold in the last")), BorderLayout.WEST);
        top.add(UI.flow(FlowLayout.RIGHT, 0, 0, slowRange), BorderLayout.EAST);
        Card card = tableCard(slowTable, slowSummary, top);
        UI.widths(slowTable, 240, 120, 80, 120, 110, 110);
        slowTable.getColumnModel().getColumn(3).setCellRenderer(UI.moneyCell());
        slowTable.getColumnModel().getColumn(4).setCellRenderer(UI.dateCell());
        JPanel p = UI.transparent(new BorderLayout());
        p.add(card, BorderLayout.CENTER);
        return p;
    }

    private void loadSlow() {
        int days = new int[]{14, 30, 60}[slowRange.getSelectedIndex()];
        LocalDate since = LocalDate.now().minusDays(days - 1L);
        List<Sale> sales = SystemContext.sales.getAll();
        Map<Integer, LocalDate> last = new HashMap<>();
        for (Sale s : sales) {
            if (s.isVoided()) continue;
            for (SaleItem i : s.getItems()) last.merge(i.getProductId(), s.getDateTime().toLocalDate(), (a, b) -> a.isAfter(b) ? a : b);
        }
        List<Slow> rows = new ArrayList<>();
        double tied = 0;
        for (Product p : ReportService.deadStock(SystemContext.inventory.getAllProducts(), sales, since)) {
            rows.add(new Slow(p, last.get(p.getProductId())));
            tied += p.stockValueAtCost();
        }
        slowModel.setRows(rows);
        slowSummary.setText(rows.isEmpty() ? "Everything on the shelf has sold recently."
                : rows.size() + " slow movers  ·  " + Formats.money(tied) + " of stock at cost is sitting idle. Consider a discount, a promotion or smaller orders.");
    }
}
