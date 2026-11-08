package ui.kit;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/** Tiny drawing helpers shared by the kit. */
public final class Gfx {

    private Gfx() { }

    public static Graphics2D aa(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        return g2;
    }

    public static void fillRound(Graphics2D g, double x, double y, double w, double h, double r, Color c) {
        g.setColor(c);
        g.fill(new RoundRectangle2D.Double(x, y, w, h, r * 2, r * 2));
    }

    public static void strokeRound(Graphics2D g, double x, double y, double w, double h, double r, Color c, float width) {
        g.setColor(c);
        g.setStroke(new BasicStroke(width));
        double o = width / 2.0;
        g.draw(new RoundRectangle2D.Double(x + o, y + o, w - width, h - width, r * 2, r * 2));
    }

    public static void text(Graphics2D g, String s, double x, double baseline, Font f, Color c) {
        g.setFont(f);
        g.setColor(c);
        g.drawString(s, (float) x, (float) baseline);
    }

    /** Draws {@code s} centred vertically inside the box at (x,y,w,h). */
    public static void textMid(Graphics2D g, String s, double x, double y, double h, Font f, Color c) {
        FontMetrics fm = g.getFontMetrics(f);
        text(g, s, x, y + (h - fm.getHeight()) / 2.0 + fm.getAscent(), f, c);
    }

    public static String ellipsize(String s, FontMetrics fm, int maxW) {
        if (s == null) return "";
        if (fm.stringWidth(s) <= maxW) return s;
        String dots = "...";
        int end = s.length();
        while (end > 1 && fm.stringWidth(s.substring(0, end) + dots) > maxW) end--;
        return s.substring(0, end).trim() + dots;
    }
}
