package ui.kit;

import java.awt.*;
import javax.swing.*;

/** Short non-blocking notifications in the corner of the window. */
public final class Toast {

    public enum Kind { SUCCESS, ERROR, INFO, WARN }

    private static JComponent current;

    private Toast() { }

    public static void show(Component anchor, String message, Kind kind) {
        JRootPane root = anchor == null ? null : SwingUtilities.getRootPane(anchor);
        if (root == null) return;
        JLayeredPane lp = root.getLayeredPane();
        if (current != null && current.getParent() != null) current.getParent().remove(current);

        Color accent = kind == Kind.SUCCESS ? Theme.success : kind == Kind.ERROR ? Theme.danger : kind == Kind.WARN ? Theme.warning : Theme.info;
        String icon = kind == Kind.SUCCESS ? "check" : kind == Kind.ERROR ? "close" : kind == Kind.WARN ? "alert" : "clock";
        float[] alpha = {0f};

        JComponent c = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Gfx.aa(g);
                g2.setComposite(AlphaComposite.SrcOver.derive(Math.max(0f, Math.min(1f, alpha[0]))));
                int w = getWidth(), h = getHeight();
                Gfx.fillRound(g2, 0, 3, w, h - 3, 10, new Color(0, 0, 0, 40));
                Gfx.fillRound(g2, 0, 0, w, h - 3, 10, Theme.dark ? Theme.surfaceAlt : new Color(0x14201A));
                Gfx.fillRound(g2, 12, (h - 3 - 24) / 2.0, 24, 24, 12, accent);
                Icons.paint(g2, icon, 17, (h - 3 - 14) / 2.0, 14, Color.WHITE, 2.4f);
                Font f = Theme.font(13);
                FontMetrics fm = g2.getFontMetrics(f);
                Gfx.text(g2, message, 48, (h - 3 - fm.getHeight()) / 2.0 + fm.getAscent(), f, Color.WHITE);
                g2.dispose();
            }
        };
        FontMetrics fm = c.getFontMetrics(Theme.font(13));
        int w = Math.min(fm.stringWidth(message) + 72, Math.max(240, root.getWidth() - 40));
        int h = 52;
        c.setSize(w, h);
        c.setLocation(root.getWidth() - w - 24, root.getHeight() - h - 24);
        lp.add(c, JLayeredPane.POPUP_LAYER);
        current = c;

        int[] tick = {0};
        Timer t = new Timer(16, null);
        t.addActionListener(e -> {
            tick[0]++;
            if (tick[0] < 12) alpha[0] = tick[0] / 12f;
            else if (tick[0] < 12 + 170) alpha[0] = 1f;
            else if (tick[0] < 12 + 170 + 14) alpha[0] = 1f - (tick[0] - 182) / 14f;
            else {
                t.stop();
                if (c.getParent() != null) { Container p = c.getParent(); p.remove(c); p.repaint(); }
                return;
            }
            c.repaint();
        });
        t.start();
    }

    public static void ok(Component a, String m) { show(a, m, Kind.SUCCESS); }
    public static void err(Component a, String m) { show(a, m, Kind.ERROR); }
    public static void info(Component a, String m) { show(a, m, Kind.INFO); }
}
