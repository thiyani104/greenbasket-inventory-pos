package ui;

import java.awt.Component;
import javax.swing.JLabel;
import model.Product;
import service.ServiceException;
import util.Formats;
import util.SystemContext;

/** Receive a delivery, write off damaged / expired goods, or record a physical stock count. */
public class StockAdjustDialog extends BaseDialog {

    private static final String RECEIVE = "Receive stock (delivery)";
    private static final String WRITE_OFF = "Write off (damaged / expired / lost)";
    private static final String COUNT = "Set counted quantity (stock take)";

    private final Product product;
    private final FlatComboBox<String> action = new FlatComboBox<>(RECEIVE, WRITE_OFF, COUNT);
    private final FlatTextField qty = new FlatTextField(10);
    private final FlatTextField newCost = new FlatTextField(10).placeholder("keep " + Formats.number(0));
    private final FlatTextField note = new FlatTextField(24).placeholder("Optional note, e.g. delivery note number");
    private final JLabel preview = UI.label(" ", java.awt.Font.BOLD, 13, Theme.PRIMARY_DARK);
    private final JLabel qtyLabel = UI.label("Quantity received", java.awt.Font.BOLD, 12, Theme.TEXT);
    private boolean saved;

    private StockAdjustDialog(Component parent, Product product, int preselect) {
        super(parent, "Adjust stock", product.getProductName() + " · currently " + product.getQuantity() + " in stock", "Save");
        this.product = product;
        action.setSelectedIndex(preselect);
        newCost.placeholder("keep " + Formats.number(product.getCost()));
        form.addRow("Action", action);
        form.addPair("Quantity", qty, "New unit cost (optional)", newCost);
        form.addRow("Note", note);
        form.addFull(preview);
        action.addActionListener(e -> update());
        qty.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { update(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { update(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { update(); }
        });
        update();
    }

    private void update() {
        boolean receive = RECEIVE.equals(action.getSelectedItem());
        newCost.setEnabled(receive);
        try {
            int q = Integer.parseInt(qty.getText().trim());
            int result = receive ? product.getQuantity() + q
                    : WRITE_OFF.equals(action.getSelectedItem()) ? product.getQuantity() - q : q;
            preview.setText("New stock level: " + result);
            preview.setForeground(result < 0 ? Theme.DANGER : Theme.PRIMARY_DARK);
        } catch (NumberFormatException e) {
            preview.setText(" ");
        }
    }

    @Override
    protected void apply() {
        int q = parseInt(qty.getText(), "Quantity");
        String n = note.getText().trim();
        String user = SystemContext.actor();
        Object sel = action.getSelectedItem();
        String what;
        if (RECEIVE.equals(sel)) {
            Double c = newCost.getText().isBlank() ? null : parseMoney(newCost.getText(), "Unit cost");
            SystemContext.inventory.receiveStock(product.getProductId(), q, c, n.isEmpty() ? "Stock received" : n, user);
            what = "+" + q + " received";
        } else if (WRITE_OFF.equals(sel)) {
            SystemContext.inventory.writeOff(product.getProductId(), q, n.isEmpty() ? "Written off" : n, user);
            what = "-" + q + " written off";
        } else {
            SystemContext.inventory.setCount(product.getProductId(), q, n.isEmpty() ? "Stock count" : n, user);
            what = "counted " + q;
        }
        SystemContext.log.add(user, "Stock change", product.getProductName() + " · " + what);
        saved = true;
        dispose();
    }

    /** mode: 0 = receive, 1 = write off, 2 = count. */
    public static boolean open(Component parent, Product p, int mode) {
        StockAdjustDialog d = new StockAdjustDialog(parent, p, mode);
        d.open();
        return d.saved;
    }
}
