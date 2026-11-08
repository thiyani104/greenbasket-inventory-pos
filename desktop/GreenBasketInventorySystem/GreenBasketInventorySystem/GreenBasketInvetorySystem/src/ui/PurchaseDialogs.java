package ui;

import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import model.Product;
import model.PurchaseItem;
import model.PurchaseOrder;
import ui.kit.*;
import util.Fmt;
import util.SystemContext;

/** Dialogs for raising, reading and suggesting purchase orders. */
final class PurchaseDialogs {

    private PurchaseDialogs() { }

    private static class Opt {
        final Product p;
        Opt(Product p) { this.p = p; }
        @Override public String toString() { return p.getProductName() + "   (" + p.getQuantity() + " in stock)"; }
    }

    // ------------------------------------------------------------ new order

    /** Returns true if an order was created. {@code prefill} lines and {@code focus} product are optional. */
    static boolean create(MainFrame host, String supplierName, List<PurchaseItem> prefill, Product focus) {
        ModalDialog d = new ModalDialog(host, "New purchase order", "Order stock from a supplier. Stock is added when you mark the order as received.");
        Combo<String> supplier = new Combo<>(SystemContext.inventory.suppliers());
        supplier.setEditable(true);
        supplier.setSelectedItem(supplierName == null ? (focus != null ? focus.getSupplier() : "") : supplierName);
        Combo<Opt> product = new Combo<>();
        Field qty = new Field("Qty");
        Field cost = new Field("Unit cost");
        Field note = new Field("Optional note for your records");
        Btn add = new Btn("Add line", "plus", Btn.Kind.SOFT);
        JLabel total = Ui.label("", Theme.bold(15), Theme.text);
        JLabel error = Ui.label(" ", Theme.font(12.5f), Theme.danger);

        List<PurchaseItem> lines = new ArrayList<>();
        if (prefill != null) lines.addAll(prefill);
        ListTableModel<PurchaseItem> model = new ListTableModel<PurchaseItem>()
                .title("Product", 240, PurchaseItem::getName)
                .integer("Qty", 70, PurchaseItem::getQuantity)
                .money("Unit cost", 110, PurchaseItem::getUnitCost)
                .money("Line total", 120, PurchaseItem::lineTotal);
        Tables.DataTable table = Tables.table(model);
        table.setRowHeight(38);
        table.setEmptyText("No products added yet");
        JScrollPane sp = Tables.scroll(table);
        sp.setBorder(BorderFactory.createLineBorder(Theme.border));
        sp.setPreferredSize(new Dimension(640, 190));
        Btn remove = new Btn("Remove selected", "trash", Btn.Kind.GHOST).compact();

        Runnable refreshLines = () -> {
            model.setRows(lines);
            double t = 0;
            int units = 0;
            for (PurchaseItem i : lines) { t += i.lineTotal(); units += i.getQuantity(); }
            total.setText(units + " units  -  " + Fmt.money(t));
            remove.setEnabled(table.getSelectedRow() >= 0);
        };
        table.getSelectionModel().addListSelectionListener(e -> remove.setEnabled(table.getSelectedRow() >= 0));
        remove.addActionListener(e -> {
            PurchaseItem it = Tables.selected(table);
            if (it != null) { lines.remove(it); }
            refreshLines.run();
        });

        Runnable fillProducts = () -> {
            String s = supplier.editorText();
            List<Opt> opts = new ArrayList<>();
            List<Product> all = new ArrayList<>(SystemContext.inventory.getAllProducts());
            all.sort(Comparator.comparing(Product::getProductName, String.CASE_INSENSITIVE_ORDER));
            boolean any = all.stream().anyMatch(p -> p.getSupplier().equalsIgnoreCase(s));
            for (Product p : all) if (!any || p.getSupplier().equalsIgnoreCase(s)) opts.add(new Opt(p));
            product.setModel(new DefaultComboBoxModel<>(opts.toArray(new Opt[0])));
        };
        fillProducts.run();
        Runnable onPick = () -> {
            Opt o = (Opt) product.getSelectedItem();
            if (o == null) return;
            cost.setText(num(o.p.getCostPrice()));
            int target = Math.max(o.p.getReorderLevel() * 3, o.p.getReorderLevel() + 10);
            qty.setText(String.valueOf(Math.max(1, target - o.p.getQuantity())));
        };
        product.addActionListener(e -> onPick.run());
        ((JTextField) supplier.getEditor().getEditorComponent()).addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusLost(java.awt.event.FocusEvent e) { fillProducts.run(); onPick.run(); }
        });
        supplier.addActionListener(e -> { fillProducts.run(); onPick.run(); });
        if (focus != null) {
            for (int i = 0; i < product.getItemCount(); i++) if (product.getItemAt(i).p == focus) { product.setSelectedIndex(i); break; }
        }
        onPick.run();

        add.addActionListener(e -> {
            error.setText(" ");
            try {
                Opt o = (Opt) product.getSelectedItem();
                if (o == null) throw new IllegalArgumentException("Choose a product.");
                int q = Fmt.parseInt(qty.getText(), "Quantity");
                double c = Fmt.parseDouble(cost.getText(), "Unit cost");
                if (q <= 0) throw new IllegalArgumentException("Quantity must be at least 1.");
                if (c < 0) throw new IllegalArgumentException("Unit cost cannot be negative.");
                PurchaseItem existing = null;
                for (PurchaseItem i : lines) if (i.getProductId() == o.p.getProductId()) existing = i;
                if (existing != null) {
                    lines.remove(existing);
                    q += existing.getQuantity();
                }
                lines.add(new PurchaseItem(o.p.getProductId(), o.p.getProductName(), q, c));
                refreshLines.run();
            } catch (IllegalArgumentException ex) {
                error.setText(ex.getMessage());
            }
        });

        JPanel adder = new JPanel(new GridBagLayout());
        adder.setOpaque(false);
        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.gridy = 0;
        gc.weightx = 1;
        gc.gridx = 0;
        gc.insets = new Insets(0, 0, 0, 8);
        adder.add(product, gc);
        gc.gridx = 1;
        gc.weightx = 0;
        qty.setPreferredSize(new Dimension(80, 40));
        adder.add(qty, gc);
        gc.gridx = 2;
        cost.setPreferredSize(new Dimension(100, 40));
        adder.add(cost, gc);
        gc.gridx = 3;
        gc.insets = new Insets(0, 0, 0, 0);
        adder.add(add, gc);

        Form f = new Form();
        f.row("Supplier", supplier);
        f.span(sectionLabel("Add products"), 16);
        f.span(adder, 5);
        f.span(sp, 12);
        JPanel under = Ui.transparent(new BorderLayout());
        under.add(remove, BorderLayout.WEST);
        under.add(total, BorderLayout.EAST);
        f.span(under, 6);
        f.row("Note", note);
        f.span(error, 8);
        d.setContent(f);
        refreshLines.run();

        d.cancelButton("Cancel");
        Btn save = d.addAction(new Btn("Create order", "check", Btn.Kind.PRIMARY));
        save.addActionListener(e -> {
            try {
                PurchaseOrder po = SystemContext.purchases.create(supplier.editorText(), lines, host.user().getUsername(), note.getText().trim());
                host.toast("Purchase order #" + po.getId() + " created");
                d.accept();
            } catch (IllegalArgumentException ex) {
                error.setText(ex.getMessage());
            }
        });
        return d.open();
    }

    private static JLabel sectionLabel(String s) {
        return Ui.label(s, Theme.bold(12), Theme.muted);
    }

    private static String num(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    // ------------------------------------------------------------- view

    static void view(MainFrame host, PurchaseOrder po) {
        ModalDialog d = new ModalDialog(host, "Purchase order #" + po.getId(),
                po.getSupplier() + "  -  raised " + po.getCreated().format(Fmt.DATE_TIME_SHORT) + " by " + po.getCreatedBy());
        ListTableModel<PurchaseItem> model = new ListTableModel<PurchaseItem>()
                .title("Product", 240, PurchaseItem::getName)
                .integer("Qty", 70, PurchaseItem::getQuantity)
                .money("Unit cost", 110, PurchaseItem::getUnitCost)
                .money("Line total", 120, PurchaseItem::lineTotal);
        model.setRows(po.getItems());
        Tables.DataTable t = Tables.table(model);
        t.setRowHeight(38);
        JScrollPane sp = Tables.scroll(t);
        sp.setBorder(BorderFactory.createLineBorder(Theme.border));
        sp.setPreferredSize(new Dimension(640, Math.min(300, 60 + po.getItems().size() * 38)));

        JPanel info = Ui.transparent(new BorderLayout(10, 0));
        info.setBorder(new EmptyBorder(0, 0, 12, 0));
        info.add(new Badge(po.getStatus()), BorderLayout.WEST);
        String extra = po.getReceived() != null ? "Received " + po.getReceived().format(Fmt.DATE_TIME_SHORT) : po.getNote();
        info.add(Ui.label(extra == null ? "" : extra, Theme.font(12.5f), Theme.muted), BorderLayout.CENTER);
        info.add(Ui.label(po.units() + " units  -  " + Fmt.money(po.total()), Theme.bold(15), Theme.text), BorderLayout.EAST);

        JPanel body = Ui.transparent(new BorderLayout());
        body.add(info, BorderLayout.NORTH);
        body.add(sp, BorderLayout.CENTER);
        d.setContent(body);

        Btn export = new Btn("Export", "download", Btn.Kind.SECONDARY);
        export.addActionListener(e -> ReceiptDialog.showText(d, "Purchase order #" + po.getId(), orderText(po), "PO-" + po.getId() + ".txt"));
        d.addLeft(export);
        Btn close = new Btn("Close", Btn.Kind.SECONDARY);
        close.addActionListener(e -> d.dispose());
        if (po.isOpen()) {
            Btn cancel = new Btn("Cancel order", "close", Btn.Kind.GHOST);
            cancel.addActionListener(e -> {
                if (Dialogs.confirm(d, "Cancel order", "Cancel purchase order #" + po.getId() + "? No stock will be added.", "Cancel order", true)) {
                    SystemContext.purchases.cancel(po, host.user().getUsername());
                    host.toastInfo("Order #" + po.getId() + " cancelled");
                    d.dispose();
                }
            });
            d.addAction(cancel);
            d.addAction(close);
            Btn rec = d.addAction(new Btn("Mark received", "check", Btn.Kind.PRIMARY));
            rec.addActionListener(e -> {
                try {
                    SystemContext.purchases.receive(po, host.user().getUsername());
                    host.toast(po.units() + " units added to stock");
                    d.dispose();
                } catch (IllegalArgumentException ex) {
                    Dialogs.error(d, ex.getMessage());
                }
            });
            d.makeDefault(rec);
        } else {
            d.addAction(close);
            d.makeDefault(close);
        }
        d.open();
    }

    static String orderText(PurchaseOrder po) {
        int w = 60;
        StringBuilder sb = new StringBuilder();
        sb.append(SystemContext.settings.getStoreName().toUpperCase()).append("  -  PURCHASE ORDER #").append(po.getId()).append('\n');
        sb.append("=".repeat(w)).append('\n');
        sb.append("Supplier : ").append(po.getSupplier()).append('\n');
        sb.append("Date     : ").append(po.getCreated().format(Fmt.DATE_TIME_SHORT)).append('\n');
        sb.append("Raised by: ").append(po.getCreatedBy()).append('\n');
        sb.append("Status   : ").append(po.getStatus()).append('\n');
        if (!po.getNote().isBlank()) sb.append("Note     : ").append(po.getNote()).append('\n');
        sb.append("-".repeat(w)).append('\n');
        sb.append(String.format("%-30s %6s %10s %11s%n", "Product", "Qty", "Unit cost", "Total"));
        sb.append("-".repeat(w)).append('\n');
        for (PurchaseItem i : po.getItems()) {
            String n = i.getName().length() > 30 ? i.getName().substring(0, 29) + "~" : i.getName();
            sb.append(String.format("%-30s %6d %,10.2f %,11.2f%n", n, i.getQuantity(), i.getUnitCost(), i.lineTotal()));
        }
        sb.append("=".repeat(w)).append('\n');
        sb.append(String.format("%-30s %6d %10s %,11.2f%n", "TOTAL (" + Fmt.currency() + ")", po.units(), "", po.total()));
        return sb.toString();
    }

    // ------------------------------------------------------------ suggestions

    private static class Row {
        final PurchaseItem base;
        final Product product;
        final Field qty = new Field();
        Row(PurchaseItem base, Product product) {
            this.base = base;
            this.product = product;
            qty.setText(String.valueOf(base.getQuantity()));
        }
    }

    private static class Section {
        String supplier;
        final List<Row> rows = new ArrayList<>();
        final Field supplierField = new Field("Supplier name");
        final JPanel panel = new JPanel(new BorderLayout());
        final Btn create = new Btn("Create order", "truck", Btn.Kind.PRIMARY).compact();
        boolean done;
        JLabel totalLabel = Ui.label("", Theme.font(12.5f), Theme.muted);
    }

    /** Lists products that need restocking, grouped by supplier; one click raises an order per supplier. */
    static void suggestions(MainFrame host) {
        Map<String, List<PurchaseItem>> sug = SystemContext.purchases.suggestions();
        if (sug.isEmpty()) {
            Dialogs.info(host, "Nothing to reorder", "Every product is above its reorder level, or already covered by an open purchase order.");
            return;
        }
        ModalDialog d = new ModalDialog(host, "Suggested purchase orders",
                "Quantities aim for three times the reorder level, minus stock already on order. Adjust them before you create the orders.");
        List<Section> sections = new ArrayList<>();
        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setOpaque(false);

        for (Map.Entry<String, List<PurchaseItem>> e : sug.entrySet()) {
            Section s = new Section();
            s.supplier = e.getKey();
            for (PurchaseItem i : e.getValue()) s.rows.add(new Row(i, SystemContext.inventory.findById(i.getProductId())));
            s.panel.setBackground(Theme.surface);
            s.panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.border), new EmptyBorder(12, 14, 12, 14)));
            s.panel.setAlignmentX(0);

            JPanel head = Ui.transparent(new BorderLayout(10, 0));
            JPanel nameBox = Ui.transparent(null);
            nameBox.setLayout(new BoxLayout(nameBox, BoxLayout.Y_AXIS));
            boolean unassigned = e.getKey().equals("Unassigned");
            if (unassigned) {
                nameBox.add(Ui.label("No supplier set - type one:", Theme.font(12), Theme.warning));
                s.supplierField.setPreferredSize(new Dimension(220, 36));
                s.supplierField.setMaximumSize(new Dimension(260, 36));
                s.supplierField.setAlignmentX(0);
                nameBox.add(Box.createVerticalStrut(4));
                nameBox.add(s.supplierField);
            } else {
                nameBox.add(Ui.label(e.getKey(), Theme.bold(14), Theme.text));
            }
            nameBox.add(s.totalLabel);
            head.add(nameBox, BorderLayout.CENTER);
            head.add(s.create, BorderLayout.EAST);
            s.panel.add(head, BorderLayout.NORTH);

            JPanel rowsPanel = Ui.transparent(null);
            rowsPanel.setLayout(new BoxLayout(rowsPanel, BoxLayout.Y_AXIS));
            rowsPanel.setBorder(new EmptyBorder(8, 0, 0, 0));
            for (Row r : s.rows) {
                JPanel line = new JPanel(new BorderLayout(10, 0));
                line.setOpaque(false);
                line.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.border));
                line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
                JPanel t = Ui.transparent(null);
                t.setLayout(new BoxLayout(t, BoxLayout.Y_AXIS));
                t.setBorder(new EmptyBorder(6, 0, 6, 0));
                t.add(Ui.label(r.base.getName(), Theme.bold(13), Theme.text));
                Product p = r.product;
                String state = p == null ? "" : (p.isOutOfStock() ? "Sold out" : p.getQuantity() + " left") + "  -  reorder at " + p.getReorderLevel()
                        + "  -  cost " + Fmt.money(r.base.getUnitCost());
                t.add(Ui.label(state, Theme.font(11.5f), p != null && p.isOutOfStock() ? Theme.danger : Theme.muted));
                line.add(t, BorderLayout.CENTER);
                r.qty.setPreferredSize(new Dimension(80, 36));
                r.qty.setHorizontalAlignment(JTextField.RIGHT);
                JPanel qp = new JPanel(new GridBagLayout());
                qp.setOpaque(false);
                qp.add(r.qty);
                line.add(qp, BorderLayout.EAST);
                r.qty.getDocument().addDocumentListener(new SimpleDoc(() -> updateTotal(s)));
                rowsPanel.add(line);
            }
            s.panel.add(rowsPanel, BorderLayout.CENTER);
            updateTotal(s);
            list.add(s.panel);
            list.add(Box.createVerticalStrut(12));
            sections.add(s);

            s.create.addActionListener(ev -> createFrom(host, s, d, sections));
        }

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(list, BorderLayout.NORTH);
        JScrollPane sp = Tables.slimScroll(wrap);
        sp.getViewport().setOpaque(false);
        sp.setPreferredSize(new Dimension(640, Math.min(470, 80 + sug.values().stream().mapToInt(List::size).sum() * 52 + sug.size() * 70)));
        d.setContent(sp);

        Btn all = new Btn("Create all orders", "truck", Btn.Kind.PRIMARY);
        all.addActionListener(e -> {
            int n = 0;
            for (Section s : sections) if (!s.done && createFrom(host, s, d, sections)) n++;
            if (n > 0) host.toast(n + " purchase order(s) created");
            boolean allDone = sections.stream().allMatch(s -> s.done);
            if (allDone) d.dispose();
        });
        Btn close = new Btn("Close", Btn.Kind.SECONDARY);
        close.addActionListener(e -> d.dispose());
        d.addAction(close);
        d.addAction(all);
        d.open();
    }

    private static void updateTotal(Section s) {
        double t = 0;
        int units = 0;
        for (Row r : s.rows) {
            try {
                int q = Integer.parseInt(r.qty.getText().trim());
                if (q > 0) { t += q * r.base.getUnitCost(); units += q; }
                r.qty.setInvalid(false);
            } catch (NumberFormatException ex) {
                r.qty.setInvalid(true);
            }
        }
        s.totalLabel.setText(s.rows.size() + " products  -  " + units + " units  -  " + Fmt.money(t));
    }

    private static boolean createFrom(MainFrame host, Section s, Window dlg, List<Section> all) {
        List<PurchaseItem> lines = new ArrayList<>();
        for (Row r : s.rows) {
            int q;
            try {
                q = Integer.parseInt(r.qty.getText().trim());
            } catch (NumberFormatException ex) {
                r.qty.setInvalid(true);
                host.toastError("Quantities must be whole numbers");
                return false;
            }
            if (q > 0) lines.add(new PurchaseItem(r.base.getProductId(), r.base.getName(), q, r.base.getUnitCost()));
        }
        String sup = s.supplier.equals("Unassigned") ? s.supplierField.getText().trim() : s.supplier;
        try {
            PurchaseOrder po = SystemContext.purchases.create(sup, lines, host.user().getUsername(), "Auto-suggested reorder");
            s.done = true;
            s.create.setText("Order #" + po.getId());
            s.create.setEnabled(false);
            for (Row r : s.rows) r.qty.setEnabled(false);
            s.supplierField.setEnabled(false);
            s.panel.setBackground(Theme.surfaceAlt);
            host.toast("Order #" + po.getId() + " created for " + po.getSupplier());
            return true;
        } catch (IllegalArgumentException ex) {
            s.supplierField.setInvalid(s.supplier.equals("Unassigned"));
            host.toastError(ex.getMessage());
            return false;
        }
    }
}
