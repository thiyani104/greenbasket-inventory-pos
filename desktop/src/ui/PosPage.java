package ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import model.PaymentMethod;
import model.Product;
import model.Sale;
import model.User;
import service.Cart;
import service.ServiceException;
import util.Formats;
import util.SystemContext;

/** Point of sale: find products, build a cart, take payment, print a receipt. */
public class PosPage extends Page {

    private static final String ALL = "All categories";

    private final User user = SystemContext.currentUser;
    private final Cart cart = new Cart();

    // catalogue side
    private final FlatTextField search = new FlatTextField(20).placeholder("Search or enter product ID, then press Enter to add").searchIcon();
    private final FlatComboBox<String> category = new FlatComboBox<>();
    private final ProductModel productModel = new ProductModel();
    private final JTable productTable = new JTable(productModel);

    // cart side
    private final CartModel cartModel = new CartModel();
    private final JTable cartTable = new JTable(cartModel);
    private final JLabel itemsLabel = UI.muted("0 items");
    private final JLabel subtotalVal = valueLabel(Font.PLAIN, 13);
    private final JLabel discountVal = valueLabel(Font.PLAIN, 13);
    private final JLabel taxLabel = UI.body("Tax");
    private final JLabel taxVal = valueLabel(Font.PLAIN, 13);
    private final JLabel totalVal = valueLabel(Font.BOLD, 26);
    private final FlatTextField discountField = new FlatTextField(4);
    private final FlatTextField tenderedField = new FlatTextField(8).placeholder("exact");
    private final Segmented payment = new Segmented("Cash", "Card", "Mobile pay");
    private final JPanel quickCash = UI.transparent(new FlowLayout(FlowLayout.LEFT, 6, 0));
    private final JLabel changeLabel = UI.label(" ", Font.BOLD, 13, Theme.PRIMARY_DARK);
    private final JLabel hint = UI.label(" ", Font.PLAIN, 12, Theme.DANGER);
    private final FlatButton charge = FlatButton.primary("Charge");
    private final JPanel cashRow = UI.transparent(new BorderLayout(0, 6));
    private boolean loading;
    private boolean discountOk = true;

    public PosPage() {
        add(buildCatalogue(), BorderLayout.CENTER);
        add(buildCartCard(), BorderLayout.EAST);
        wireKeys();
        recalc();
    }

    @Override public String getTitle() { return "Point of Sale"; }

    @Override public String getSubtitle() { return "F2 search  ·  Enter add  ·  F9 charge  ·  Esc clear search"; }

    private static JLabel valueLabel(int style, float size) {
        JLabel l = UI.label("", style, size, Theme.TEXT);
        l.setHorizontalAlignment(SwingConstants.RIGHT);
        return l;
    }

    // ============================== catalogue side ==============================

