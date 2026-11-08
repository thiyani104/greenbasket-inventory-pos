package ui;

import java.awt.*;
import java.time.LocalDate;
import javax.swing.*;
import model.Product;
import ui.kit.*;
import util.Fmt;
import util.SystemContext;

/** Add / edit a product. Quantity is only set for new products; later changes go through the stock dialog. */
public final class ProductDialog {

    private ProductDialog() { }

    /** Returns true when a product was saved. {@code existing == null} means "add". */
    public static boolean show(Window owner, Product existing) {
        boolean edit = existing != null;
        ModalDialog d = new ModalDialog(owner, edit ? "Edit product" : "Add product",
                edit ? "Change the details of " + existing.getProductName() + "." : "Create a new item in the catalogue.");

        Field name = new Field("e.g. Organic Mango 500g");
        Combo<String> category = new Combo<>(SystemContext.inventory.categories());
        category.setEditable(true);
        Combo<String> supplier = new Combo<>(SystemContext.inventory.suppliers());
        supplier.setEditable(true);
        Field cost = new Field("0.00");
        Field price = new Field("0.00");
        Field qty = new Field("0");
        Field reorder = new Field(String.valueOf(SystemContext.settings.getDefaultReorderLevel()));
        DatePicker expiry = new DatePicker();
        Field barcode = new Field("Scan or type");
        JLabel margin = Ui.label(" ", Theme.font(12), Theme.muted);
        JLabel error = Ui.label(" ", Theme.font(12.5f), Theme.danger);

        if (edit) {
            name.setText(existing.getProductName());
            category.setSelectedItem(existing.getCategory());
            supplier.setSelectedItem(existing.getSupplier());
            cost.setText(num(existing.getCostPrice()));
            price.setText(num(existing.getPrice()));
            qty.setText(String.valueOf(existing.getQuantity()));
            qty.setEnabled(false);
            reorder.setText(String.valueOf(existing.getReorderLevel()));
            expiry.setDate(existing.getExpiryDate());
            barcode.setText(existing.getBarcode());
        } else {
            category.setSelectedItem("");
            supplier.setSelectedItem("");
        }

        Btn gen = new Btn("Generate", Btn.Kind.SECONDARY);
        gen.addActionListener(e -> barcode.setText(generateBarcode()));
        JPanel barcodeRow = new JPanel(new BorderLayout(8, 0));
        barcodeRow.setOpaque(false);
        barcodeRow.add(barcode, BorderLayout.CENTER);
        barcodeRow.add(gen, BorderLayout.EAST);

        Form f = new Form();
        f.row("Product name", name);
        f.pair("Category", category, "Supplier", supplier);
        f.pair("Cost price (" + Fmt.currency() + ")", cost, "Selling price (" + Fmt.currency() + ")", price);
        f.span(margin, 6);
        f.pair(edit ? "Stock on hand" : "Opening stock", qty, "Reorder level", reorder);
        f.pair("Expiry date (optional)", expiry, "Barcode", barcodeRow);
        f.span(error, 12);
        d.setContent(f);

        Runnable updateMargin = () -> {
            try {
                double c = Fmt.parseDouble(cost.getText(), "Cost"), p = Fmt.parseDouble(price.getText(), "Price");
                if (p <= 0) { margin.setText(" "); return; }
                double m = (p - c) / p * 100;
                margin.setText(String.format("Profit %s per unit  -  margin %.1f%%", Fmt.money(p - c), m));
                margin.setForeground(m < 0 ? Theme.danger : m < 10 ? Theme.warning : Theme.success);
            } catch (IllegalArgumentException ex) {
                margin.setText(" ");
            }
        };
        cost.getDocument().addDocumentListener(new SimpleDoc(updateMargin));
        price.getDocument().addDocumentListener(new SimpleDoc(updateMargin));
        updateMargin.run();

        d.cancelButton("Cancel");
        Btn save = d.addAction(new Btn(edit ? "Save changes" : "Add product", Btn.Kind.PRIMARY));
        d.makeDefault(save);
        save.addActionListener(e -> {
            for (Field x : new Field[]{name, cost, price, qty, reorder, barcode}) x.setInvalid(false);
            error.setText(" ");
            try {
                String n = name.getText().trim();
                if (n.isEmpty()) { name.setInvalid(true); throw new IllegalArgumentException("Product name is required."); }
                String cat = category.editorText();
                if (cat.isEmpty()) throw new IllegalArgumentException("Category is required.");
                double c, p;
                int q, r;
                try { c = Fmt.parseDouble(cost.getText(), "Cost price"); } catch (IllegalArgumentException ex) { cost.setInvalid(true); throw ex; }
                try { p = Fmt.parseDouble(price.getText(), "Selling price"); } catch (IllegalArgumentException ex) { price.setInvalid(true); throw ex; }
                try { q = edit ? existing.getQuantity() : Fmt.parseInt(qty.getText().isBlank() ? "0" : qty.getText(), "Opening stock"); } catch (IllegalArgumentException ex) { qty.setInvalid(true); throw ex; }
                try { r = Fmt.parseInt(reorder.getText().isBlank() ? "0" : reorder.getText(), "Reorder level"); } catch (IllegalArgumentException ex) { reorder.setInvalid(true); throw ex; }
                LocalDate exp = expiry.getDate();
                if (c < 0) { cost.setInvalid(true); throw new IllegalArgumentException("Cost price cannot be negative."); }
                if (p < 0) { price.setInvalid(true); throw new IllegalArgumentException("Selling price cannot be negative."); }
                if (q < 0) { qty.setInvalid(true); throw new IllegalArgumentException("Stock cannot be negative."); }
                if (r < 0) { reorder.setInvalid(true); throw new IllegalArgumentException("Reorder level cannot be negative."); }
                if (p < c && !Dialogs.confirm(d, "Selling below cost", "The selling price (" + Fmt.money(p) + ") is lower than the cost ("
                        + Fmt.money(c) + "). You will lose money on every sale.\n\nSave anyway?", "Save anyway", true)) return;
                if (edit) {
                    SystemContext.inventory.updateProduct(existing.getProductId(), n, cat, supplier.editorText(), c, p, r, exp, barcode.getText().trim());
                } else {
                    Product np = new Product(SystemContext.inventory.nextProductId(), n, cat, supplier.editorText(), c, p, q, r, exp);
                    np.setBarcode(barcode.getText().trim());
                    SystemContext.inventory.addProduct(np);
                }
                d.accept();
            } catch (IllegalArgumentException ex) {
                error.setText(ex.getMessage());
                String m = ex.getMessage() == null ? "" : ex.getMessage();
                if (m.contains("called")) name.setInvalid(true);
                if (m.contains("Barcode")) barcode.setInvalid(true);
            }
        });
        SwingUtilities.invokeLater(name::requestFocusInWindow);
        return d.open();
    }

    private static String num(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    /** A free EAN-13 style code (valid check digit) that no product uses yet. */
    public static String generateBarcode() {
        int n = SystemContext.inventory.nextProductId();
        while (true) {
            String base = "4790001" + String.format("%05d", n++);
            int sum = 0;
            for (int i = 0; i < 12; i++) sum += (base.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
            String code = base + ((10 - sum % 10) % 10);
            if (SystemContext.inventory.findByBarcode(code) == null) return code;
        }
    }
}
