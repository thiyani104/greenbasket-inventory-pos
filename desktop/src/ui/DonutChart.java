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
import java.awt.geom.Arc2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleFunction;
import javax.swing.JComponent;
import model.Category;

/** Donut chart with a legend; hovering a slice (or legend row) shows its value in the centre. */
public class DonutChart extends JComponent {

    private List<String> labels = new ArrayList<>();
    private List<Double> values = new ArrayList<>();
    private DoubleFunction<String> format = v -> String.valueOf(Math.round(v));
    private String centerCaption = "Total";
    private int hover = -1;
    private String emptyText = "No data yet";

    public DonutChart() {
        setPreferredSize(new Dimension(400, 240));
        setOpaque(false);
        MouseAdapter m = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                int i = hit(e.getX(), e.getY());
                if (i != hover) { hover = i; repaint(); }
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

    public void setFormat(DoubleFunction<String> f) { format = f; }

    public void setCenterCaption(String c) { centerCaption = c; }

    public void setEmptyText(String t) { emptyText = t; }

    private double total() {
        double t = 0;
        for (double v : values) t += v;
        return t;
    }

    private int ringSize() { return Math.max(60, Math.min(getHeight() - 8, getWidth() / 2)); }

    private int originX() { return 4; }

    private int originY() { return (getHeight() - ringSize()) / 2; }

    private static Color colorAt(List<String> labels, int i) {
        return Category.colorFor(labels.get(i));
    }

    private int hit(int x, int y) {
        int s = ringSize();
        double cx = originX() + s / 2.0, cy = originY() + s / 2.0;
        double dx = x - cx, dy = y - cy;
        double dist = Math.sqrt(dx * dx + dy * dy);
        double outer = s / 2.0, inner = outer * 0.62;
        if (dist >= inner && dist <= outer && total() > 0) {
            double ang = Math.toDegrees(Math.atan2(-dy, dx));
            double fromTop = (90 - ang + 360) % 360;
            double acc = 0, t = total();
            for (int i = 0; i < values.size(); i++) {
                acc += values.get(i) / t * 360;
                if (fromTop <= acc) return i;
            }
        }
        // legend rows
        int lx = originX() + s + 24;
        int rows = Math.min(values.size(), maxRows());
        int ly = (getHeight() - rows * 26) / 2;
        if (x >= lx && y >= ly && y < ly + rows * 26) return (y - ly) / 26;
        return -1;
    }

    private int maxRows() { return Math.max(1, (getHeight() - 8) / 26); }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        double t = total();
        if (values.isEmpty() || t <= 0) {
            g2.setColor(Theme.MUTED);
            g2.setFont(Theme.font(java.awt.Font.PLAIN, 13));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(emptyText, (w - fm.stringWidth(emptyText)) / 2, h / 2);
            g2.dispose();
            return;
        }
        int s = ringSize();
        int ox = originX(), oy = originY();
        double stroke = s * 0.19;
        double start = 90;
        for (int i = 0; i < values.size(); i++) {
            double extent = -values.get(i) / t * 360;
            g2.setColor(colorAt(labels, i));
            double sw = i == hover ? stroke + 6 : stroke;
            g2.setStroke(new BasicStroke((float) sw, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
            double inset = stroke / 2 + 3 - (i == hover ? 3 : 0);
            double gap = values.size() > 1 ? 1.2 : 0;
            g2.draw(new Arc2D.Double(ox + inset, oy + inset, s - inset * 2, s - inset * 2,
                    start - gap / 2, extent + gap, Arc2D.OPEN));
            start += extent;
        }
        // centre text
        String big = hover >= 0 ? format.apply(values.get(hover)) : format.apply(t);
        String small = hover >= 0 ? labels.get(hover) : centerCaption;
        g2.setFont(Theme.font(java.awt.Font.BOLD, 15));
        FontMetrics fm = g2.getFontMetrics();
        int cx = ox + s / 2, cy = oy + s / 2;
        g2.setColor(Theme.TEXT);
        g2.drawString(big, cx - fm.stringWidth(big) / 2, cy + 2);
        g2.setFont(Theme.font(java.awt.Font.PLAIN, 11));
        fm = g2.getFontMetrics();
        g2.setColor(Theme.MUTED);
        String sm = small.length() > 16 ? small.substring(0, 15) + "…" : small;
        g2.drawString(sm, cx - fm.stringWidth(sm) / 2, cy + 18);
        // legend
        int lx = ox + s + 24;
        int rows = Math.min(values.size(), maxRows());
        int ly = (h - rows * 26) / 2;
        for (int i = 0; i < rows; i++) {
            int y = ly + i * 26;
            if (i == hover) {
                g2.setColor(new Color(0xF1F6F3));
                g2.fillRoundRect(lx - 8, y, w - lx - 4, 24, 8, 8);
            }
            g2.setColor(colorAt(labels, i));
            g2.fillRoundRect(lx, y + 7, 10, 10, 4, 4);
            g2.setFont(Theme.font(i == hover ? java.awt.Font.BOLD : java.awt.Font.PLAIN, 12));
            fm = g2.getFontMetrics();
            String pct = String.format(java.util.Locale.US, "%.0f%%", values.get(i) / t * 100);
            int maxLabel = w - lx - 52;
            String lab = labels.get(i);
            while (fm.stringWidth(lab) > maxLabel && lab.length() > 3) lab = lab.substring(0, lab.length() - 2) + "…";
            g2.setColor(Theme.TEXT);
            g2.drawString(lab, lx + 18, y + 16);
            g2.setColor(Theme.MUTED);
            g2.drawString(pct, w - 8 - fm.stringWidth(pct), y + 16);
        }
        g2.dispose();
    }
}