    private Card buildCatalogue() {
        Card card = new Card(new BorderLayout(0, 12)).padding(14, 14, 14, 14);
        JPanel top = UI.transparent(new BorderLayout(10, 0));
        top.add(search, BorderLayout.CENTER);
        category.setPreferredSize(new Dimension(170, 38));
        top.add(category, BorderLayout.EAST);
        card.add(top, BorderLayout.NORTH);

        UI.styleTable(productTable);
        productTable.setRowHeight(44);
        var cm = productTable.getColumnModel();
        cm.getColumn(0).setPreferredWidth(260);
        cm.getColumn(1).setPreferredWidth(110);
        cm.getColumn(2).setCellRenderer(UI.moneyCell());
        cm.getColumn(2).setPreferredWidth(100);
        cm.getColumn(3).setCellRenderer(new UI.Cell(SwingConstants.RIGHT) {
            @Override protected String format(Object v) {
                Product p = (Product) v;
                return p.isExpired() ? "Expired" : String.valueOf(p.getQuantity());
            }
            @Override protected Color colorFor(Object v, int row, JTable t) {
                Product p = (Product) v;
                return p.isExpired() || p.isOutOfStock() ? Theme.DANGER : p.isLowStock() ? Theme.WARN : Theme.TEXT;
            }
        });
        cm.getColumn(3).setPreferredWidth(80);
        cm.getColumn(0).setCellRenderer(new UI.Cell(SwingConstants.LEFT) {
            @Override protected Color colorFor(Object v, int row, JTable t) {
                Product p = productModel.rows.get(row);
                return p.isExpired() || p.isOutOfStock() ? new Color(0x9AA8A1) : Theme.TEXT;
            }
        });
        card.add(UI.tableScroll(productTable), BorderLayout.CENTER);

        DocumentListener dl = new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { loadProducts(); }
            public void removeUpdate(DocumentEvent e) { loadProducts(); }
            public void changedUpdate(DocumentEvent e) { loadProducts(); }
        };
        search.getDocument().addDocumentListener(dl);
        category.addActionListener(e -> { if (!loading) loadProducts(); });
        search.addActionListener(e -> addFromSearch());
        search.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DOWN && productModel.getRowCount() > 0) {
                    productTable.requestFocusInWindow();
                    productTable.setRowSelectionInterval(0, 0);
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    search.setText("");
                }
            }
        });
        productTable.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && productTable.rowAtPoint(e.getPoint()) >= 0) addSelectedProduct();
            }
        });
        productTable.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) { addSelectedProduct(); e.consume(); }
                if (e.getKeyCode() == KeyEvent.VK_UP && productTable.getSelectedRow() <= 0) search.requestFocusInWindow();
            }
        });
        return card;
    }

    private void loadProducts() {
        String cat = (String) category.getSelectedItem();
        List<Product> rows = new ArrayList<>(SystemContext.inventory.search(search.getText(),
                cat == null || cat.equals(ALL) ? null : cat, null));
        rows.sort(Comparator.comparing(p -> p.getProductName().toLowerCase()));
        productModel.rows = rows;
        productModel.fireTableDataChanged();
    }

    private void addFromSearch() {
        String q = search.getText().trim();
        if (q.isEmpty()) return;
        Product target = null;
        if (q.matches("\\d+")) target = SystemContext.inventory.findById(Integer.parseInt(q));
        if (target == null && !productModel.rows.isEmpty()) target = productModel.rows.get(0);
        if (target == null) { Toast.show(this, "No product matches \"" + q + "\"", Dialogs.Kind.WARNING); return; }
        if (addToCart(target)) search.setText("");
        search.requestFocusInWindow();
    }

    private void addSelectedProduct() {
        int r = productTable.getSelectedRow();
        if (r >= 0) addToCart(productModel.rows.get(productTable.convertRowIndexToModel(r)));
    }

    private boolean addToCart(Product p) {
        try {
            cart.add(p, 1);
            refreshCart();
            return true;
        } catch (ServiceException ex) {
            Toast.show(this, ex.getMessage(), Dialogs.Kind.ERROR);
            return false;
        }
    }

    // ============================== cart side ==============================

    private Card buildCartCard() {
        Card card = new Card(new BorderLayout(0, 10)).padding(16, 16, 16, 16);
        card.setPreferredSize(new Dimension(430, 100));

        JPanel head = UI.transparent(new BorderLayout());
        JPanel hl = UI.transparent(new FlowLayout(FlowLayout.LEFT, 8, 0));
        hl.add(UI.heading("Current sale"));
        hl.add(itemsLabel);
        head.add(hl, BorderLayout.WEST);
        FlatButton clear = FlatButton.ghost("Clear").compact();
        clear.addActionListener(e -> clearCart());
        head.add(clear, BorderLayout.EAST);
        card.add(head, BorderLayout.NORTH);

        UI.styleTable(cartTable);
        cartTable.setRowHeight(46);
        cartTable.setShowHorizontalLines(false);
        var cm = cartTable.getColumnModel();
        cm.getColumn(0).setPreferredWidth(150);
        cm.getColumn(1).setPreferredWidth(70);
        cm.getColumn(1).setCellRenderer(UI.moneyCell());
        cm.getColumn(2).setPreferredWidth(104);
        cm.getColumn(2).setMinWidth(104);
        cm.getColumn(2).setMaxWidth(104);
        cm.getColumn(2).setCellRenderer(new QtyRenderer());
        cm.getColumn(3).setPreferredWidth(78);
        cm.getColumn(3).setCellRenderer(UI.moneyCell());
        cm.getColumn(4).setPreferredWidth(34);
        cm.getColumn(4).setMinWidth(34);
        cm.getColumn(4).setMaxWidth(34);
        cm.getColumn(4).setCellRenderer(removeRenderer());
        cartTable.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { cartClick(e); }
        });
        JPanel emptyHint = UI.transparent(new BorderLayout());
        card.add(UI.tableScroll(cartTable), BorderLayout.CENTER);

        card.add(buildSummary(), BorderLayout.SOUTH);
        return card;
    }

    private JPanel buildSummary() {
        JPanel p = UI.transparent(null);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        JPanel grid = UI.transparent(new GridBagLayout());
        addSummaryRow(grid, 0, UI.body("Subtotal"), subtotalVal);
        JPanel discLabel = UI.transparent(new FlowLayout(FlowLayout.LEFT, 6, 0));
        discLabel.add(UI.body("Discount"));
        discountField.setPreferredSize(new Dimension(58, 32));
        discountField.setHorizontalAlignment(SwingConstants.CENTER);
        discountField.setText("0");
        discLabel.add(discountField);
        discLabel.add(UI.muted("%"));
        addSummaryRow(grid, 1, discLabel, discountVal);
        addSummaryRow(grid, 2, taxLabel, taxVal);
        GridBagConstraints sep = UI.gbc(0, 3);
        sep.gridwidth = 2;
        sep.fill = GridBagConstraints.HORIZONTAL;
        sep.weightx = 1;
        sep.insets = new Insets(8, 0, 6, 0);
        JPanel line = new JPanel();
        line.setBackground(Theme.BORDER);
        line.setPreferredSize(new Dimension(1, 1));
        grid.add(line, sep);
        addSummaryRow(grid, 4, UI.label("Total", Font.BOLD, 16, Theme.TEXT), totalVal);
        p.add(grid);

        p.add(Box.createVerticalStrut(12));
        JPanel payRow = UI.transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
        payRow.add(payment);
        p.add(payRow);
        p.add(Box.createVerticalStrut(10));

        JPanel cashTop = UI.transparent(new BorderLayout(10, 0));
        cashTop.add(UI.body("Cash received"), BorderLayout.WEST);
        cashTop.add(tenderedField, BorderLayout.CENTER);
        cashRow.add(cashTop, BorderLayout.NORTH);
        JPanel cashBottom = UI.transparent(new BorderLayout(0, 0));
        cashBottom.add(quickCash, BorderLayout.WEST);
        cashBottom.add(changeLabel, BorderLayout.EAST);
        cashRow.add(cashBottom, BorderLayout.CENTER);
        p.add(cashRow);
        p.add(Box.createVerticalStrut(4));
        hint.setAlignmentX(0);
        p.add(hint);
        p.add(Box.createVerticalStrut(6));

        charge.setFont(Theme.font(Font.BOLD, 15));
        charge.setPreferredSize(new Dimension(100, 50));
        JPanel chargeWrap = UI.transparent(new BorderLayout());
        chargeWrap.add(charge, BorderLayout.CENTER);
        p.add(chargeWrap);

        DocumentListener dl = new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { recalc(); }
            public void removeUpdate(DocumentEvent e) { recalc(); }
            public void changedUpdate(DocumentEvent e) { recalc(); }
        };
        discountField.getDocument().addDocumentListener(dl);
        tenderedField.getDocument().addDocumentListener(dl);
        payment.onChange(this::recalc);
        charge.addActionListener(e -> checkout());
        return p;
    }

    private void addSummaryRow(JPanel grid, int y, Component left, JLabel right) {
        GridBagConstraints l = UI.gbc(0, y);
        l.weightx = 1;
        l.fill = GridBagConstraints.HORIZONTAL;
        l.insets = new Insets(2, 0, 2, 0);
        grid.add(left, l);
        GridBagConstraints r = UI.gbc(1, y);
        r.anchor = GridBagConstraints.EAST;
        r.insets = new Insets(2, 8, 2, 0);
        grid.add(right, r);
    }

    private PaymentMethod method() {
        return switch (payment.getSelectedIndex()) {
            case 1 -> PaymentMethod.CARD;
            case 2 -> PaymentMethod.MOBILE;
            default -> PaymentMethod.CASH;
        };
    }

    private void cartClick(MouseEvent e) {
        int row = cartTable.rowAtPoint(e.getPoint());
        int col = cartTable.columnAtPoint(e.getPoint());
        if (row < 0 || col < 0) return;
        var rect = cartTable.getCellRect(row, col, false);
        int rel = e.getX() - rect.x;
        try {
            if (col == 2) {
                int q = cart.getLines().get(row).getQuantity();
                if (rel < 38) cart.setQuantity(row, q - 1);
                else if (rel > rect.width - 38) cart.setQuantity(row, q + 1);
                else return;
            } else if (col == 4) {
                cart.remove(row);
            } else {
                return;
            }
        } catch (ServiceException ex) {
            Toast.show(this, ex.getMessage(), Dialogs.Kind.ERROR);
        }
        refreshCart();
    }

    private void clearCart() {
        if (cart.isEmpty()) return;
        cart.clear();
        discountField.setText("0");
        tenderedField.setText("");
        refreshCart();
    }

    private void refreshCart() {
        cartModel.fireTableDataChanged();
        recalc();
    }

    private double parseDiscount() {
        String t = discountField.getText().trim();
        discountOk = true;
        if (t.isEmpty()) return 0;
        try {
            double d = Double.parseDouble(t);
            if (d < 0 || d > user.getMaxDiscountPercent()) {
                discountOk = false;
                hint.setText("Your account can give up to " + (int) user.getMaxDiscountPercent() + "% discount.");
                return 0;
            }
            return d;
        } catch (NumberFormatException e) {
            discountOk = false;
            hint.setText("Discount must be a number.");
            return 0;
        }
    }

    private void recalc() {
        if (loading) return;
        hint.setText(" ");
        double disc = parseDiscount();
        discountField.setError(!discountOk);
        double taxRate = SystemContext.settings.getTaxRate();
        Sale prev = cart.preview(disc, taxRate);
        itemsLabel.setText(cart.getItemCount() + (cart.getItemCount() == 1 ? " item" : " items"));
        subtotalVal.setText(Formats.money(prev.getSubtotal()));
        discountVal.setText(prev.getDiscountAmount() > 0 ? "-" + Formats.money(prev.getDiscountAmount()) : Formats.money(0));
        taxLabel.setText("Tax (" + (taxRate == Math.rint(taxRate) ? String.valueOf((long) taxRate) : String.valueOf(taxRate)) + "%)");
        taxVal.setText(Formats.money(prev.getTaxAmount()));
        totalVal.setText(Formats.money(prev.getTotal()));

        boolean cash = method() == PaymentMethod.CASH;
        cashRow.setVisible(cash);
        boolean ok = !cart.isEmpty() && discountOk;
        changeLabel.setText(" ");
        tenderedField.setError(false);
        if (cash && ok) {
            String t = tenderedField.getText().trim();
            if (!t.isEmpty()) {
                try {
                    double paid = Double.parseDouble(t.replace(",", ""));
                    if (paid + 0.005 < prev.getTotal()) {
                        changeLabel.setForeground(Theme.DANGER);
                        changeLabel.setText("Short by " + Formats.money(prev.getTotal() - paid));
                        tenderedField.setError(true);
                        ok = false;
                    } else {
                        changeLabel.setForeground(Theme.PRIMARY_DARK);
                        changeLabel.setText("Change " + Formats.money(paid - prev.getTotal()));
                    }
                } catch (NumberFormatException e) {
                    tenderedField.setError(true);
                    hint.setText("Cash received must be a number.");
                    ok = false;
                }
            }
            rebuildQuickCash(prev.getTotal());
        }
        charge.setText(cart.isEmpty() ? "Charge" : "Charge " + Formats.money(prev.getTotal()) + "   (F9)");
        charge.setEnabled(ok);
    }

    private void rebuildQuickCash(double total) {
        quickCash.removeAll();
        if (total > 0) {
            Set<Long> amounts = new LinkedHashSet<>();
            for (long step : new long[]{100, 500, 1000, 5000}) {
                long v = (long) (Math.ceil(total / step) * step);
                if (v > total + 0.004) amounts.add(v);
                if (amounts.size() >= 3) break;
            }
            FlatButton exact = FlatButton.secondary("Exact").compact();
            exact.addActionListener(e -> tenderedField.setText(""));
            quickCash.add(exact);
            for (long a : amounts) {
                FlatButton b = FlatButton.secondary(String.valueOf(a)).compact();
                b.addActionListener(e -> tenderedField.setText(String.valueOf(a)));
                quickCash.add(b);
            }
        }
        quickCash.revalidate();
        quickCash.repaint();
    }

    private void checkout() {
        if (!charge.isEnabled()) return;
        double disc = parseDiscount();
        PaymentMethod pm = method();
        Sale prev = cart.preview(disc, SystemContext.settings.getTaxRate());
        double tendered = prev.getTotal();
        String t = tenderedField.getText().trim();
        if (pm == PaymentMethod.CASH && !t.isEmpty()) tendered = Double.parseDouble(t.replace(",", ""));
        try {
            Sale sale = SystemContext.sales.checkout(cart, user, pm, disc, tendered);
            cart.clear();
            loading = true;
            discountField.setText("0");
            tenderedField.setText("");
            loading = false;
            SystemContext.fireChanged();
            refreshCart();
            loadProducts();
            ReceiptDialog.show(this, sale);
            search.requestFocusInWindow();
        } catch (ServiceException ex) {
            Dialogs.error(this, ex.getMessage());
            loadProducts();
        }
    }

    // ============================== keys, lifecycle ==============================

    private void wireKeys() {
        var im = getInputMap(WHEN_IN_FOCUSED_WINDOW);
        var am = getActionMap();
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F9, 0), "charge");
        am.put("charge", new AbstractAction() { public void actionPerformed(java.awt.event.ActionEvent e) { checkout(); } });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), "focusSearch");
        am.put("focusSearch", new AbstractAction() {
            public void actionPerformed(java.awt.event.ActionEvent e) { search.requestFocusInWindow(); search.selectAll(); }
        });
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
        loadProducts();
        // drop cart lines whose product vanished
        for (int i = cart.getLines().size() - 1; i >= 0; i--) {
            if (SystemContext.inventory.findById(cart.getLines().get(i).getProduct().getProductId()) == null) cart.remove(i);
        }
        cartModel.fireTableDataChanged();
        recalc();
    }

    @Override
    public void onShow() {
        refresh();
        SwingUtilitiesLater.focus(search);
    }

    private static final class SwingUtilitiesLater {
        static void focus(JComponent c) { javax.swing.SwingUtilities.invokeLater(c::requestFocusInWindow); }
    }

    // ============================== table models & renderers ==============================

    private static final class ProductModel extends AbstractTableModel {
        private List<Product> rows = new ArrayList<>();
        private final String[] cols = {"Product", "Category", "Price", "Stock"};

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return 4; }
        @Override public String getColumnName(int c) { return cols[c]; }
        @Override public Class<?> getColumnClass(int c) { return c == 2 ? Double.class : Object.class; }
        @Override public Object getValueAt(int r, int c) {
            Product p = rows.get(r);
            return switch (c) {
                case 0 -> p.getProductName();
                case 1 -> p.getCategory();
                case 2 -> p.getPrice();
                default -> p;
            };
        }
    }

    private final class CartModel extends AbstractTableModel {
        private final String[] cols = {"Item", "Price", "Qty", "Total", ""};

        @Override public int getRowCount() { return cart.getLines().size(); }
        @Override public int getColumnCount() { return 5; }
        @Override public String getColumnName(int c) { return cols[c]; }
        @Override public Class<?> getColumnClass(int c) { return c == 1 || c == 3 ? Double.class : Object.class; }
        @Override public Object getValueAt(int r, int c) {
            Cart.Line l = cart.getLines().get(r);
            return switch (c) {
                case 0 -> l.getProduct().getProductName();
                case 1 -> l.getProduct().getPrice();
                case 2 -> l.getQuantity();
                case 3 -> l.getLineTotal();
                default -> "";
            };
        }
    }

    private static final class QtyRenderer extends JComponent implements TableCellRenderer {
        private int qty;
        private boolean sel;

        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
            qty = v instanceof Integer i ? i : 0;
            sel = s;
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            g2.setColor(sel ? Theme.PRIMARY_LIGHT : Color.WHITE);
            g2.fillRect(0, 0, w, h);
            g2.setColor(Theme.BORDER_SOFT);
            g2.fillRect(0, h - 1, w, 1);
            int bs = 26, y = (h - bs) / 2;
            g2.setColor(new Color(0xEEF3F0));
            g2.fillRoundRect(6, y, bs, bs, 10, 10);
            g2.fillRoundRect(w - 6 - bs, y, bs, bs, 10, 10);
            g2.setColor(Theme.TEXT);
            g2.setStroke(new java.awt.BasicStroke(1.8f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
            g2.drawLine(6 + bs / 2 - 5, h / 2, 6 + bs / 2 + 5, h / 2);
            int px = w - 6 - bs / 2;
            g2.drawLine(px - 5, h / 2, px + 5, h / 2);
            g2.drawLine(px, h / 2 - 5, px, h / 2 + 5);
            g2.setFont(Theme.font(Font.BOLD, 14));
            String s = String.valueOf(qty);
            java.awt.FontMetrics fm = g2.getFontMetrics();
            g2.drawString(s, (w - fm.stringWidth(s)) / 2, (h - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }

    private static TableCellRenderer removeRenderer() {
        return (t, v, sel, f, r, c) -> {
            JComponent comp = new JComponent() {
                @Override protected void paintComponent(Graphics g) {
                    g.setColor(sel ? Theme.PRIMARY_LIGHT : Color.WHITE);
                    g.fillRect(0, 0, getWidth(), getHeight());
                    g.setColor(Theme.BORDER_SOFT);
                    g.fillRect(0, getHeight() - 1, getWidth(), 1);
                    Icons.of(Icons.Kind.CLOSE, 14, new Color(0x9AA8A1)).paintIcon(this, g, (getWidth() - 14) / 2, (getHeight() - 14) / 2);
                }
            };
            return comp;
        };
    }
}
