package ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import model.Product;
import model.StockStatus;
import model.User;
import service.Inventory;
import service.ServiceException;
import util.FileManager;
import util.Formats;
import util.SystemContext;

/** Product catalogue: search, filter, sort, add / edit / delete, stock actions, CSV import / export. */
public class ProductsPage extends Page {

    private static final String ALL = "All categories";

    private final User user = SystemContext.currentUser;
    private final boolean canEdit = user.canEditProducts();
    private final boolean canCost = user.canViewCosts();
    private final Model model = new Model();
    private final JTable table = new JTable(model);
    private final FlatTextField search = new FlatTextField(20).placeholder("Search name, category, supplier or ID").searchIcon();
    private final FlatComboBox<String> category = new FlatComboBox<>();
    private final FlatComboBox<Inventory.StatusFilter> status = new FlatComboBox<>(Inventory.StatusFilter.values());
    private final JLabel summary = UI.muted(" ");
    private boolean loading;

    public ProductsPage() {
        UI.styleTable(table);
        TableRowSorter<Model> sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);
        setupColumns();

        JPanel bar = UI.transparent(new BorderLayout(12, 0));
        search.setPreferredSize(new java.awt.Dimension(250, 38));
        category.setPreferredSize(new java.awt.Dimension(160, 38));
        status.setPreferredSize(new java.awt.Dimension(160, 38));
        bar.add(UI.flow(FlowLayout.LEFT, 10, 0, search, category, status), BorderLayout.WEST);
        JPanel actions = UI.flow(FlowLayout.RIGHT, 8, 0);
        FlatButton export = FlatButton.secondary("Export").icon(Icons.Kind.DOWNLOAD);
        export.addActionListener(e -> exportCsv());
        if (canCost) actions.add(export);
        if (canEdit) {
            FlatButton imp = FlatButton.secondary("Import").icon(Icons.Kind.UPLOAD);
            imp.addActionListener(e -> importCsv());
            FlatButton add = FlatButton.primary("Add product").icon(Icons.Kind.PLUS);
            add.addActionListener(e -> addProduct());
            actions.add(imp);
            actions.add(add);
        }
        bar.add(actions, BorderLayout.EAST);

        Card card = new Card(new BorderLayout(0, 10)).padding(14, 14, 10, 14);
        card.add(UI.tableScroll(table), BorderLayout.CENTER);
        card.add(summary, BorderLayout.SOUTH);

        add(bar, BorderLayout.NORTH);
        add(card, BorderLayout.CENTER);

