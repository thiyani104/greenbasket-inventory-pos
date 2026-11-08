package ui.kit;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.*;
import java.util.List;
import javax.swing.JComponent;
import service.ReportService.Line;
import util.Fmt;

/** Lightweight charts drawn with Java2D: area, columns, ranked bars and a donut. */
public final class Charts {

    private Charts() { }

    static double niceMax(double v) {
        if (v <= 0) return 1;
        double exp = Math.pow(10, Math.floor(Math.log10(v)));
        double f = v / exp;
        double nice = f <= 1 ? 1 : f <= 2 ? 2 : f <= 2.5 ? 2.5 : f <= 5 ? 5 : 10;
        return nice * exp;
    }

    private static void tooltip(Graphics2D g, int cx, int top, int maxW, String l1, String l2, int boundW) {
        Font b = Theme.bold(12), n = Theme.font(11.5f);
        FontMetrics fb = g.getFontMetrics(b), fn = g.getFontMetrics(n);
        int w = Math.max(fb.stringWidth(l1), l2 == null ? 0 : fn.stringWidth(l2)) + 20;
        int h = l2 == null ? 30 : 48;
        int x = Math.max(4, Math.min(boundW - w - 4, cx - w / 2));
        int y = Math.max(2, top);
        Gfx.fillRound(g, x, y, w, h, 8, Theme.dark ? Theme.surfaceAlt : new Color(0x14201A));
        Gfx.text(g, l1, x + 10, y + 19, b, Color.WHITE);
        if (l2 != null) Gfx.text(g, l2, x + 10, y + 37, n, new Color(0xC4D8CC));
    }

    private static String emptyMsg = "No sales in this period yet";

    // ------------------------------------------------------------------ area

    public static class Area extends JComponent {
        private List<Line> data = java.util.Collections.emptyList();
        private int hover = -1;
        private final Color color;
        private final String extraLabel;

