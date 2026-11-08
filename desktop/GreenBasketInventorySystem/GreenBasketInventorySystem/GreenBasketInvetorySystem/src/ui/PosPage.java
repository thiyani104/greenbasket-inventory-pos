package ui;

import java.awt.*;
import java.awt.event.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import model.Product;
import model.Sale;
import model.SaleItem;
import service.Cart;
import service.ReportService;
import service.SalesService;
import ui.kit.*;
import util.Fmt;
import util.SystemContext;

/**
 * Point of sale: tap or scan products into the basket, apply a discount, take payment and print a receipt.
 * Shortcuts: F4 search, F8 hold sale, F9 charge, Esc clear search.
 */
public class PosPage extends Page {

    private static final int ASSISTANT_MAX_DISCOUNT = 10;
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    private static class Held {
        final Cart cart;
        final String customer;
        final LocalDateTime time = LocalDateTime.now();
        Held(Cart cart, String customer) { this.cart = cart; this.customer = customer; }
    }

    // ---- catalogue side
    private final Field search = new Field("Search products or scan a barcode, then press Enter").icon("search");
    private final JPanel categoryHolder = Ui.transparent(new BorderLayout());
    private Segmented categories;
    private List<String> categoryNames = new ArrayList<>();
    private final TileGrid grid = new TileGrid();
    private final JLabel resultInfo = Ui.muted("");

    // ---- basket side
    private Cart cart;
    private final List<Held> held = new ArrayList<>();
    private final JPanel lines = new JPanel();
    private final JLabel emptyHint = new JLabel("<html><div style='text-align:center'>The basket is empty.<br>Tap a product or scan a barcode to start.</div></html>", SwingConstants.CENTER);
    private final Combo<String> customer = new Combo<>();
    private final JLabel tierInfo = Ui.label(" ", Theme.bold(12), Theme.warning);
    private final Btn applyLoyalty = new Btn("Apply", Btn.Kind.SOFT).compact();
    private final Field discount = new Field();
    private final JLabel subtotalVal = Ui.label("", Theme.font(13), Theme.text);
    private final JLabel discountVal = Ui.label("", Theme.font(13), Theme.success);
    private final JLabel taxVal = Ui.label("", Theme.font(13), Theme.text);
    private final JLabel taxLbl = Ui.label("Tax", Theme.font(13), Theme.muted);
    private final JLabel totalVal = Ui.label("", Theme.bold(24), Theme.text);
    private final Segmented payment = new Segmented(true, SalesService.PAYMENT_METHODS);
    private final Field cashField = new Field("0.00");
    private final JPanel cashPanel = Ui.transparent(null);
    private final JPanel quickCash = Ui.transparent(new FlowLayout(FlowLayout.LEFT, 6, 0));
    private final JLabel changeVal = Ui.label("", Theme.bold(14), Theme.success);
    private final Btn charge = new Btn("Charge", "check", Btn.Kind.PRIMARY);
    private final Btn holdBtn = new Btn("Hold", "pause", Btn.Kind.SECONDARY);
    private final Btn heldBtn = new Btn("Held", "history", Btn.Kind.GHOST);
    private final Btn clearBtn = new Btn("Clear", "trash", Btn.Kind.GHOST);
    private final JLabel title = Ui.h2("Current sale");

    private Map<String, ReportService.CustomerStat> customerStats = new HashMap<>();
    private boolean updating;
    private boolean checkingOut;

    public PosPage(MainFrame host) {
        super(host);
        cart = new Cart(SystemContext.settings.getTaxRate());

        Btn newSale = new Btn("Clear search", Btn.Kind.GHOST);
        newSale.addActionListener(e -> { search.setText(""); search.requestFocusInWindow(); });
        JComponent head = Ui.header("Point of Sale", "Tap a product or scan a barcode to add it to the sale", null);

        JPanel left = Ui.transparent(new BorderLayout(0, 12));
        JPanel tools = Ui.transparent(new BorderLayout(0, 10));
        tools.add(search, BorderLayout.NORTH);
        categoryHolder.setOpaque(false);
        tools.add(categoryHolder, BorderLayout.CENTER);
        left.add(tools, BorderLayout.NORTH);
        JScrollPane sp = Tables.slimScroll(grid);
        sp.getViewport().setOpaque(false);
        sp.setOpaque(false);
        sp.getVerticalScrollBar().setUnitIncrement(28);
        left.add(sp, BorderLayout.CENTER);
        resultInfo.setBorder(new EmptyBorder(6, 2, 0, 0));
        left.add(resultInfo, BorderLayout.SOUTH);

        JComponent right = buildBasket();
        right.setPreferredSize(new Dimension(420, 100));

        JPanel body = Ui.transparent(new BorderLayout(20, 0));
        body.add(left, BorderLayout.CENTER);
        body.add(right, BorderLayout.EAST);
        shell(head, body);

        wire();
        refresh();
    }