        DocumentListener dl = new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { applyFilter(); }
            public void removeUpdate(DocumentEvent e) { applyFilter(); }
            public void changedUpdate(DocumentEvent e) { applyFilter(); }
        };
        search.getDocument().addDocumentListener(dl);
        category.addActionListener(e -> { if (!loading) applyFilter(); });
        status.addActionListener(e -> { if (!loading) applyFilter(); });

        if (canEdit) {
            table.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2 && table.rowAtPoint(e.getPoint()) >= 0) editSelected();
                }
                @Override public void mousePressed(MouseEvent e) { popup(e); }
                @Override public void mouseReleased(MouseEvent e) { popup(e); }
            });
            table.addKeyListener(new KeyAdapter() {
                @Override public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_DELETE) deleteSelected();
                    if (e.getKeyCode() == KeyEvent.VK_ENTER) { editSelected(); e.consume(); }
                }
            });
        }
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(javax.swing.KeyStroke.getKeyStroke("control F"), "focusSearch");
        getActionMap().put("focusSearch", new javax.swing.AbstractAction() {
            public void actionPerformed(java.awt.event.ActionEvent e) { search.requestFocusInWindow(); search.selectAll(); }
        });
    }

    @Override public String getTitle() { return "Products"; }

    @Override public String getSubtitle() { return canEdit ? "Manage your catalogue · double-click a row to edit" : "Browse the catalogue and check stock"; }

    private void setupColumns() {
        var cm = table.getColumnModel();
        for (int i = 0; i < model.getColumnCount(); i++) {
            String n = model.getColumnName(i);
            var col = cm.getColumn(i);
            switch (n) {
                case "ID" -> { col.setCellRenderer(new UI.Cell(SwingConstants.LEFT, true) {
                    @Override protected Color colorFor(Object v, int row, JTable t) { return Theme.MUTED; } });
                    col.setPreferredWidth(50); }
                case "Product" -> col.setPreferredWidth(210);
                case "Category" -> col.setPreferredWidth(110);
                case "Supplier" -> col.setPreferredWidth(150);
                case "Price", "Cost" -> { col.setCellRenderer(UI.moneyCell()); col.setPreferredWidth(95); }
                case "Stock" -> { col.setCellRenderer(new UI.Cell(SwingConstants.RIGHT) {
                    @Override protected Color colorFor(Object v, int row, JTable t) {
                        Product p = model.rows.get(t.convertRowIndexToModel(row));
                        return p.isOutOfStock() ? Theme.DANGER : p.isLowStock() ? Theme.WARN : Theme.TEXT; } });
                    col.setPreferredWidth(70); }
                case "Reorder" -> col.setPreferredWidth(70);
                case "Expiry" -> { col.setCellRenderer(new UI.Cell(SwingConstants.LEFT) {
                    @Override protected String format(Object v) { return v instanceof LocalDate d ? Formats.date(d) : "—"; }
                    @Override protected Color colorFor(Object v, int row, JTable t) {
                        Product p = model.rows.get(t.convertRowIndexToModel(row));
                        if (p.getExpiryDate() == null) return Theme.MUTED;
                        return p.isExpired() ? Theme.DANGER : p.isExpiringWithin(SystemContext.settings.getExpiryWarnDays()) ? Theme.ORANGE : Theme.TEXT; } });
                    col.setPreferredWidth(100); }
                case "Status" -> { col.setCellRenderer(UI.statusRenderer()); col.setPreferredWidth(120); }
                default -> { }
            }
        }
    }

    @Override
    public void refresh() {
        loading = true;
        Object sel = category.getSelectedItem();
        category.removeAllItems();
        category.addItem(ALL);
        for (String c : SystemContext.inventory.getCategoryChoices()) category.addItem(c);
        category.setSelectedItem(sel == null ? ALL : sel);
        if (category.getSelectedItem() == null) category.setSelectedItem(ALL);
        loading = false;
        applyFilter();
    }

    private void applyFilter() {
        String cat = (String) category.getSelectedItem();
        List<Product> rows = SystemContext.inventory.search(search.getText(),
                cat == null || cat.equals(ALL) ? null : cat, (Inventory.StatusFilter) status.getSelectedItem());
        model.setRows(rows);
        StringBuilder sb = new StringBuilder(rows.size() + " of " + SystemContext.inventory.size() + " products");
        if (canCost) {
            sb.append("  ·  stock worth ").append(Formats.money(SystemContext.inventory.stockValueAtCost()))
              .append(" at cost, ").append(Formats.money(SystemContext.inventory.stockValueAtRetail())).append(" at retail");
        }
        summary.setText(sb.toString());
    }

    private Product selected() {
        int r = table.getSelectedRow();
        return r < 0 ? null : model.rows.get(table.convertRowIndexToModel(r));
    }

    // ============================== actions ==============================

    private void changed() {
        SystemContext.fireChanged();
        refresh();
    }

    private void addProduct() {
        if (ProductDialog.showAdd(this)) {
            changed();
            Toast.success(this, "Product added");
        }
    }

    private void editSelected() {
        Product p = selected();
        if (p == null) { Toast.show(this, "Select a product first", Dialogs.Kind.WARNING); return; }
        if (ProductDialog.showEdit(this, p)) {
            changed();
            Toast.success(this, "Product updated");
        }
    }

    private void stockSelected(int mode) {
        Product p = selected();
        if (p == null) return;
        if (StockAdjustDialog.open(this, p, mode)) {
            changed();
            Toast.success(this, "Stock updated");
        }
    }

    private void deleteSelected() {
        Product p = selected();
        if (p == null) { Toast.show(this, "Select a product first", Dialogs.Kind.WARNING); return; }
        if (!Dialogs.confirm(this, "Delete product?",
                "\"" + p.getProductName() + "\" will be removed from the catalogue. Past sales keep their records.",
                "Delete", true)) return;
        SystemContext.inventory.deleteProduct(p.getProductId());
        SystemContext.log.add(SystemContext.actor(), "Delete product", p.getProductName());
        changed();
        Toast.success(this, "Product deleted");
    }

    private void popup(MouseEvent e) {
        if (!e.isPopupTrigger()) return;
        int r = table.rowAtPoint(e.getPoint());
        if (r < 0) return;
        table.setRowSelectionInterval(r, r);
        JPopupMenu m = new JPopupMenu();
        addItem(m, "Edit product…", this::editSelected);
        addItem(m, "Receive stock…", () -> stockSelected(0));
        addItem(m, "Write off stock…", () -> stockSelected(1));
        addItem(m, "Stock count…", () -> stockSelected(2));
        m.addSeparator();
        addItem(m, "Delete product", this::deleteSelected);
        m.show(table, e.getX(), e.getY());
    }

    private static void addItem(JPopupMenu m, String text, Runnable r) {
        JMenuItem it = new JMenuItem(text);
        it.setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 24));
        it.addActionListener(ev -> r.run());
        m.add(it);
    }

    private void exportCsv() {
        Path p = Dialogs.chooseSave(this, "Export products", "products.csv");
        if (p == null) return;
        try {
            FileManager.exportCsv(p, SystemContext.inventory.exportRows());
            Toast.success(this, "Exported " + SystemContext.inventory.size() + " products");
        } catch (ServiceException ex) {
            Dialogs.error(this, ex.getMessage());
        }
    }

    private void importCsv() {
        Path p = Dialogs.chooseOpen(this, "Import products from CSV");
        if (p == null) return;
        try {
            Inventory.ImportResult r = SystemContext.inventory.importCsv(p, SystemContext.actor());
            SystemContext.log.add(SystemContext.actor(), "Import products", r.added() + " added, " + r.updated() + " updated");
            changed();
            StringBuilder sb = new StringBuilder(r.added() + " added, " + r.updated() + " updated.");
            if (!r.errors().isEmpty()) {
                sb.append("\n\n").append(r.errors().size()).append(" row(s) skipped:");
                for (int i = 0; i < Math.min(5, r.errors().size()); i++) sb.append("\n• ").append(r.errors().get(i));
                if (r.errors().size() > 5) sb.append("\n…and ").append(r.errors().size() - 5).append(" more");
            }
            Dialogs.message(this, r.errors().isEmpty() ? Dialogs.Kind.SUCCESS : Dialogs.Kind.WARNING, "Import finished", sb.toString());
        } catch (ServiceException ex) {
            Dialogs.error(this, ex.getMessage());
        }
    }

    // ============================== table model ==============================

    private final class Model extends AbstractTableModel {
        private List<Product> rows = new ArrayList<>();
        private final String[] cols = canCost
                ? new String[]{"ID", "Product", "Category", "Supplier", "Price", "Cost", "Stock", "Reorder", "Expiry", "Status"}
                : new String[]{"ID", "Product", "Category", "Supplier", "Price", "Stock", "Reorder", "Expiry", "Status"};

        void setRows(List<Product> r) {
            rows = new ArrayList<>(r);
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int c) { return cols[c]; }

        @Override public Class<?> getColumnClass(int c) {
            return switch (cols[c]) {
                case "ID", "Stock", "Reorder" -> Integer.class;
                case "Price", "Cost" -> Double.class;
                case "Expiry" -> LocalDate.class;
                case "Status" -> StockStatus.class;
                default -> String.class;
            };
        }

        @Override public Object getValueAt(int r, int c) {
            Product p = rows.get(r);
            return switch (cols[c]) {
                case "ID" -> p.getProductId();
                case "Product" -> p.getProductName();
                case "Category" -> p.getCategory();
                case "Supplier" -> p.getSupplier().isEmpty() ? "—" : p.getSupplier();
                case "Price" -> p.getPrice();
                case "Cost" -> p.getCost();
                case "Stock" -> p.getQuantity();
                case "Reorder" -> p.getReorderLevel();
                case "Expiry" -> p.getExpiryDate();
                case "Status" -> p.getStatus(SystemContext.settings.getExpiryWarnDays());
                default -> "";
            };
        }
    }
}
