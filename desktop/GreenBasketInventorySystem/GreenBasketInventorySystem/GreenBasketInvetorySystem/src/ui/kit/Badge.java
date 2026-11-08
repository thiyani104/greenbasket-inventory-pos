package ui.kit;

import java.awt.*;
import javax.swing.JComponent;

/** Small coloured status pill. */
public class Badge extends JComponent {

    private String text;
    private Color fg, bg;

    public Badge(String text) { set(text); }

    public void set(String t) {
        this.text = t == null ? "" : t;
        Color[] c = colors(text);
        fg = c[0];
        bg = c[1];
        revalidate();
        repaint();
    }

    public void tone(Color fg, Color bg) { this.fg = fg; this.bg = bg; repaint(); }

    /** {foreground, background} for a status word. */
    public static Color[] colors(String t) {
        String s = t == null ? "" : t.toUpperCase();
        switch (s) {
            case "OK": case "IN STOCK": case "COMPLETED": case "RECEIVED": case "ACTIVE": case "GOLD": case "RESTOCK": case "SALE":
                return new Color[]{Theme.success, Theme.successSoft};
            case "LOW": case "ORDERED": case "SILVER": case "EXPIRING": case "EXPIRING SOON": case "CORRECTION": case "OPENING":
                return new Color[]{Theme.warning, Theme.warningSoft};
            case "OUT": case "VOIDED": case "CANCELLED": case "EXPIRED": case "INACTIVE": case "WRITE_OFF": case "VOID": case "OUT OF STOCK":
                return new Color[]{Theme.danger, Theme.dangerSoft};
            case "STORE MANAGER": case "MANAGER": case "CARD": case "REGULAR":
                return new Color[]{Theme.info, Theme.infoSoft};
            default:
                return new Color[]{Theme.muted, Theme.mix(Theme.surface, Theme.border, 0.7)};
        }
    }

    public static Dimension size(FontMetrics fm, String t) { return new Dimension(fm.stringWidth(t) + 20, 22); }

    /** Draws a pill at x,y (top-left) and returns its width. */
    public static int paint(Graphics2D g, String t, int x, int y, Color fg, Color bg) {
        Font f = Theme.bold(11);
        FontMetrics fm = g.getFontMetrics(f);
        int w = fm.stringWidth(t) + 20;
        Gfx.fillRound(g, x, y, w, 22, 11, bg);
        Gfx.text(g, t, x + 10, y + (22 - fm.getHeight()) / 2.0 + fm.getAscent(), f, fg);
        return w;
    }

    @Override public Dimension getPreferredSize() {
        return size(getFontMetrics(Theme.bold(11)), text);
    }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = Gfx.aa(g);
        paint(g2, text, 0, (getHeight() - 22) / 2, fg, bg);
        g2.dispose();
    }
}
