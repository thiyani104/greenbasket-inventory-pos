package ui;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import model.Product;
import model.StockMovement;
import ui.kit.*;
import util.Fmt;
import util.SystemContext;

/** Catalogue and stock control: filter, edit, receive / write off / count stock, import and export, ledger. */
public class InventoryPage extends Page {

    private static final String[] STATUS = {"All", "Needs attention", "Low", "Out of stock", "Expiring", "Expired"};
    private static final String[] MOVE_TYPES = {"All types", "SALE", "VOID", "RESTOCK", "WRITE_OFF", "CORRECTION", "OPENING"};

    private final boolean mgr;
    private final Kpi k1, k2, k3, k4;
    private final JLabel subtitle = Ui.muted("");

    private final Segmented view = new Segmented("Products", "Stock ledger");
    private final JPanel cards = new JPanel(new CardLayout());

    // products tab
    private final Field search = new Field("Search by name, category, supplier or barcode").icon("search");
    private final Combo<String> category = new Combo<>("All categories");
    private final Segmented status = new Segmented(true, STATUS);
    private final ListTableModel<Product> model = new ListTableModel<>();
    private Tables.DataTable table;
    private final JLabel selection = Ui.label("Select a product to edit it or adjust its stock", Theme.font(13), Theme.muted);
    private final Btn adjustBtn = new Btn("Adjust stock", "refresh", Btn.Kind.SOFT).compact();
    private final Btn editBtn = new Btn("Edit", "edit", Btn.Kind.SECONDARY).compact();
    private final Btn historyBtn = new Btn("History", "history", Btn.Kind.SECONDARY).compact();
    private final Btn deleteBtn = new Btn("Delete", "trash", Btn.Kind.GHOST).compact();
    private Map<Integer, Double> velocity = new HashMap<>();
    private boolean updatingFilters;

    // ledger tab
    private final Field ledgerSearch = new Field("Search product, user or note").icon("search");
    private final Combo<String> ledgerType = new Combo<>(MOVE_TYPES);
    private final ListTableModel<StockMovement> ledgerModel = StockDialog.movementModel(true);
    private final JLabel ledgerInfo = Ui.muted("");

    public InventoryPage(MainFrame host) {
        super(host);
        mgr = host.isManager();
        k1 = new Kpi("Products", "box", Theme.chart[0]);
        k2 = new Kpi(mgr ? "Stock value (cost)" : "Units in stock", mgr ? "tag" : "box", Theme.chart[1]);
        k3 = new Kpi("Needs restock", "alert", Theme.chart[2]);
        k4 = new Kpi("Expiring or expired", "clock", Theme.chart[4]);

        buildModel();
        table = Tables.table(model);
        table.setEmptyText("No products match your filters");

        Btn add = new Btn("Add product", "plus", Btn.Kind.PRIMARY);
        add.addActionListener(e -> addProduct());
        Btn export = new Btn("Export CSV", "download", Btn.Kind.SECONDARY);
        export.addActionListener(e -> exportCsv());
        Btn imp = new Btn("Import CSV", "upload", Btn.Kind.SECONDARY);
        imp.addActionListener(e -> importCsv());
        JComponent head = Ui.header("Inventory", null, mgr ? actions(imp, export, add) : actions(export));
        // replace the static subtitle by a live one
        JPanel headWrap = Ui.transparent(new BorderLayout());
        JPanel left = Ui.transparent(null);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        JLabel h1 = Ui.h1("Inventory");
        h1.setAlignmentX(0);
        subtitle.setAlignmentX(0);
        left.add(h1);
        left.add(Box.createVerticalStrut(3));
        left.add(subtitle);
        headWrap.add(left, BorderLayout.WEST);
        headWrap.add(mgr ? actions(imp, export, add) : actions(export), BorderLayout.EAST);
        headWrap.setBorder(new EmptyBorder(0, 0, 16, 0));

        JPanel kpis = new JPanel(new GridLayout(1, 4, 16, 0));
        kpis.setOpaque(false);
        kpis.add(k1); kpis.add(k2); kpis.add(k3); kpis.add(k4);
        kpis.setBorder(new EmptyBorder(0, 0, 16, 0));

        cards.setOpaque(false);
        cards.add(buildProductsTab(), "products");
        cards.add(buildLedgerTab(), "ledger");
        view.onChange(i -> {
            ((CardLayout) cards.getLayout()).show(cards, i == 0 ? "products" : "ledger");
            if (i == 1) refreshLedger();
        });
        JPanel viewRow = Ui.transparent(new BorderLayout());
        viewRow.add(view, BorderLayout.WEST);
        viewRow.setBorder(new EmptyBorder(0, 0, 12, 0));

        JPanel body = Ui.transparent(new BorderLayout());
        JPanel top = Ui.transparent(new BorderLayout());
        top.add(kpis, BorderLayout.NORTH);
        top.add(viewRow, BorderLayout.SOUTH);
        body.add(top, BorderLayout.NORTH);
        body.add(cards, BorderLayout.CENTER);
        shell(headWrap, body);
        refresh();
    }

