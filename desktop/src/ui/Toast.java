package ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** Small non-blocking notification that fades away on its own. */
public final class Toast {

    private static JComponent current;

    private Toast() {}

    public static void show(Component anchor, String text, Dialogs.Kind kind) {
        JRootPane root = anchor == null ? null : SwingUtilities.getRootPane(anchor);
        if (root == null) return;
        JLayeredPane layer = root.getLayeredPane();
        if (current != null && current.getParent() != null) {
            current.getParent().remove(current);
        }
        Color accent = switch (kind) {
            case SUCCESS -> Theme.PRIMARY;
            case ERROR -> Theme.DANGER;
            case WARNING -> Theme.WARN;
            case INFO -> Theme.INFO;
        };
        JLabel label = UI.label(text, java.awt.Font.BOLD, 13, Color.WHITE);
        label.setBorder(javax.swing.BorderFactory.createEmptyBorder(11, 38, 11, 18));
        JComponent toast = new JComponent() {
            { setLayout(new java.awt.BorderLayout()); add(label); setOpaque(false); }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0, 0, 0, 40));
                g2.fillRoundRect(1, 3, getWidth() - 2, getHeight() - 3, 26, 26);
                g2.setColor(new Color(0x1B2B24));
                g2.fillRoundRect(0, 0, getWidth(), getHeight() - 2, 26, 26);
                g2.setColor(accent);
                g2.fillOval(14, getHeight() / 2 - 6, 10, 10);
                g2.dispose();
            }
        };
        Dimension d = toast.getPreferredSize();
        toast.setBounds((layer.getWidth() - d.width) / 2, layer.getHeight() - d.height - 36, d.width, d.height + 2);
        layer.add(toast, JLayeredPane.POPUP_LAYER);
        layer.repaint();
        current = toast;
        Timer t = new Timer(2800, e -> {
            if (toast.getParent() != null) {
                java.awt.Container parent = toast.getParent();
                parent.remove(toast);
                parent.repaint();
            }
        });
        t.setRepeats(false);
        t.start();
    }

    public static void success(Component anchor, String text) { show(anchor, text, Dialogs.Kind.SUCCESS); }

    public static void error(Component anchor, String text) { show(anchor, text, Dialogs.Kind.ERROR); }
}
