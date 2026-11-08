package ui;

import java.awt.Component;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import javax.swing.JLabel;
import model.Product;
import util.Formats;
import util.SystemContext;

/** Add a new product or edit an existing one. Stock levels change through the stock dialog, not here. */
public class ProductDialog extends BaseDialog {

    private final Product existing;
    private final FlatTextField name = new FlatTextField(24).placeholder("e.g. Organic Apple");
    private final FlatComboBox<String> category = new FlatComboBox<String>().makeEditable();
    private final FlatComboBox<String> supplier = new FlatComboBox<String>().makeEditable();
    private final FlatTextField price = new FlatTextField(10).placeholder("0.00");
    private final FlatTextField cost = new FlatTextField(10).placeholder("0.00");
    private final FlatTextField qty = new FlatTextField(10).placeholder("0");
    private final FlatTextField reorder = new FlatTextField(10);
    private final FlatTextField expiry = new FlatTextField(10).placeholder("yyyy-mm-dd");
    private final JLabel margin = UI.muted(" ");
    private boolean saved;

    private ProductDialog(Component parent, Product existing) {
        super(parent, existing == null ? "Add product" : "Edit product",
                existing == null ? "Create a new item in the catalogue." : "Product #" + existing.getProductId() + " · stock changes are made from Stock & Orders.",
                existing == null ? "Add product" : "Save changes");
        this.existing = existing;
        for (String c : SystemContext.inventory.getCategoryChoices()) category.addItem(c);
        for (String s : SystemContext.inventory.getAllSuppliers()) supplier.addItem(s);
        category.setSelectedItem(null);
        supplier.setSelectedItem(null);
        reorder.setText(String.valueOf(SystemContext.settings.getDefaultReorderLevel()));

        form.addRow("Product name", name);
        form.addPair("Category", category, "Supplier (optional)", supplier);
        form.addPair("Selling price (" + Formats.getCurrency() + ")", price, "Cost price (" + Formats.getCurrency() + ")", cost);
        form.addFull(margin);
        if (existing == null) {
            form.addPair("Opening stock", qty, "Reorder level", reorder);
        } else {
            form.addPair("In stock", readOnly(String.valueOf(existing.getQuantity())), "Reorder level", reorder);
        }
        form.addRow("Expiry date (optional)", expiry, "Leave empty for items that do not expire. Expired stock cannot be sold.");

        if (existing != null) {
            name.setText(existing.getProductName());
            category.setSelectedItem(existing.getCategory());
            supplier.setSelectedItem(existing.getSupplier().isEmpty() ? null : existing.getSupplier());
            price.setText(String.valueOf(existing.getPrice()));
            cost.setText(String.valueOf(existing.getCost()));
            reorder.setText(String.valueOf(existing.getReorderLevel()));
            expiry.setText(Formats.date(existing.getExpiryDate()));
        }
        javax.swing.event.DocumentListener dl = new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { updateMargin(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { updateMargin(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { updateMargin(); }
        };
        price.getDocument().addDocumentListener(dl);
        cost.getDocument().addDocumentListener(dl);
        updateMargin();
    }

    private static FlatTextField readOnly(String text) {
        FlatTextField f = new FlatTextField(10);
        f.setText(text);
        f.setEditable(false);
        f.setFocusable(false);
        return f;
    }

    private void updateMargin() {
        try {
            double p = Double.parseDouble(price.getText().trim());
            double c = Double.parseDouble(cost.getText().trim());
            if (p > 0) {
                double m = (p - c) / p * 100;
                margin.setText("Margin " + Formats.percent(m) + " · profit " + Formats.money(p - c) + " per unit");
                margin.setForeground(c > p ? Theme.DANGER : Theme.PRIMARY_DARK);
                return;
            }
        } catch (NumberFormatException ignored) {
            // fall through
        }
        margin.setText(" ");
    }

    @Override
    protected void apply() {
        LocalDate exp;
        try {
            exp = Formats.parseDate(expiry.getText());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Expiry date must look like 2026-12-31.");
        }
        Product p = new Product(existing == null ? 0 : existing.getProductId(), name.getText(), category.editorText(),
                supplier.editorText(), parseMoney(price.getText(), "Selling price"), parseMoney(cost.getText(), "Cost price"),
                existing == null ? parseInt(qty.getText().isBlank() ? "0" : qty.getText(), "Opening stock") : existing.getQuantity(),
                parseInt(reorder.getText(), "Reorder level"), exp);
        if (existing == null) SystemContext.inventory.addProduct(p, SystemContext.actor());
        else SystemContext.inventory.updateProduct(p);
        SystemContext.log.add(SystemContext.actor(), existing == null ? "Add product" : "Edit product", p.getProductName());
        saved = true;
        dispose();
    }

    /** Returns true if something was saved. */
    public static boolean showAdd(Component parent) {
        ProductDialog d = new ProductDialog(parent, null);
        d.open();
        return d.saved;
    }

    public static boolean showEdit(Component parent, Product p) {
        ProductDialog d = new ProductDialog(parent, p);
        d.open();
        return d.saved;
    }
}
