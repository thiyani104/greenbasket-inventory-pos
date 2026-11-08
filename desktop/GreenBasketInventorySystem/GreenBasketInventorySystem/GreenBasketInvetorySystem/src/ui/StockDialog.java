package ui;

import java.awt.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import model.Product;
import model.StockMovement;
import ui.kit.*;
import util.Fmt;
import util.SystemContext;

/** Receive, write off or count stock for one product, and browse a product's movement history. */
public final class StockDialog {

    private StockDialog() { }

    private static final String[][] REASONS = {
            {"Delivery received", "Supplier delivery", "Returned by customer", "Found in storeroom"},
            {"Damaged", "Spoiled", "Expired", "Lost or stolen", "Used as sample", "Supplier return"},
            {"Stock count", "Recount after audit", "Data entry mistake"}};

    /** Returns true if the stock changed. */
    public static boolean adjust(Window owner, Product p) {
        ModalDialog d = new ModalDialog(owner, "Adjust stock", p.getProductName() + "  -  " + p.getCategory());

        JPanel now = new JPanel(new BorderLayout());
        now.setBackground(Theme.surfaceAlt);
        now.setBorder(new EmptyBorder(12, 14, 12, 14));
        now.add(Ui.label("On hand now", Theme.font(12.5f), Theme.muted), BorderLayout.WEST);
        now.add(Ui.label(p.getQuantity() + " units", Theme.bold(15), Theme.text), BorderLayout.EAST);

        Segmented mode = new Segmented("Receive", "Write off", "Count");
        Field qty = new Field("0");
        Combo<String> reason = new Combo<>(REASONS[0]);
        reason.setEditable(true);
        JLabel qtyLabel = Ui.label("Units received", Theme.bold(12), Theme.muted);
        JLabel preview = Ui.label(" ", Theme.bold(13), Theme.success);
        JLabel error = Ui.label(" ", Theme.font(12.5f), Theme.danger);

        JPanel qtyBox = Ui.transparent(null);
        qtyBox.setLayout(new BoxLayout(qtyBox, BoxLayout.Y_AXIS));
        qtyLabel.setAlignmentX(0);
        qty.setAlignmentX(0);
        qtyBox.add(qtyLabel);
        qtyBox.add(Box.createVerticalStrut(5));
        qtyBox.add(qty);

        Form f = new Form();
        f.span(now, 0);
        f.span(mode, 16);
        f.span(qtyBox, 16);
        f.row("Reason / note", reason);
        f.span(preview, 12);
        f.span(error, 4);
        d.setContent(f);

        Runnable update = () -> {
            int m = mode.getSelected();
            qtyLabel.setText(m == 0 ? "Units received" : m == 1 ? "Units to remove" : "Counted quantity on the shelf");
            try {
                int v = Fmt.parseInt(qty.getText().isBlank() ? "0" : qty.getText(), "Quantity");
                int after = m == 0 ? p.getQuantity() + v : m == 1 ? p.getQuantity() - v : v;
                preview.setText("Stock after this change:  " + after + " units");
                preview.setForeground(after < 0 ? Theme.danger : after <= p.getReorderLevel() ? Theme.warning : Theme.success);
                qty.setInvalid(false);
            } catch (IllegalArgumentException ex) {
                preview.setText(" ");
            }
        };
        qty.getDocument().addDocumentListener(new SimpleDoc(update));
        mode.onChange(i -> {
            reason.setModel(new DefaultComboBoxModel<>(REASONS[i]));
            reason.setSelectedIndex(0);
            if (i == 2) qty.setText(String.valueOf(p.getQuantity()));
            else if (qty.getText().equals(String.valueOf(p.getQuantity()))) qty.setText("");
            update.run();
            qty.requestFocusInWindow();
            qty.selectAll();
        });
        update.run();

        d.cancelButton("Cancel");
        Btn save = d.addAction(new Btn("Update stock", Btn.Kind.PRIMARY));
        d.makeDefault(save);
        save.addActionListener(e -> {
            error.setText(" ");
            try {
                int v = Fmt.parseInt(qty.getText().isBlank() ? "0" : qty.getText(), "Quantity");
                String note = reason.editorText();
                switch (mode.getSelected()) {
                    case 0:
                        if (v <= 0) throw new IllegalArgumentException("Enter how many units were received.");
                        SystemContext.inventory.adjustStock(p.getProductId(), v, "RESTOCK", note);
                        break;
                    case 1:
                        if (v <= 0) throw new IllegalArgumentException("Enter how many units to remove.");
                        SystemContext.inventory.adjustStock(p.getProductId(), -v, "WRITE_OFF", note);
                        break;
                    default:
                        if (SystemContext.inventory.setCount(p.getProductId(), v, note) == 0)
                            throw new IllegalArgumentException("That matches the current stock - nothing to change.");
                }
                d.accept();
            } catch (IllegalArgumentException ex) {
                qty.setInvalid(true);
                error.setText(ex.getMessage());
            }
        });
        SwingUtilities.invokeLater(qty::requestFocusInWindow);
        return d.open();
    }

    /** Movement history for one product. */
    public static void history(Window owner, Product p) {
        ModalDialog d = new ModalDialog(owner, "Stock history", p.getProductName() + "  -  " + p.getQuantity() + " in stock now");
        List<StockMovement> rows = SystemContext.inventory.movementsFor(p.getProductId());
        ListTableModel<StockMovement> model = movementModel(false);
        model.setRows(rows);
        Tables.DataTable t = Tables.table(model);
        t.setRowHeight(40);
        t.setEmptyText("No stock movements recorded yet");
        JScrollPane sp = Tables.scroll(t);
        sp.setBorder(BorderFactory.createLineBorder(Theme.border));
        sp.setPreferredSize(new Dimension(760, 360));
        d.setContent(sp);
        Btn close = d.addAction(new Btn("Close", Btn.Kind.PRIMARY));
        close.addActionListener(e -> d.dispose());
        d.makeDefault(close);
        d.open();
    }

    static ListTableModel<StockMovement> movementModel(boolean withProduct) {
        ListTableModel<StockMovement> m = new ListTableModel<>();
        m.dateTime("Time", 150, StockMovement::getTime);
        if (withProduct) m.title("Product", 190, StockMovement::getProductName);
        m.badge("Type", 120, StockMovement::getType);
        m.integer("Change", 80, StockMovement::getChange);
        m.integer("Balance", 80, StockMovement::getQuantityAfter);
        m.muted("By", 90, StockMovement::getUser);
        m.muted("Note", 220, StockMovement::getNote);
        int ci = withProduct ? 3 : 2;
        m.columns().get(ci).fmt = v -> ((Integer) v) > 0 ? "+" + v : String.valueOf(v);
        m.columns().get(ci).bold = true;
        return m;
    }
}
