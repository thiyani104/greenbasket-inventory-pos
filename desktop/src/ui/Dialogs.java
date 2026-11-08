package ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.nio.file.Path;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/** Consistent, themed message / confirm / prompt dialogs and native file pickers. */
public final class Dialogs {

    public enum Kind { INFO, SUCCESS, WARNING, ERROR }

    private Dialogs() {}

    private static Window owner(Component parent) {
        return parent == null ? null : parent instanceof Window w ? w : SwingUtilities.getWindowAncestor(parent);
    }

    private static Color accent(Kind k) {
        return switch (k) {
            case INFO -> Theme.INFO;
            case SUCCESS -> Theme.PRIMARY;
            case WARNING -> Theme.WARN;
            case ERROR -> Theme.DANGER;
        };
    }

    private static JComponent badge(Kind k) {
        Color c = accent(k);
        return new JComponent() {
            { setPreferredSize(new java.awt.Dimension(44, 44)); setMinimumSize(getPreferredSize()); setMaximumSize(getPreferredSize()); }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.alpha(c, 30));
                g2.fillOval(0, 0, 44, 44);
                g2.dispose();
                Icons.Kind ik = switch (k) {
                    case INFO -> Icons.Kind.ACTIVITY;
                    case SUCCESS -> Icons.Kind.CHECK;
                    case WARNING, ERROR -> Icons.Kind.WARNING;
                };
                if (k == Kind.INFO) ik = Icons.Kind.RECEIPT;
                Icons.of(ik, 22, c).paintIcon(this, g, 11, 11);
            }
        };
    }

    private static String html(String msg) {
        String safe = msg.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
        return "<html><body style='width:300px'>" + safe + "</body></html>";
    }

    private static JDialog shell(Component parent, String title, Kind kind, String message, JComponent extra, FlatButton... buttons) {
        JDialog d = new JDialog(owner(parent), title, JDialog.DEFAULT_MODALITY_TYPE);
        JPanel root = new JPanel(new BorderLayout(16, 0));
        root.setBackground(Color.WHITE);
        root.setBorder(BorderFactory.createEmptyBorder(24, 24, 18, 24));
        JPanel iconWrap = UI.transparent(new BorderLayout());
        iconWrap.add(badge(kind), BorderLayout.NORTH);
        root.add(iconWrap, BorderLayout.WEST);
        JPanel center = UI.transparent(null);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        JLabel t = UI.heading(title);
        t.setAlignmentX(0);
        center.add(t);
        center.add(Box.createVerticalStrut(6));
        JLabel m = UI.label(html(message), java.awt.Font.PLAIN, 13, Theme.MUTED);
        m.setAlignmentX(0);
        center.add(m);
        if (extra != null) {
            center.add(Box.createVerticalStrut(12));
            extra.setAlignmentX(0);
            center.add(extra);
        }
        root.add(center, BorderLayout.CENTER);
        JPanel foot = UI.flow(java.awt.FlowLayout.RIGHT, 8, 0, buttons);
        foot.setBorder(BorderFactory.createEmptyBorder(18, 0, 0, 0));
        root.add(foot, BorderLayout.SOUTH);
        d.setContentPane(root);
        d.setResizable(false);
        d.pack();
        d.setLocationRelativeTo(owner(parent));
        return d;
    }

    public static void message(Component parent, Kind kind, String title, String message) {
        FlatButton ok = kind == Kind.ERROR ? FlatButton.danger("OK") : FlatButton.primary("OK");
        JDialog[] holder = new JDialog[1];
        ok.addActionListener(e -> holder[0].dispose());
        holder[0] = shell(parent, title, kind, message, null, ok);
        holder[0].getRootPane().setDefaultButton(ok);
        holder[0].setVisible(true);
    }

    public static void error(Component parent, String message) { message(parent, Kind.ERROR, "Something went wrong", message); }

    public static void info(Component parent, String title, String message) { message(parent, Kind.INFO, title, message); }

    public static void success(Component parent, String title, String message) { message(parent, Kind.SUCCESS, title, message); }

    public static boolean confirm(Component parent, String title, String message, String okLabel, boolean danger) {
        boolean[] result = {false};
        FlatButton ok = danger ? FlatButton.danger(okLabel) : FlatButton.primary(okLabel);
        FlatButton cancel = FlatButton.secondary("Cancel");
        JDialog[] holder = new JDialog[1];
        ok.addActionListener(e -> { result[0] = true; holder[0].dispose(); });
        cancel.addActionListener(e -> holder[0].dispose());
        holder[0] = shell(parent, title, danger ? Kind.WARNING : Kind.INFO, message, null, cancel, ok);
        holder[0].getRootPane().setDefaultButton(ok);
        holder[0].setVisible(true);
        return result[0];
    }

    /** Asks for one line of text. Returns null if cancelled. */
    public static String prompt(Component parent, String title, String message, String initial) {
        String[] result = {null};
        FlatTextField field = new FlatTextField(24);
        field.setText(initial == null ? "" : initial);
        FlatButton ok = FlatButton.primary("OK");
        FlatButton cancel = FlatButton.secondary("Cancel");
        JDialog[] holder = new JDialog[1];
        ok.addActionListener(e -> { result[0] = field.getText().trim(); holder[0].dispose(); });
        cancel.addActionListener(e -> holder[0].dispose());
        holder[0] = shell(parent, title, Kind.INFO, message, field, cancel, ok);
        holder[0].getRootPane().setDefaultButton(ok);
        SwingUtilities.invokeLater(field::requestFocusInWindow);
        holder[0].setVisible(true);
        return result[0];
    }

    // ============================== file pickers ==============================

    public static Path chooseSave(Component parent, String title, String defaultName) {
        return choose(parent, title, defaultName, FileDialog.SAVE);
    }

    public static Path chooseOpen(Component parent, String title) {
        return choose(parent, title, null, FileDialog.LOAD);
    }

    private static Path choose(Component parent, String title, String name, int mode) {
        Window w = owner(parent);
        FileDialog fd = w instanceof Frame f ? new FileDialog(f, title, mode)
                : w instanceof java.awt.Dialog dlg ? new FileDialog(dlg, title, mode) : new FileDialog((Frame) null, title, mode);
        if (name != null) fd.setFile(name);
        fd.setVisible(true);
        if (fd.getFile() == null) return null;
        return Path.of(fd.getDirectory(), fd.getFile());
    }
}
