package ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import util.Formats;

/** Simple vertical bar chart with gridlines, hover highlight and a value tooltip. */
public class BarChart extends JComponent {

    private List<String> labels = new ArrayList<>();
    private List<Double> values = new ArrayList<>();
    private Color color = Theme.PRIMARY;
    private int hover = -1;
    private String emptyText = "No data for this period";

    public BarChart() {
        setPreferredSize(new Dimension(400, 240));
        setOpaque(false);
        MouseAdapter m = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                int h = indexAt(e.getX());
                if (h != hover) { hover = h; repaint(); }
            }
            @Override public void mouseExited(MouseEvent e) { hover = -1; repaint(); }
        };
        addMouseMotionListener(m);
        addMouseListener(m);
    }

    public void setData(List<String> labels, List<Double> values) {
        this.labels = labels;
        this.values = values;
        hover = -1;
        repaint();
    }

    public void setColor(Color c) { color = c; }

    public void setEmptyText(String t) { emptyText = t; }

    private static final int LEFT = 54, RIGHT = 8, TOP = 14, BOTTOM = 28;

    private int indexAt(int x) {
        int n = values.size();
        if (n == 0) return -1;
        double slot = (getWidth() - LEFT - RIGHT) / (double) n;
        int i = (int) ((x - LEFT) / slot);
        return x < LEFT || i < 0 || i >= n ? -1 : i;
    }

    private static double niceMax(double max) {
        if (max <= 0) return 1;
        double exp = Math.pow(10, Math.floor(Math.log10(max)));
        double f = max / exp;
        double nice = f <= 1 ? 1 : f <= 2 ? 2 : f <= 2.5 ? 2.5 : f <= 5 ? 5 : 10;
        return nice * exp;
    }

    static String compact(double v) {
        double a = Math.abs(v);
        if (a >= 1_000_000) return trim(v / 1_000_000) + "M";
        if (a >= 1_000) return trim(v / 1_000) + "k";
        return trim(v);
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(java.util.Locale.US, "%.1f", v);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        int cw = w - LEFT - RIGHT, ch = h - TOP - BOTTOM;
        g2.setFont(Theme.font(java.awt.Font.PLAIN, 11));
        FontMetrics fm = g2.getFontMetrics();

        double max = 0;
        for (double v : values) max = Math.max(max, v);
        if (values.isEmpty() || max <= 0) {
            g2.setColor(Theme.MUTED);
            g2.setFont(Theme.font(java.awt.Font.PLAIN, 13));
            fm = g2.getFontMetrics();
            g2.drawString(emptyText, (w - fm.stringWidth(emptyText)) / 2, h / 2);
            g2.dispose();
            return;
        }
        double top = niceMax(max);
        int ticks = 4;
        for (int i = 0; i <= ticks; i++) {
            int y = TOP + ch - (int) (ch * i / (double) ticks);
            g2.setColor(i == 0 ? Theme.BORDER : Theme.BORDER_SOFT);
            g2.drawLine(LEFT, y, w - RIGHT, y);
            String t = compact(top * i / ticks);
            g2.setColor(Theme.MUTED);
            g2.drawString(t, LEFT - 8 - fm.stringWidth(t), y + fm.getAscent() / 2 - 1);
        }
        int n = values.size();
        double slot = cw / (double) n;
        double bw = Math.max(4, Math.min(44, slot * 0.62));
        int step = (int) Math.max(1, Math.ceil(n / Math.max(1.0, cw / 52.0)));
        for (int i = 0; i < n; i++) {
            double v = values.get(i);
            double bh = ch * v / top;
            double x = LEFT + slot * i + (slot - bw) / 2;
            double y = TOP + ch - bh;
            Color c = i == hover ? Theme.PRIMARY_DARK : color;
            g2.setColor(c);
            if (bh > 0.5) {
                double r = Math.min(6, bw / 2);
                Path2D.Double p = new Path2D.Double();
                p.append(new RoundRectangle2D.Double(x, y, bw, bh + r, r * 2, r * 2), false);
                g2.setClip(new java.awt.geom.Rectangle2D.Double(x - 1, y - 1, bw + 2, bh + 1));
                g2.fill(new RoundRectangle2D.Double(x, y, bw, bh + r, r * 2, r * 2));
                g2.setClip(null);
            }
            if (i % step == 0 || i == n - 1 && (n - 1) % step == 0) {
                String l = labels.get(i);
                g2.setColor(i == hover ? Theme.TEXT : Theme.MUTED);
                g2.drawString(l, (int) (LEFT + slot * i + slot / 2 - fm.stringWidth(l) / 2.0), h - 9);
            }
        }
        if (hover >= 0) {
            double v = values.get(hover);
            String t1 = labels.get(hover);
            String t2 = Formats.money(v);
            g2.setFont(Theme.font(java.awt.Font.BOLD, 12));
            FontMetrics bf = g2.getFontMetrics();
            int tw = Math.max(bf.stringWidth(t1), bf.stringWidth(t2)) + 20;
            int th = 44;
            double bh = ch * v / top;
            int cx = (int) (LEFT + slot * hover + slot / 2);
            int tx = Math.max(LEFT, Math.min(w - tw - 2, cx - tw / 2));
            int ty = (int) Math.max(2, TOP + ch - bh - th - 8);
            g2.setColor(new Color(0x1B2B24));
            g2.fillRoundRect(tx, ty, tw, th, 10, 10);
            g2.setColor(new Color(0xB9CFC3));
            g2.setFont(Theme.font(java.awt.Font.PLAIN, 11));
            g2.drawString(t1, tx + 10, ty + 17);
            g2.setColor(Color.WHITE);
            g2.setFont(Theme.font(java.awt.Font.BOLD, 12));
            g2.drawString(t2, tx + 10, ty + 34);
        }
        g2.dispose();
    }
}
