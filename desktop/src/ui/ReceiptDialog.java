package ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Window;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import model.Sale;
import util.ReceiptFormatter;
import util.SystemContext;

/** Shows a receipt as printed paper, with print / save / copy. */
public final class ReceiptDialog {

    private ReceiptDialog() {}

    public static void show(Component parent, Sale sale) {
        Window owner = parent instanceof Window w ? w : SwingUtilities.getWindowAncestor(parent);
        JDialog d = new JDialog(owner, "Receipt " + sale.getReceiptNumber(), JDialog.DEFAULT_MODALITY_TYPE);
        String text = ReceiptFormatter.format(sale, SystemContext.settings);

        JTextArea area = new JTextArea(text);
        area.setFont(Theme.mono(13));
        area.setEditable(false);
        area.setBackground(Color.WHITE);
        area.setForeground(sale.isVoided() ? Theme.DANGER : new Color(0x222222));
        area.setBorder(BorderFactory.createEmptyBorder(22, 24, 22, 24));
        area.setCaretPosition(0);

        JScrollPane sp = UI.scroll(area);
        sp.getViewport().setOpaque(true);
        sp.getViewport().setBackground(Color.WHITE);
        sp.setBorder(BorderFactory.createLineBorder(Theme.BORDER));

        JPanel paper = new JPanel(new BorderLayout());
        paper.setBackground(Theme.BG);
        paper.setBorder(BorderFactory.createEmptyBorder(20, 24, 8, 24));
        paper.add(sp, BorderLayout.CENTER);

        FlatButton print = FlatButton.secondary("Print").compact();
        FlatButton save = FlatButton.secondary("Save as text").compact();
        FlatButton copy = FlatButton.secondary("Copy").compact();
        FlatButton done = FlatButton.primary("Done");
        print.addActionListener(e -> {
            try {
                area.print();
            } catch (java.awt.print.PrinterException ex) {
                Dialogs.error(d, "Could not print: " + ex.getMessage());
            }
        });
        save.addActionListener(e -> {
            Path p = Dialogs.chooseSave(d, "Save receipt", sale.getReceiptNumber() + ".txt");
            if (p == null) return;
            try {
                Files.writeString(p, text, StandardCharsets.UTF_8);
                Toast.success(d, "Receipt saved");
            } catch (java.io.IOException ex) {
                Dialogs.error(d, "Could not save: " + ex.getMessage());
            }
        });
        copy.addActionListener(e -> {
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new java.awt.datatransfer.StringSelection(text), null);
            Toast.success(d, "Receipt copied");
        });
        done.addActionListener(e -> d.dispose());

        JPanel foot = new JPanel(new BorderLayout());
        foot.setBackground(Theme.BG);
        foot.setBorder(BorderFactory.createEmptyBorder(8, 24, 18, 24));
        foot.add(UI.flow(FlowLayout.LEFT, 8, 0, print, save, copy), BorderLayout.WEST);
        foot.add(UI.flow(FlowLayout.RIGHT, 0, 0, done), BorderLayout.EAST);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BG);
        root.add(paper, BorderLayout.CENTER);
        root.add(foot, BorderLayout.SOUTH);
        d.setContentPane(root);
        d.getRootPane().setDefaultButton(done);
        d.getRootPane().registerKeyboardAction(e -> d.dispose(), KeyStroke.getKeyStroke("ESCAPE"),
                javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
        d.setSize(460, 640);
        d.setLocationRelativeTo(owner);
        d.setVisible(true);
    }
}