    // ------------------------------------------------------------------ build

    private JComponent buildBasket() {
        Card card = new Card(new BorderLayout(0, 0), 0);

        // header: title + held + clear, customer
        JPanel top = Ui.transparent(null);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setBorder(new EmptyBorder(16, 18, 12, 14));
        JPanel titleRow = Ui.transparent(new BorderLayout());
        titleRow.add(title, BorderLayout.WEST);
        titleRow.add(group(2, heldBtn, clearBtn), BorderLayout.EAST);
        titleRow.setAlignmentX(0);
        top.add(titleRow);
        top.add(Box.createVerticalStrut(10));

        customer.setEditable(true);
        customer.setAlignmentX(0);
        JLabel cl = Ui.label("Customer (optional)", Theme.bold(11.5f), Theme.muted);
        cl.setAlignmentX(0);
        cl.setBorder(new EmptyBorder(0, 0, 4, 0));
        top.add(cl);
        top.add(customer);
        JPanel tierRow = Ui.transparent(new BorderLayout(8, 0));
        tierRow.setAlignmentX(0);
        tierRow.setBorder(new EmptyBorder(6, 0, 0, 0));
        tierRow.add(tierInfo, BorderLayout.CENTER);
        tierRow.add(applyLoyalty, BorderLayout.EAST);
        applyLoyalty.setVisible(false);
        tierRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        top.add(tierRow);
        card.add(top, BorderLayout.NORTH);

        // lines
        lines.setLayout(new BoxLayout(lines, BoxLayout.Y_AXIS));
        lines.setOpaque(false);
        JPanel linesWrap = new JPanel(new BorderLayout());
        linesWrap.setOpaque(false);
        linesWrap.add(lines, BorderLayout.NORTH);
        JScrollPane lsp = Tables.slimScroll(linesWrap);
        lsp.getViewport().setOpaque(false);
        JPanel mid = new JPanel(new BorderLayout());
        mid.setOpaque(false);
        mid.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, Theme.border));
        emptyHint.setFont(Theme.font(13));
        emptyHint.setForeground(Theme.faint);
        JPanel stack = new JPanel(new CardLayout());
        stack.setOpaque(false);
        stack.add(lsp, "lines");
        stack.add(emptyHint, "empty");
        stack.setName("stack");
        mid.add(stack, BorderLayout.CENTER);
        card.add(mid, BorderLayout.CENTER);
        linesStack = stack;

        // totals + payment
        JPanel bottom = Ui.transparent(null);
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        bottom.setBorder(new EmptyBorder(14, 18, 16, 18));

        discount.setText("0");
        discount.setPreferredSize(new Dimension(70, 36));
        discount.setMaximumSize(new Dimension(70, 36));
        Btn d5 = new Btn("5%", Btn.Kind.SECONDARY).compact();
        Btn d10 = new Btn("10%", Btn.Kind.SECONDARY).compact();
        d5.addActionListener(e -> discount.setText("5"));
        d10.addActionListener(e -> discount.setText("10"));
        JPanel discRow = Ui.transparent(new BorderLayout(8, 0));
        discRow.setAlignmentX(0);
        discRow.add(Ui.label("Discount %", Theme.font(13), Theme.muted), BorderLayout.WEST);
        discRow.add(group(6, d5, d10, discount), BorderLayout.EAST);
        discRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        bottom.add(discRow);
        bottom.add(Box.createVerticalStrut(10));

        bottom.add(totalRow(Ui.label("Subtotal", Theme.font(13), Theme.muted), subtotalVal));
        bottom.add(Box.createVerticalStrut(4));
        bottom.add(totalRow(Ui.label("Discount", Theme.font(13), Theme.muted), discountVal));
        bottom.add(Box.createVerticalStrut(4));
        bottom.add(totalRow(taxLbl, taxVal));
        bottom.add(Box.createVerticalStrut(8));
        JPanel tot = Ui.transparent(new BorderLayout());
        tot.setAlignmentX(0);
        tot.add(Ui.label("Total", Theme.bold(15), Theme.text), BorderLayout.WEST);
        tot.add(totalVal, BorderLayout.EAST);
        tot.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        bottom.add(tot);
        bottom.add(Box.createVerticalStrut(12));

        payment.setAlignmentX(0);
        bottom.add(payment);
        bottom.add(Box.createVerticalStrut(10));

        cashPanel.setLayout(new BoxLayout(cashPanel, BoxLayout.Y_AXIS));
        cashPanel.setAlignmentX(0);
        JPanel cashRow = Ui.transparent(new BorderLayout(10, 0));
        cashRow.add(Ui.label("Cash received", Theme.font(13), Theme.muted), BorderLayout.WEST);
        cashField.setPreferredSize(new Dimension(150, 38));
        cashField.setHorizontalAlignment(JTextField.RIGHT);
        cashRow.add(cashField, BorderLayout.EAST);
        cashRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        cashPanel.add(cashRow);
        cashPanel.add(Box.createVerticalStrut(8));
        quickCash.setAlignmentX(0);
        quickCash.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        cashPanel.add(quickCash);
        cashPanel.add(Box.createVerticalStrut(6));
        JPanel chg = Ui.transparent(new BorderLayout());
        chg.add(Ui.label("Change", Theme.font(13), Theme.muted), BorderLayout.WEST);
        chg.add(changeVal, BorderLayout.EAST);
        chg.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        cashPanel.add(chg);
        bottom.add(cashPanel);
        bottom.add(Box.createVerticalStrut(12));

        JPanel btns = Ui.transparent(new GridBagLayout());
        btns.setAlignmentX(0);
        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.BOTH;
        gc.weighty = 1;
        gc.gridx = 0;
        gc.weightx = 0;
        gc.insets = new Insets(0, 0, 0, 10);
        btns.add(holdBtn, gc);
        gc.gridx = 1;
        gc.weightx = 1;
        gc.insets = new Insets(0, 0, 0, 0);
        btns.add(charge, gc);
        charge.setPreferredSize(new Dimension(100, 48));
        charge.setFont(Theme.bold(15));
        btns.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        bottom.add(btns);
        card.add(bottom, BorderLayout.SOUTH);
        return card;
    }

    private JPanel linesStack;

    private static JComponent totalRow(JLabel l, JLabel v) {
        JPanel p = Ui.transparent(new BorderLayout());
        p.setAlignmentX(0);
        p.add(l, BorderLayout.WEST);
        p.add(v, BorderLayout.EAST);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        return p;
    }

    // ----------------------------------------------------------------- wiring

    private void wire() {
        search.getDocument().addDocumentListener(new SimpleDoc(this::rebuildTiles));
        search.addActionListener(e -> onSearchEnter());
        search.registerKeyboardAction(e -> { search.setText(""); }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_FOCUSED);

        customer.addActionListener(e -> onCustomerChanged());
        ((JTextField) customer.getEditor().getEditorComponent()).getDocument().addDocumentListener(new SimpleDoc(this::onCustomerChanged));
        applyLoyalty.addActionListener(e -> {
            ReportService.CustomerStat c = customerStats.get(customer.editorText().toLowerCase());
            if (c != null) {
                discount.setText(trim(ReportService.tierDiscount(c.tier())));
                host.toastInfo(c.tier() + " loyalty discount applied");
            }
        });

        discount.getDocument().addDocumentListener(new SimpleDoc(this::onDiscountChanged));
        payment.onChange(i -> { updateTotals(); if (i == 0) cashField.requestFocusInWindow(); });
        cashField.getDocument().addDocumentListener(new SimpleDoc(this::updateChange));
        cashField.addActionListener(e -> checkout());

        charge.addActionListener(e -> checkout());
        holdBtn.addActionListener(e -> holdSale());
        heldBtn.addActionListener(e -> showHeldMenu());
        clearBtn.addActionListener(e -> {
            if (cart.isEmpty()) return;
            if (Dialogs.confirm(host, "Clear the sale", "Remove everything from the current basket?", "Clear", true)) resetSale();
        });

        registerKey(KeyEvent.VK_F4, () -> search.requestFocusInWindow());
        registerKey(KeyEvent.VK_F8, this::holdSale);
        registerKey(KeyEvent.VK_F9, this::checkout);
    }

    private void registerKey(int key, Runnable r) {
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key, 0), "pos" + key);
        getActionMap().put("pos" + key, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { r.run(); }
        });
    }

    @Override public void onShow() {
        refresh();
        SwingUtilities.invokeLater(() -> search.requestFocusInWindow());
    }

    // ---------------------------------------------------------------- refresh

    @Override public void refresh() {
        // customer suggestions
        List<ReportService.CustomerStat> stats = SystemContext.reports.customers(null, null);
        customerStats = new HashMap<>();
        List<String> names = new ArrayList<>();
        for (ReportService.CustomerStat c : stats) {
            customerStats.put(c.name.toLowerCase(), c);
            names.add(c.name);
        }
        updating = true;
        String keep = customer.editorText();
        DefaultComboBoxModel<String> m = new DefaultComboBoxModel<>();
        m.addElement("");
        m.addElement("Walk-in");
        for (String n : names) m.addElement(n);
        customer.setModel(m);
        customer.setSelectedItem(keep);
        updating = false;

        // categories
        List<String> cats = SystemContext.inventory.categories();
        if (!cats.equals(categoryNames) || categories == null) {
            String selected = categories == null ? "All" : categories.selectedLabel();
            categoryNames = cats;
            List<String> opts = new ArrayList<>();
            opts.add("All");
            opts.addAll(cats);
            categories = new Segmented(true, opts.toArray(new String[0]));
            int idx = opts.indexOf(selected);
            categories.setSelected(Math.max(idx, 0));
            categories.onChange(i -> rebuildTiles());
            categoryHolder.removeAll();
            JScrollPane cs = Tables.slimScroll(categories);
            cs.getViewport().setOpaque(false);
            cs.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
            cs.setPreferredSize(new Dimension(100, 44));
            cs.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
            categoryHolder.add(cs, BorderLayout.CENTER);
            categoryHolder.revalidate();
        }

        if (!checkingOut) validateCartAgainstStock();
        rebuildTiles();
        renderCart();
        onCustomerChanged();
    }

    private void validateCartAgainstStock() {
        boolean changed = false;
        for (SaleItem l : new ArrayList<>(cart.getLines())) {
            Product p = SystemContext.inventory.findById(l.getProductId());
            if (p == null || p.isExpired() || p.getQuantity() <= 0) {
                cart.remove(l.getProductId());
                changed = true;
            } else if (l.getQuantity() > p.getQuantity()) {
                cart.setQuantity(p, p.getQuantity());
                changed = true;
            }
        }
        if (changed) host.toastInfo("Basket adjusted to match current stock");
    }

    private void rebuildTiles() {
        String q = search.getText().trim();
        String cat = categories == null || categories.getSelected() == 0 ? null : categories.selectedLabel();
        List<Product> list = new ArrayList<>();
        for (Product p : SystemContext.inventory.search(q)) if (cat == null || p.getCategory().equalsIgnoreCase(cat)) list.add(p);
        list.sort(Comparator.comparing((Product p) -> !isSellable(p)).thenComparing(Product::getProductName, String.CASE_INSENSITIVE_ORDER));
        grid.removeAll();
        for (Product p : list) {
            Tile t = new Tile(p);
            grid.add(t);
        }
        grid.revalidate();
        grid.repaint();
        long sellable = list.stream().filter(PosPage::isSellable).count();
        resultInfo.setText(list.size() + " product(s)" + (list.size() != sellable ? " - " + (list.size() - sellable) + " unavailable" : ""));
    }

    private static boolean isSellable(Product p) { return p.getQuantity() > 0 && !p.isExpired(); }

    // ------------------------------------------------------------------ search

    private void onSearchEnter() {
        String q = search.getText().trim();
        if (q.isEmpty()) return;
        Product byCode = SystemContext.inventory.findByBarcode(q);
        if (byCode != null) {
            addToCart(byCode, 1);
            search.setText("");
            return;
        }
        List<Product> hits = SystemContext.inventory.search(q);
        hits.removeIf(p -> !isSellable(p));
        if (hits.size() == 1) {
            addToCart(hits.get(0), 1);
            search.setText("");
        } else if (hits.isEmpty()) {
            host.toastError("No available product matches \"" + q + "\"");
        } else {
            host.toastInfo(hits.size() + " matches - tap the product you want");
        }
    }

    // -------------------------------------------------------------------- cart

    private void addToCart(Product p, int qty) {
        try {
            cart.add(p, qty);
            renderCart();
            grid.repaint();
        } catch (IllegalArgumentException ex) {
            host.toastError(ex.getMessage());
        }
    }

    private void renderCart() {
        lines.removeAll();
        for (SaleItem l : cart.getLines()) lines.add(new CartRow(l));
        ((CardLayout) linesStack.getLayout()).show(linesStack, cart.isEmpty() ? "empty" : "lines");
        lines.revalidate();
        lines.repaint();
        int n = cart.itemCount();
        title.setText(n == 0 ? "Current sale" : "Current sale  -  " + n + (n == 1 ? " item" : " items"));
        updateTotals();
        heldBtn.setText(held.isEmpty() ? "Held" : "Held (" + held.size() + ")");
        clearBtn.setEnabled(!cart.isEmpty());
        holdBtn.setEnabled(!cart.isEmpty());
        grid.repaint();
    }

    private void onDiscountChanged() {
        if (updating) return;
        String t = discount.getText().trim();
        if (t.isEmpty()) t = "0";
        try {
            double pct = Fmt.parseDouble(t, "Discount");
            if (!host.isManager() && pct > ASSISTANT_MAX_DISCOUNT) {
                discount.setInvalid(true);
                host.toastError("Discounts above " + ASSISTANT_MAX_DISCOUNT + "% need a manager");
                return;
            }
            cart.setDiscountPercent(pct);
            discount.setInvalid(false);
            updateTotals();
        } catch (IllegalArgumentException ex) {
            discount.setInvalid(true);
        }
    }

    private void updateTotals() {
        subtotalVal.setText(Fmt.money(cart.subtotal()));
        discountVal.setText(cart.discount() > 0 ? "- " + Fmt.money(cart.discount()) : Fmt.money(0));
        discountVal.setForeground(cart.discount() > 0 ? Theme.success : Theme.muted);
        taxLbl.setText("Tax (" + trim(cart.getTaxRate()) + "%)");
        taxVal.setText(Fmt.money(cart.tax()));
        totalVal.setText(Fmt.money(cart.total()));
        boolean cash = payment.getSelected() == 0;
        cashPanel.setVisible(cash);
        charge.setText(cart.isEmpty() ? "Charge" : "Charge  " + Fmt.money(cart.total()));
        charge.setEnabled(!cart.isEmpty());
        quickCash.removeAll();
        if (cash && !cart.isEmpty()) {
            double total = cart.total();
            LinkedHashSet<Double> amounts = new LinkedHashSet<>();
            amounts.add(total);
            for (double step : new double[]{100, 500, 1000, 5000}) {
                double v = Math.ceil(total / step) * step;
                if (v > total) amounts.add(v);
                if (amounts.size() >= 4) break;
            }
            for (double a : amounts) {
                Btn b = new Btn(a == total ? "Exact" : Fmt.compact(a), Btn.Kind.SECONDARY).compact();
                b.addActionListener(e -> cashField.setText(String.format("%.2f", a)));
                quickCash.add(b);
            }
        }
        quickCash.revalidate();
        quickCash.repaint();
        updateChange();
    }

    private void updateChange() {
        if (payment.getSelected() != 0) return;
        try {
            String t = cashField.getText().trim();
            double tendered = t.isEmpty() ? 0 : Fmt.parseDouble(t, "Cash received");
            double diff = tendered - cart.total();
            cashField.setInvalid(false);
            if (cart.isEmpty() || t.isEmpty()) {
                changeVal.setText(Fmt.money(0));
                changeVal.setForeground(Theme.muted);
            } else if (diff >= -0.005) {
                changeVal.setText(Fmt.money(Math.max(0, diff)));
                changeVal.setForeground(Theme.success);
            } else {
                changeVal.setText("Short by " + Fmt.money(-diff));
                changeVal.setForeground(Theme.danger);
            }
        } catch (IllegalArgumentException ex) {
            cashField.setInvalid(true);
            changeVal.setText("-");
        }
    }

    private void onCustomerChanged() {
        if (updating) return;
        ReportService.CustomerStat c = customerStats.get(customer.editorText().toLowerCase());
        if (c == null) {
            String t = customer.editorText();
            tierInfo.setText(t.isEmpty() || t.equalsIgnoreCase("walk-in") ? " " : "New customer");
            tierInfo.setForeground(Theme.muted);
            applyLoyalty.setVisible(false);
            return;
        }
        String tier = c.tier();
        double pct = ReportService.tierDiscount(tier);
        tierInfo.setText(c.name + "  -  " + tier + "  -  " + c.visits + " visits, " + Fmt.money(c.spend));
        tierInfo.setForeground("GOLD".equals(tier) ? Theme.warning : "SILVER".equals(tier) ? Theme.info : Theme.muted);
        applyLoyalty.setText("Apply " + trim(pct) + "%");
        applyLoyalty.setVisible(pct > 0);
    }

    private static String trim(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    // ---------------------------------------------------------------- checkout

    private void checkout() {
        if (!isShowing()) return;
        if (cart.isEmpty()) {
            host.toastInfo("Add something to the basket first");
            return;
        }
        String method = payment.selectedLabel();
        double tendered = cart.total();
        if ("Cash".equals(method)) {
            try {
                String t = cashField.getText().trim();
                tendered = t.isEmpty() ? cart.total() : Fmt.parseDouble(t, "Cash received");
            } catch (IllegalArgumentException ex) {
                cashField.setInvalid(true);
                host.toastError(ex.getMessage());
                return;
            }
        }
        checkingOut = true;
        try {
            Sale sale = SystemContext.sales.checkout(cart, host.user().getUsername(), customer.editorText(), method, tendered);
            checkingOut = false;
            resetSale();
            ReceiptDialog.show(host, sale, true);
            search.requestFocusInWindow();
        } catch (IllegalArgumentException ex) {
            checkingOut = false;
            Dialogs.error(host, ex.getMessage());
            refresh();
        } finally {
            checkingOut = false;
        }
    }

    private void resetSale() {
        cart = new Cart(SystemContext.settings.getTaxRate());
        updating = true;
        customer.setSelectedItem("");
        discount.setText("0");
        discount.setInvalid(false);
        cashField.setText("");
        updating = false;
        renderCart();
        onCustomerChanged();
    }

    // -------------------------------------------------------------- held sales

    private void holdSale() {
        if (!isShowing()) return;
        if (cart.isEmpty()) {
            host.toastInfo("Nothing to hold");
            return;
        }
        held.add(new Held(cart, customer.editorText()));
        resetSale();
        host.toastInfo("Sale put on hold - resume it from \"Held\"");
    }

    private void showHeldMenu() {
        JPopupMenu m = new JPopupMenu();
        if (held.isEmpty()) {
            JMenuItem none = new JMenuItem("No held sales");
            none.setEnabled(false);
            m.add(none);
        }
        int i = 1;
        for (Held h : new ArrayList<>(held)) {
            String label = "#" + i++ + "   " + h.cart.itemCount() + " items   " + Fmt.money(h.cart.total()) + "   "
                    + h.time.format(HM) + (h.customer.isBlank() ? "" : "   " + h.customer);
            JMenuItem it = new JMenuItem("Resume " + label);
            it.addActionListener(e -> resume(h));
            m.add(it);
            JMenuItem del = new JMenuItem("Discard " + label);
            del.addActionListener(e -> {
                if (Dialogs.confirm(host, "Discard held sale", "Discard this held sale?", "Discard", true)) {
                    held.remove(h);
                    renderCart();
                }
            });
            m.add(del);
            m.addSeparator();
        }
        m.show(heldBtn, 0, heldBtn.getHeight() + 4);
    }

    private void resume(Held h) {
        if (!cart.isEmpty()) held.add(new Held(cart, customer.editorText()));
        held.remove(h);
        cart = h.cart;
        updating = true;
        customer.setSelectedItem(h.customer);
        discount.setText(trim(cart.getDiscountPercent()));
        updating = false;
        validateCartAgainstStock();
        renderCart();
        onCustomerChanged();
    }

    // ------------------------------------------------------------- components

    private class CartRow extends JPanel {
        CartRow(SaleItem l) {
            super(new BorderLayout(10, 0));
            setOpaque(false);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.border), new EmptyBorder(10, 18, 10, 14)));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
            setPreferredSize(new Dimension(300, 70));
            JPanel names = Ui.transparent(null);
            names.setLayout(new BoxLayout(names, BoxLayout.Y_AXIS));
            JLabel n = Ui.label(l.getName(), Theme.bold(13), Theme.text);
            JLabel u = Ui.label(Fmt.money(l.getUnitPrice()) + " each", Theme.font(11.5f), Theme.muted);
            names.add(Box.createVerticalGlue());
            names.add(n);
            names.add(u);
            names.add(Box.createVerticalGlue());
            add(names, BorderLayout.CENTER);

            Product p = SystemContext.inventory.findById(l.getProductId());
            Btn minus = new Btn("", "minus", Btn.Kind.SECONDARY).compact();
            Btn plus = new Btn("", "plus", Btn.Kind.SECONDARY).compact();
            JLabel q = Ui.label(String.valueOf(l.getQuantity()), Theme.bold(14), Theme.text);
            q.setHorizontalAlignment(SwingConstants.CENTER);
            q.setPreferredSize(new Dimension(30, 30));
            q.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            q.setToolTipText("Click to type a quantity");
            minus.addActionListener(e -> change(p, l.getQuantity() - 1));
            plus.addActionListener(e -> change(p, l.getQuantity() + 1));
            q.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    String s = Dialogs.prompt(host, "Quantity", l.getName(), "Quantity", String.valueOf(l.getQuantity()));
                    if (s == null) return;
                    try {
                        change(p, Fmt.parseInt(s, "Quantity"));
                    } catch (IllegalArgumentException ex) {
                        host.toastError(ex.getMessage());
                    }
                }
            });
            JPanel stepper = Ui.transparent(new FlowLayout(FlowLayout.CENTER, 4, 0));
            stepper.add(minus);
            stepper.add(q);
            stepper.add(plus);

            JLabel total = Ui.label(Fmt.money(l.lineTotal()), Theme.bold(13), Theme.text);
            total.setHorizontalAlignment(SwingConstants.RIGHT);
            total.setPreferredSize(new Dimension(92, 30));
            Btn rm = new Btn("", "close", Btn.Kind.GHOST).compact();
            rm.setToolTipText("Remove");
            rm.addActionListener(e -> { cart.remove(l.getProductId()); renderCart(); });
            JPanel east = Ui.transparent(new FlowLayout(FlowLayout.RIGHT, 2, 0));
            east.add(stepper);
            east.add(total);
            east.add(rm);
            add(east, BorderLayout.EAST);
        }

        private void change(Product p, int qty) {
            if (p == null) return;
            try {
                cart.setQuantity(p, qty);
                renderCart();
            } catch (IllegalArgumentException ex) {
                host.toastError(ex.getMessage());
            }
        }
    }

    /** One product on the grid. */
    private class Tile extends JComponent {
        private final Product p;
        private boolean hover;

        Tile(Product p) {
            this.p = p;
            setCursor(Cursor.getPredefinedCursor(isSellable(p) ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                @Override public void mouseClicked(MouseEvent e) {
                    if (!SwingUtilities.isLeftMouseButton(e)) return;
                    if (!isSellable(p)) {
                        host.toastError(p.isExpired() ? "\"" + p.getProductName() + "\" has expired" : "\"" + p.getProductName() + "\" is out of stock");
                        return;
                    }
                    addToCart(p, (e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) != 0 ? 5 : 1);
                }
            });
            setToolTipText(p.getProductName() + "  -  barcode " + (p.getBarcode().isEmpty() ? "n/a" : p.getBarcode()) + "  (Shift+click adds 5)");
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = Gfx.aa(g);
            int w = getWidth(), h = getHeight() - 2;
            boolean ok = isSellable(p);
            SaleItem inCart = cart.find(p.getProductId());
            if (!Theme.dark && ok) Gfx.fillRound(g2, 0, 2, w, h, 12, new Color(16, 40, 28, hover ? 18 : 9));
            Gfx.fillRound(g2, 0, 0, w, h, 12, hover && ok ? Theme.surfaceAlt : Theme.surface);
            Gfx.strokeRound(g2, 0, 0, w, h, 12, inCart != null ? Theme.primary : hover && ok ? Theme.borderStrong : Theme.border, inCart != null ? 1.8f : 1f);
            Composite old = g2.getComposite();
            if (!ok) g2.setComposite(AlphaComposite.SrcOver.derive(0.5f));

            int pad = 14;
            Font cf = Theme.font(11);
            Gfx.text(g2, p.getCategory().toUpperCase(), pad, 22, cf, Theme.faint);
            Font nf = Theme.bold(13.5f);
            FontMetrics nm = g2.getFontMetrics(nf);
            Gfx.text(g2, Gfx.ellipsize(p.getProductName(), nm, w - pad * 2), pad, 44, nf, Theme.text);
            Font pf = Theme.bold(17);
            Gfx.text(g2, Fmt.money(p.getPrice()), pad, h - 36 + 14, pf, Theme.dark ? Theme.primaryHover : Theme.primaryDark);

            String st;
            Color sc;
            if (p.isExpired()) { st = "Expired"; sc = Theme.danger; }
            else if (p.isOutOfStock()) { st = "Out of stock"; sc = Theme.danger; }
            else if (p.isLowStock()) { st = "Only " + p.getQuantity() + " left"; sc = Theme.warning; }
            else { st = p.getQuantity() + " in stock"; sc = Theme.muted; }
            g2.setColor(sc);
            g2.fillOval(pad, h - 19, 7, 7);
            Gfx.text(g2, st, pad + 13, h - 12, Theme.font(11.5f), sc);
            g2.setComposite(old);

            if (inCart != null) {
                String t = "x" + inCart.getQuantity();
                Font bf = Theme.bold(11.5f);
                FontMetrics bm = g2.getFontMetrics(bf);
                int bw = bm.stringWidth(t) + 16;
                Gfx.fillRound(g2, w - bw - 10, 10, bw, 22, 11, Theme.primary);
                Gfx.text(g2, t, w - bw - 10 + 8, 10 + (22 - bm.getHeight()) / 2.0 + bm.getAscent(), bf, Theme.onPrimary);
            }
            g2.dispose();
        }
    }

    /** Wraps tiles into as many equal columns as fit the width. */
    private static class TileGrid extends JPanel implements Scrollable {
        private static final int MIN_W = 190, TILE_H = 104, GAP = 12;

        TileGrid() {
            super(null);
            setOpaque(false);
            setLayout(new LayoutManager() {
                @Override public void addLayoutComponent(String name, Component comp) { }
                @Override public void removeLayoutComponent(Component comp) { }
                @Override public Dimension preferredLayoutSize(Container c) {
                    int w = c.getParent() != null ? c.getParent().getWidth() : 700;
                    int cols = Math.max(1, (w + GAP) / (MIN_W + GAP));
                    int rows = (c.getComponentCount() + cols - 1) / cols;
                    return new Dimension(w, rows == 0 ? 10 : rows * (TILE_H + GAP) - GAP + 4);
                }
                @Override public Dimension minimumLayoutSize(Container c) { return new Dimension(MIN_W, TILE_H); }
                @Override public void layoutContainer(Container c) {
                    int w = c.getWidth();
                    int cols = Math.max(1, (w + GAP) / (MIN_W + GAP));
                    int tw = (w - GAP * (cols - 1)) / cols;
                    for (int i = 0; i < c.getComponentCount(); i++) {
                        c.getComponent(i).setBounds((i % cols) * (tw + GAP), (i / cols) * (TILE_H + GAP), tw, TILE_H);
                    }
                }
            });
        }

        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 28; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return r.height - 40; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }
}