    // ----------------------------------------------------------------- tables

    private void buildModel() {
        model.integer("ID", 50, Product::getProductId);
        model.title("Product", 200, Product::getProductName);
        model.muted("Category", 100, Product::getCategory);
        model.muted("Supplier", 140, Product::getSupplier);
        if (mgr) model.money("Cost", 90, Product::getCostPrice);
        model.money("Price", 90, Product::getPrice);
        if (mgr) model.percent("Margin", 70, Product::marginPercent);
        model.integer("Stock", 60, Product::getQuantity);
        model.columns().get(model.columns().size() - 1).bold = true;
        model.integer("Days left", 76, p -> daysLeft(p));
        model.columns().get(model.columns().size() - 1).fmt = v -> ((Integer) v) >= 9999 ? "-" : v + " d";
        model.columns().get(model.columns().size() - 1).muted = true;
        model.date("Expiry", 100, Product::getExpiryDate);
        model.badge("Status", 110, p -> p.status(SystemContext.settings.getExpiryWarnDays()));
    }

    private int daysLeft(Product p) {
        if (p.getQuantity() <= 0) return 0;
        double v = velocity.getOrDefault(p.getProductId(), 0.0);
        if (v <= 0.001) return 9999;
        return (int) Math.min(9998, Math.floor(p.getQuantity() / v));
    }

