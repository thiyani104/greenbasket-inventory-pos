package ui;

import java.awt.*;
import java.awt.print.PrinterException;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import model.Sale;
import service.SalesService;
import ui.kit.*;
import util.FileManager;
import util.SystemContext;

/** Shows a receipt on a paper-like card, with print and save. */
public final class ReceiptDialog {

    private ReceiptDialog() { }

    public static void show(Window owner, Sale sale, boolean justSold) {
        String text = SalesService.receiptText(sale, SystemContext.settings.getStoreName());
        ModalDialog d = new ModalDialog(owner, justSold ? "Sale complete" : "Receipt #" + sale.getId(),
                justSold ? "Receipt #" + sale.getId() + " has been recorded." : null);

        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setFont(Theme.mono(12.5f));
        area.setForeground(new Color(0x1B1B1B));
        area.setBackground(new Color(0xFFFEFA));
        area.setBorder(new EmptyBorder(18, 18, 18, 18));
        area.setCaretPosition(0);
        JScrollPane sp = Tables.slimScroll(area);
        sp.setBorder(BorderFactory.createLineBorder(Theme.border));
        sp.getViewport().setOpaque(true);
        sp.getViewport().setBackground(new Color(0xFFFEFA));
        sp.setPreferredSize(new Dimension(430, Math.min(520, 150 + sale.getItems().size() * 40 + 240)));
        d.setContent(sp);

        if (justSold && "Cash".equals(sale.getPayment()) && sale.getChange() > 0) {
            JLabel change = Ui.label("Change due: " + util.Fmt.money(sale.getChange()), Theme.bold(16), Theme.success);
            change.setBorder(new EmptyBorder(0, 0, 12, 0));
            d.body().add(change, BorderLayout.NORTH);
        }

        Btn print = new Btn("Print", "print", Btn.Kind.SECONDARY);
        print.addActionListener(e -> {
            try {
                area.print(null, null, true, null, null, true);
            } catch (PrinterException | HeadlessException ex) {
                Dialogs.error(d, "Could not print: " + ex.getMessage());
            }
        });
        Btn save = new Btn("Save", "download", Btn.Kind.SECONDARY);
        save.addActionListener(e -> {
            Path p = Pick.save(d, "receipt-" + sale.getId() + ".txt", "Text file", "txt");
            if (p == null) return;
            try {
                FileManager.writeText(p, text);
            } catch (RuntimeException ex) {
                Dialogs.error(d, ex.getMessage());
            }
        });
        d.addLeft(print);
        d.addLeft(save);
        Btn close = d.addAction(new Btn(justSold ? "New sale" : "Close", Btn.Kind.PRIMARY));
        close.addActionListener(e -> d.dispose());
        d.makeDefault(close);
        d.open();
    }

    /** Generic read-only text viewer (purchase orders, reports) with save. */
    public static void showText(Window owner, String title, String text, String fileName) {
        ModalDialog d = new ModalDialog(owner, title, null);
        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setFont(Theme.mono(12.5f));
        area.setForeground(new Color(0x1B1B1B));
        area.setBackground(new Color(0xFFFEFA));
        area.setBorder(new EmptyBorder(16, 16, 16, 16));
        area.setCaretPosition(0);
        JScrollPane sp = Tables.slimScroll(area);
        sp.setBorder(BorderFactory.createLineBorder(Theme.border));
        sp.getViewport().setOpaque(true);
        sp.getViewport().setBackground(new Color(0xFFFEFA));
        sp.setPreferredSize(new Dimension(520, 420));
        d.setContent(sp);
        Btn save = new Btn("Save", "download", Btn.Kind.SECONDARY);
        save.addActionListener(e -> {
            Path p = Pick.save(d, fileName, "Text file", "txt");
            if (p != null) FileManager.writeText(p, text);
        });
        d.addLeft(save);
        Btn close = d.addAction(new Btn("Close", Btn.Kind.PRIMARY));
        close.addActionListener(e -> d.dispose());
        d.makeDefault(close);
        d.open();
    }

    static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd");
}