        public Area(Color color, String extraLabel) {
            this.color = color;
            this.extraLabel = extraLabel;
            setPreferredSize(new Dimension(300, 260));
            MouseAdapter m = new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent e) { hover = idx(e.getX()); repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = -1; repaint(); }
            };
            addMouseMotionListener(m);
            addMouseListener(m);
        }

        public void setData(List<Line> d) { data = d; hover = -1; repaint(); }

        private int idx(int x) {
            if (data.size() < 2) return data.isEmpty() ? -1 : 0;
            double l = 52, r = getWidth() - 14;
            int i = (int) Math.round((x - l) / (r - l) * (data.size() - 1));
            return Math.max(0, Math.min(data.size() - 1, i));
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = Gfx.aa(g);
            int w = getWidth(), h = getHeight();
            double max = 0;
            for (Line l : data) max = Math.max(max, l.value);
            if (data.isEmpty() || max <= 0) {
                Font f = Theme.font(13);
                FontMetrics fm = g2.getFontMetrics(f);
                Gfx.text(g2, emptyMsg, (w - fm.stringWidth(emptyMsg)) / 2.0, h / 2.0, f, Theme.faint);
                g2.dispose();
                return;
            }
            double top = niceMax(max);
            int l = 52, r = w - 14, t = 12, b = h - 30;
            Font small = Theme.font(11);
            for (int i = 0; i <= 4; i++) {
                double y = b - (b - t) * i / 4.0;
                g2.setColor(i == 0 ? Theme.borderStrong : Theme.border);
                g2.setStroke(new BasicStroke(1f));
                g2.draw(new Line2D.Double(l, y, r, y));
                String lab = Fmt.compact(top * i / 4);
                FontMetrics fm = g2.getFontMetrics(small);
                Gfx.text(g2, lab, l - 8 - fm.stringWidth(lab), y + 4, small, Theme.faint);
            }
            int n = data.size();
            double[] xs = new double[n], ys = new double[n];
            for (int i = 0; i < n; i++) {
                xs[i] = n == 1 ? (l + r) / 2.0 : l + (r - l) * i / (n - 1.0);
                ys[i] = b - (b - t) * (data.get(i).value / top);
            }
            Path2D.Double line = new Path2D.Double(), area = new Path2D.Double();
            line.moveTo(xs[0], ys[0]);
            area.moveTo(xs[0], b);
            area.lineTo(xs[0], ys[0]);
            for (int i = 1; i < n; i++) {
                double cx = (xs[i - 1] + xs[i]) / 2;
                line.curveTo(cx, ys[i - 1], cx, ys[i], xs[i], ys[i]);
                area.curveTo(cx, ys[i - 1], cx, ys[i], xs[i], ys[i]);
            }
            area.lineTo(xs[n - 1], b);
            area.closePath();
            g2.setPaint(new GradientPaint(0, t, Theme.alpha(color, 70), 0, b, Theme.alpha(color, 4)));
            g2.fill(area);
            g2.setPaint(null);
            g2.setColor(color);
            g2.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(line);
            // x labels
            int step = Math.max(1, (int) Math.ceil(n / Math.max(1.0, (r - l) / 62.0)));
            FontMetrics fm = g2.getFontMetrics(small);
            for (int i = 0; i < n; i += step) {
                String s = data.get(i).label;
                Gfx.text(g2, s, xs[i] - fm.stringWidth(s) / 2.0, h - 9, small, Theme.faint);
            }
            if (n <= 16) {
                for (int i = 0; i < n; i++) {
                    g2.setColor(Theme.surface);
                    g2.fill(new Ellipse2D.Double(xs[i] - 3.5, ys[i] - 3.5, 7, 7));
                    g2.setColor(color);
                    g2.setStroke(new BasicStroke(2f));
                    g2.draw(new Ellipse2D.Double(xs[i] - 3.5, ys[i] - 3.5, 7, 7));
                }
            }
            if (hover >= 0 && hover < n) {
                g2.setColor(Theme.alpha(color, 90));
                g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{4, 4}, 0));
                g2.draw(new Line2D.Double(xs[hover], t, xs[hover], b));
                g2.setColor(color);
                g2.fill(new Ellipse2D.Double(xs[hover] - 5, ys[hover] - 5, 10, 10));
                g2.setColor(Theme.surface);
                g2.fill(new Ellipse2D.Double(xs[hover] - 2.5, ys[hover] - 2.5, 5, 5));
                Line d = data.get(hover);
                tooltip(g2, (int) xs[hover], (int) ys[hover] - 62, 160, d.label + "  -  " + Fmt.money(d.value),
                        extraLabel == null ? null : (int) d.extra + " " + extraLabel, w);
            }
            g2.dispose();
        }
    }

    // --------------------------------------------------------------- columns

    public static class Columns extends JComponent {
        private List<Line> data = java.util.Collections.emptyList();
        private int hover = -1;
        private final Color color;
        private final String extraLabel;

        public Columns(Color color, String extraLabel) {
            this.color = color;
            this.extraLabel = extraLabel;
            setPreferredSize(new Dimension(300, 220));
            MouseAdapter m = new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent e) {
                    int n = data.size();
                    if (n == 0) return;
                    double l = 44, r = getWidth() - 10;
                    int i = (int) ((e.getX() - l) / ((r - l) / n));
                    i = e.getX() < l || e.getX() > r ? -1 : Math.min(n - 1, i);
                    if (i != hover) { hover = i; repaint(); }
                }
                @Override public void mouseExited(MouseEvent e) { hover = -1; repaint(); }
            };
            addMouseMotionListener(m);
            addMouseListener(m);
        }

        public void setData(List<Line> d) { data = d; hover = -1; repaint(); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = Gfx.aa(g);
            int w = getWidth(), h = getHeight();
            double max = 0;
            for (Line l : data) max = Math.max(max, l.value);
            if (data.isEmpty() || max <= 0) {
                Font f = Theme.font(13);
                FontMetrics fm = g2.getFontMetrics(f);
                Gfx.text(g2, emptyMsg, (w - fm.stringWidth(emptyMsg)) / 2.0, h / 2.0, f, Theme.faint);
                g2.dispose();
                return;
            }
            double top = niceMax(max);
            int l = 44, r = w - 10, t = 10, b = h - 28;
            Font small = Theme.font(11);
            FontMetrics fm = g2.getFontMetrics(small);
            for (int i = 0; i <= 3; i++) {
                double y = b - (b - t) * i / 3.0;
                g2.setColor(i == 0 ? Theme.borderStrong : Theme.border);
                g2.draw(new Line2D.Double(l, y, r, y));
                String lab = Fmt.compact(top * i / 3);
                Gfx.text(g2, lab, l - 8 - fm.stringWidth(lab), y + 4, small, Theme.faint);
            }
            int n = data.size();
            double slot = (r - l) / (double) n, bw = Math.min(26, slot * 0.62);
            int step = Math.max(1, (int) Math.ceil(n / Math.max(1.0, (r - l) / 34.0)));
            for (int i = 0; i < n; i++) {
                double v = data.get(i).value;
                double bh = (b - t) * (v / top);
                double x = l + slot * i + (slot - bw) / 2;
                if (v > 0) {
                    Color c = i == hover ? Theme.primaryHover : color;
                    g2.setColor(c);
                    g2.fill(new RoundRectangle2D.Double(x, b - bh, bw, Math.max(bh, 3), 6, 6));
                    g2.fillRect((int) x, (int) (b - Math.min(bh, 6)), (int) bw, (int) Math.min(bh, 6)); // flat base
                }
                if (i % step == 0) {
                    String s = data.get(i).label;
                    Gfx.text(g2, s, x + bw / 2 - fm.stringWidth(s) / 2.0, h - 9, small, Theme.faint);
                }
            }
            if (hover >= 0 && hover < n) {
                Line d = data.get(hover);
                double cx = l + slot * hover + slot / 2;
                double bh = (b - t) * (d.value / top);
                tooltip(g2, (int) cx, (int) (b - bh) - 56, 150, d.label + "  -  " + Fmt.money(d.value),
                        extraLabel == null ? null : (int) d.extra + " " + extraLabel, w);
            }
            g2.dispose();
        }
    }

    // ------------------------------------------------------------ ranked bars

    public static class Ranks extends JComponent {
        private List<Line> data = java.util.Collections.emptyList();
        private final String unit;
        private final boolean money;

        public Ranks(String unit, boolean money) {
            this.unit = unit;
            this.money = money;
        }

        public void setData(List<Line> d) { data = d; revalidate(); repaint(); }

        @Override public Dimension getPreferredSize() { return new Dimension(260, Math.max(60, data.size() * 38 + 4)); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = Gfx.aa(g);
            int w = getWidth();
            if (data.isEmpty()) {
                Font f = Theme.font(13);
                FontMetrics fm = g2.getFontMetrics(f);
                Gfx.text(g2, "Nothing to rank yet", (w - fm.stringWidth("Nothing to rank yet")) / 2.0, 36, f, Theme.faint);
                g2.dispose();
                return;
            }
            double max = 0;
            for (Line l : data) max = Math.max(max, l.value);
            Font lf = Theme.font(12.5f), vf = Theme.bold(12.5f), sf = Theme.font(11);
            FontMetrics fl = g2.getFontMetrics(lf), fv = g2.getFontMetrics(vf);
            for (int i = 0; i < data.size(); i++) {
                Line d = data.get(i);
                int y = i * 38;
                String val = money ? Fmt.money(d.value) : String.valueOf((int) d.value);
                String sub = unit == null ? "" : (int) d.extra + " " + unit;
                int vw = fv.stringWidth(val);
                Gfx.text(g2, Gfx.ellipsize(d.label, fl, w - vw - 16), 0, y + 13, lf, Theme.text);
                Gfx.text(g2, val, w - vw, y + 13, vf, Theme.text);
                double bw = Math.max(4, (w - 0) * (d.value / max));
                Gfx.fillRound(g2, 0, y + 20, w, 7, 3.5, Theme.mix(Theme.surface, Theme.border, 0.8));
                Gfx.fillRound(g2, 0, y + 20, bw, 7, 3.5, Theme.chart[i % Theme.chart.length]);
                if (!sub.isEmpty()) {
                    FontMetrics fs = g2.getFontMetrics(sf);
                    // sub-label sits at the right under the value, only if there is room
                    Gfx.text(g2, sub, w - fs.stringWidth(sub), y + 36, sf, Theme.faint);
                }
            }
            g2.dispose();
        }
    }

    // ----------------------------------------------------------------- donut

    public static class Donut extends JComponent {
        private List<Line> data = java.util.Collections.emptyList();
        private final String centerLabel;
        private int hover = -1;

        public Donut(String centerLabel) {
            this.centerLabel = centerLabel;
            setPreferredSize(new Dimension(300, 200));
            addMouseMotionListener(new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent e) {
                    int i = legendAt(e.getY());
                    if (i != hover) { hover = i; repaint(); }
                }
            });
            addMouseListener(new MouseAdapter() {
                @Override public void mouseExited(MouseEvent e) { hover = -1; repaint(); }
            });
        }

        public void setData(List<Line> d) { data = d; hover = -1; repaint(); }

        private int legendAt(int y) {
            int rows = Math.min(data.size(), 7);
            int startY = (getHeight() - rows * 26) / 2;
            int i = (y - startY) / 26;
            return y >= startY && i >= 0 && i < rows ? i : -1;
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = Gfx.aa(g);
            int w = getWidth(), h = getHeight();
            double total = 0;
            for (Line l : data) total += l.value;
            if (data.isEmpty() || total <= 0) {
                Font f = Theme.font(13);
                FontMetrics fm = g2.getFontMetrics(f);
                Gfx.text(g2, emptyMsg, (w - fm.stringWidth(emptyMsg)) / 2.0, h / 2.0, f, Theme.faint);
                g2.dispose();
                return;
            }
            int size = Math.min(h - 8, Math.min(180, (int) (w * 0.42)));
            double cx = size / 2.0 + 4, cy = h / 2.0;
            double thick = size * 0.17;
            double angle = 90;
            for (int i = 0; i < data.size(); i++) {
                double sweep = -360.0 * data.get(i).value / total;
                boolean hot = i == hover;
                double inset = hot ? -3 : 0;
                g2.setColor(Theme.chart[i % Theme.chart.length]);
                g2.setStroke(new BasicStroke((float) (thick + (hot ? 5 : 0)), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
                double gap = data.size() > 1 ? 1.2 : 0;
                g2.draw(new Arc2D.Double(cx - size / 2.0 + thick / 2 - inset, cy - size / 2.0 + thick / 2 - inset,
                        size - thick + inset * 2, size - thick + inset * 2, angle, sweep + (sweep < 0 ? gap : 0), Arc2D.OPEN));
                angle += sweep;
            }
            String big = hover >= 0 ? String.format("%.0f%%", data.get(hover).value / total * 100) : Fmt.compact(total);
            String small = hover >= 0 ? data.get(hover).label : centerLabel;
            Font bf = Theme.bold(size / 7.5f + 6), sf = Theme.font(11.5f);
            FontMetrics fb = g2.getFontMetrics(bf), fs = g2.getFontMetrics(sf);
            Gfx.text(g2, big, cx - fb.stringWidth(big) / 2.0, cy + 2, bf, Theme.text);
            String sm = Gfx.ellipsize(small, fs, (int) (size - thick * 2 - 8));
            Gfx.text(g2, sm, cx - fs.stringWidth(sm) / 2.0, cy + 20, sf, Theme.muted);

            int lx = size + 28;
            int rows = Math.min(data.size(), 7);
            int startY = (h - rows * 26) / 2;
            Font lf = Theme.font(12.5f), vf = Theme.bold(12.5f);
            FontMetrics fl = g2.getFontMetrics(lf), fv = g2.getFontMetrics(vf);
            for (int i = 0; i < rows; i++) {
                int y = startY + i * 26;
                if (i == hover) Gfx.fillRound(g2, lx - 8, y, w - lx - 4, 24, 6, Theme.hover);
                Gfx.fillRound(g2, lx, y + 8, 9, 9, 3, Theme.chart[i % Theme.chart.length]);
                String pct = String.format("%.0f%%", data.get(i).value / total * 100);
                Gfx.text(g2, pct, w - 8 - fv.stringWidth(pct), y + 17, vf, Theme.text);
                Gfx.text(g2, Gfx.ellipsize(data.get(i).label, fl, w - lx - 28 - fv.stringWidth(pct) - 12), lx + 17, y + 17, lf, Theme.text);
            }
            g2.dispose();
        }
    }
}