    private JComponent buildProductsTab() {
        category.setPreferredSize(new Dimension(190, 40));
        JPanel filters = Ui.transparent(new BorderLayout(12, 0));
        filters.setBorder(new EmptyBorder(0, 0, 12, 0));
        JPanel fl = Ui.transparent(new BorderLayout(12, 0));
        fl.add(search, BorderLayout.CENTER);
        fl.add(category, BorderLayout.EAST);
        filters.add(fl, BorderLayout.CENTER);
        status.setAlignmentY(0.5f);
        JPanel sw = Ui.transparent(new GridBagLayout());
        sw.add(status);
        filters.add(sw, BorderLayout.EAST);

        search.getDocument().addDocumentListener(new SimpleDoc(this::applyFilters));
        category.addActionListener(e -> applyFilters());
        status.onChange(i -> applyFilters());

        JScrollPane sp = Tables.scroll(table);
        Card card = new Card(new BorderLayout(0, 12), 14);
        card.add(sp, BorderLayout.CENTER);

        JPanel foot = Ui.transparent(new BorderLayout(10, 0));
        foot.add(selection, BorderLayout.CENTER);
        List<Component> bs = new ArrayList<>();
        bs.add(historyBtn);
        if (mgr) {
            bs.add(adjustBtn);
            bs.add(editBtn);
            bs.add(deleteBtn);
        }
        JPanel fb = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        fb.setOpaque(false);
        for (Component c : bs) fb.add(c);
        foot.add(fb, BorderLayout.EAST);
        foot.setPreferredSize(new Dimension(100, 34));
        card.add(foot, BorderLayout.SOUTH);

        table.getSelectionModel().addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) onSelect(); });
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && selected() != null) {
                    if (mgr) edit(selected());
                    else StockDialog.history(host, selected());
                }
            }
            @Override public void mousePressed(MouseEvent e) { popup(e); }
            @Override public void mouseReleased(MouseEvent e) { popup(e); }
            private void popup(MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                int r = table.rowAtPoint(e.getPoint());
                if (r < 0) return;
                table.setRowSelectionInterval(r, r);
                Product p = selected();
                JPopupMenu m = new JPopupMenu();
                JMenuItem hist = new JMenuItem("Stock history");
                hist.addActionListener(a -> StockDialog.history(host, p));
                if (mgr) {
                    JMenuItem adj = new JMenuItem("Adjust stock...");
                    adj.addActionListener(a -> adjust(p));
                    JMenuItem ed = new JMenuItem("Edit product...");
                    ed.addActionListener(a -> edit(p));
                    JMenuItem po = new JMenuItem("Add to a purchase order");
                    po.addActionListener(a -> {
                        host.go(MainFrame.Nav.PURCHASING);
                        ((PurchasingPage) host.page(MainFrame.Nav.PURCHASING)).newOrderFor(p);
                    });
                    m.add(adj); m.add(ed); m.add(po); m.addSeparator();
                }
                m.add(hist);
                m.show(table, e.getX(), e.getY());
            }
        });
        adjustBtn.addActionListener(e -> { if (selected() != null) adjust(selected()); });
        editBtn.addActionListener(e -> { if (selected() != null) edit(selected()); });
        historyBtn.addActionListener(e -> { if (selected() != null) StockDialog.history(host, selected()); });
        deleteBtn.addActionListener(e -> { if (selected() != null) delete(selected()); });
        onSelect();

        JPanel p = Ui.transparent(new BorderLayout());
        p.add(filters, BorderLayout.NORTH);
        p.add(card, BorderLayout.CENTER);
        return p;
    }

    private JComponent buildLedgerTab() {
        ledgerType.setPreferredSize(new Dimension(170, 40));
        JPanel filters = Ui.transparent(new BorderLayout(12, 0));
        filters.setBorder(new EmptyBorder(0, 0, 12, 0));
        filters.add(ledgerSearch, BorderLayout.CENTER);
        filters.add(ledgerType, BorderLayout.EAST);
        ledgerSearch.getDocument().addDocumentListener(new SimpleDoc(this::refreshLedger));
        ledgerType.addActionListener(e -> refreshLedger());

        Tables.DataTable t = Tables.table(ledgerModel);
        t.setEmptyText("No stock movements match");
        t.setRowHeight(40);
        Card card = new Card(new BorderLayout(0, 10), 14);
        card.add(Tables.scroll(t), BorderLayout.CENTER);
        ledgerInfo.setBorder(new EmptyBorder(0, 2, 0, 0));
        card.add(ledgerInfo, BorderLayout.SOUTH);
        JPanel p = Ui.transparent(new BorderLayout());
        p.add(filters, BorderLayout.NORTH);
        p.add(card, BorderLayout.CENTER);
        return p;
    }

    // ---------------------------------------------------------------- refresh

    @Override public void refresh() {
        velocity = SystemContext.reports.velocity(30);
        // category filter
        updatingFilters = true;
        Object keep = category.getSelectedItem();
        List<String> cats = new ArrayList<>();
        cats.add("All categories");
        cats.addAll(SystemContext.inventory.categories());
        category.setModel(new DefaultComboBoxModel<>(cats.toArray(new String[0])));
        category.setSelectedItem(keep != null && cats.contains(keep.toString()) ? keep : "All categories");
        updatingFilters = false;

        int warn = SystemContext.settings.getExpiryWarnDays();
        int restock = SystemContext.inventory.needsRestock().size();
        int out = 0;
        for (Product p : SystemContext.inventory.getAllProducts()) if (p.isOutOfStock()) out++;
        int exp = SystemContext.inventory.expiringSoon(warn).size();
        int expired = SystemContext.inventory.expired().size();
        k1.set(String.valueOf(SystemContext.inventory.size()), cats.size() - 1 + " categories", Kpi.Tone.NEUTRAL);
        if (mgr) k2.set(Fmt.money(SystemContext.inventory.totalCostValue()), "Retail " + Fmt.money(SystemContext.inventory.totalRetailValue()), Kpi.Tone.NEUTRAL);
        else k2.set(String.valueOf(SystemContext.inventory.totalUnits()), "across all products", Kpi.Tone.NEUTRAL);
        k3.set(String.valueOf(restock), restock == 0 ? "Everything is stocked" : out + " sold out", restock == 0 ? Kpi.Tone.GOOD : Kpi.Tone.WARN);
        k4.set(String.valueOf(exp + expired), expired > 0 ? expired + " already expired" : "Within " + warn + " days", exp + expired == 0 ? Kpi.Tone.GOOD : Kpi.Tone.WARN);
        subtitle.setText(SystemContext.inventory.size() + " products  -  " + SystemContext.inventory.totalUnits() + " units on the shelves");
        applyFilters();
        if (view.getSelected() == 1) refreshLedger();
    }

    private void applyFilters() {
        if (updatingFilters) return;
        Integer keepId = selected() == null ? null : selected().getProductId();
        int warn = SystemContext.settings.getExpiryWarnDays();
        String cat = category.getSelectedIndex() <= 0 ? null : String.valueOf(category.getSelectedItem());
        int st = status.getSelected();
        List<Product> rows = new ArrayList<>();
        for (Product p : SystemContext.inventory.search(search.getText())) {
            if (cat != null && !p.getCategory().equalsIgnoreCase(cat)) continue;
            boolean ok;
            switch (st) {
                case 1: ok = p.needsRestock() || p.isExpired() || p.isExpiringSoon(warn); break;
                case 2: ok = p.isLowStock(); break;
                case 3: ok = p.isOutOfStock(); break;
                case 4: ok = p.isExpiringSoon(warn); break;
                case 5: ok = p.isExpired(); break;
                default: ok = true;
            }
            if (ok) rows.add(p);
        }
        model.setRows(rows);
        if (keepId != null) {
            for (int i = 0; i < table.getRowCount(); i++) {
                Product p = model.at(table.convertRowIndexToModel(i));
                if (p != null && p.getProductId() == keepId) { table.setRowSelectionInterval(i, i); break; }
            }
        }
        onSelect();
    }

    private void refreshLedger() {
        String q = ledgerSearch.getText().trim().toLowerCase();
        String type = ledgerType.getSelectedIndex() <= 0 ? null : String.valueOf(ledgerType.getSelectedItem());
        List<StockMovement> rows = new ArrayList<>();
        int total = 0;
        for (StockMovement m : SystemContext.inventory.getMovements()) {
            if (type != null && !m.getType().equals(type)) continue;
            if (!q.isEmpty() && !(m.getProductName().toLowerCase().contains(q) || m.getUser().toLowerCase().contains(q)
                    || m.getNote().toLowerCase().contains(q))) continue;
            total++;
            if (rows.size() < 1500) rows.add(m);
        }
        ledgerModel.setRows(rows);
        ledgerInfo.setText(total > rows.size() ? "Showing the latest " + rows.size() + " of " + total + " movements" : total + " movement(s)");
    }

    // ----------------------------------------------------------------- actions

    private Product selected() { return Tables.selected(table); }

    private void onSelect() {
        Product p = selected();
        boolean has = p != null;
        adjustBtn.setEnabled(has);
        editBtn.setEnabled(has);
        historyBtn.setEnabled(has);
        deleteBtn.setEnabled(has);
        if (!has) {
            selection.setText(model.getRowCount() + " product(s) shown  -  select one to work with it");
            selection.setFont(Theme.font(13));
            selection.setForeground(Theme.muted);
        } else {
            selection.setText(p.getProductName() + "  -  " + p.getQuantity() + " in stock"
                    + (mgr ? "  -  margin " + String.format("%.0f%%", p.marginPercent()) : ""));
            selection.setFont(Theme.bold(13));
            selection.setForeground(Theme.text);
        }
    }

    /** Called from the dashboard's "Add product" shortcut. */
    public void addProduct() {
        if (!mgr) return;
        if (ProductDialog.show(host, null)) host.toast("Product added");
    }

    /** Called from the alert bell: show only products that need a decision. */
    public void showAttention() {
        view.setSelected(0);
        ((CardLayout) cards.getLayout()).show(cards, "products");
        search.setText("");
        category.setSelectedIndex(0);
        status.setSelected(1);
        applyFilters();
    }

    private void edit(Product p) {
        if (ProductDialog.show(host, p)) host.toast("Product updated");
    }

    private void adjust(Product p) {
        if (StockDialog.adjust(host, p)) host.toast("Stock updated");
    }

    private void delete(Product p) {
        if (Dialogs.confirm(host, "Delete product",
                "Delete \"" + p.getProductName() + "\" from the catalogue?\n\nPast sales keep their records. Stock history is kept in the ledger.", "Delete", true)) {
            SystemContext.inventory.deleteProduct(p.getProductId());
            host.toast("Product deleted");
        }
    }

    private void exportCsv() {
        Path path = Pick.save(this, "products-" + LocalDate.now().toString().replace("-", "") + ".csv", "CSV file", "csv");
        if (path == null) return;
        try {
            SystemContext.inventory.exportCsv(path.toFile());
            host.toast("Exported " + SystemContext.inventory.size() + " products");
        } catch (RuntimeException ex) {
            Dialogs.error(host, ex.getMessage());
        }
    }

    private void importCsv() {
        File f = Pick.open(this, "CSV file", "csv");
        if (f == null) return;
        if (!Dialogs.confirm(host, "Import products",
                "Products in \"" + f.getName() + "\" will be added, or updated when the ID already exists (a different quantity becomes a stock-count correction).\n\nContinue?",
                "Import", false)) return;
        try {
            String r = SystemContext.inventory.importCsv(f);
            Dialogs.info(host, "Import finished", r);
        } catch (RuntimeException ex) {
            Dialogs.error(host, ex.getMessage());
        }
    }
}
