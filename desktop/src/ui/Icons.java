package ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.Icon;

/** Crisp vector icons drawn with Java2D, so there are no image files or font dependencies. */
public final class Icons {

    public enum Kind {
        DASHBOARD, PRODUCTS, CART, STOCK, RECEIPT, REPORT, USERS, ACTIVITY, SETTINGS, LOGOUT,
        SEARCH, PLUS, MINUS, WARNING, LEAF, CHECK, DOWNLOAD, UPLOAD, EYE, EYE_OFF, TRASH, EDIT, CLOCK, BOX_ALERT, CLOSE
    }

    private Icons() {}

    public static Icon of(Kind kind, int size, Color color) {
        return new VectorIcon(kind, size, color);
    }

    private static final class VectorIcon implements Icon {
        private final Kind kind;
        private final int size;
        private final Color color;

        VectorIcon(Kind kind, int size, Color color) {
            this.kind = kind;
            this.size = size;
            this.color = color;
        }

        @Override public int getIconWidth() { return size; }
        @Override public int getIconHeight() { return size; }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.translate(x, y);
            double s = size / 24.0;
            g2.scale(s, s);
            g2.setColor(color);
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            draw(g2);
            g2.dispose();
        }

        private void line(Graphics2D g, double x1, double y1, double x2, double y2) {
            g.draw(new Line2D.Double(x1, y1, x2, y2));
        }

        private void poly(Graphics2D g, boolean close, double... pts) {
            Path2D.Double p = new Path2D.Double();
            p.moveTo(pts[0], pts[1]);
            for (int i = 2; i < pts.length; i += 2) p.lineTo(pts[i], pts[i + 1]);
            if (close) p.closePath();
            g.draw(p);
        }

        private void draw(Graphics2D g) {
            switch (kind) {
                case DASHBOARD -> {
                    g.draw(new RoundRectangle2D.Double(3.5, 3.5, 6.5, 6.5, 2, 2));
                    g.draw(new RoundRectangle2D.Double(14, 3.5, 6.5, 6.5, 2, 2));
                    g.draw(new RoundRectangle2D.Double(3.5, 14, 6.5, 6.5, 2, 2));
                    g.draw(new RoundRectangle2D.Double(14, 14, 6.5, 6.5, 2, 2));
                }
                case PRODUCTS -> {
                    poly(g, true, 12, 2.5, 20.5, 7, 20.5, 17, 12, 21.5, 3.5, 17, 3.5, 7);
                    poly(g, false, 3.5, 7, 12, 12, 20.5, 7);
                    line(g, 12, 12, 12, 21.5);
                }
                case CART -> {
                    poly(g, false, 2.5, 3.5, 5.5, 3.5, 8, 15.5, 18.5, 15.5, 20.5, 7, 6, 7);
                    g.fill(new Ellipse2D.Double(7.5, 18.5, 3.2, 3.2));
                    g.fill(new Ellipse2D.Double(15.5, 18.5, 3.2, 3.2));
                }
                case STOCK -> {
                    line(g, 7, 20, 7, 4);
                    poly(g, false, 3, 8, 7, 4, 11, 8);
                    line(g, 17, 4, 17, 20);
                    poly(g, false, 13, 16, 17, 20, 21, 16);
                }
                case RECEIPT -> {
                    poly(g, true, 5, 2.5, 19, 2.5, 19, 21.5, 16, 19.5, 12, 21.5, 8, 19.5, 5, 21.5);
                    line(g, 9, 8, 15, 8);
                    line(g, 9, 12.5, 15, 12.5);
                }
                case REPORT -> {
                    line(g, 3, 21, 21, 21);
                    g.fill(new RoundRectangle2D.Double(5, 12, 3.6, 8, 1.2, 1.2));
                    g.fill(new RoundRectangle2D.Double(10.2, 5, 3.6, 15, 1.2, 1.2));
                    g.fill(new RoundRectangle2D.Double(15.4, 9, 3.6, 11, 1.2, 1.2));
                }
                case USERS -> {
                    g.draw(new Ellipse2D.Double(8, 3.5, 8, 8));
                    g.draw(new Arc2D.Double(4, 14, 16, 14, 0, 180, Arc2D.OPEN));
                }
                case ACTIVITY, CLOCK -> {
                    g.draw(new Ellipse2D.Double(3, 3, 18, 18));
                    poly(g, false, 12, 7, 12, 12, 15.5, 14);
                }
                case SETTINGS -> {
                    line(g, 3, 6, 21, 6);
                    line(g, 3, 12, 21, 12);
                    line(g, 3, 18, 21, 18);
                    Color old = g.getColor();
                    g.setColor(Color.WHITE);
                    g.fill(new Ellipse2D.Double(5.5, 3.5, 5, 5));
                    g.fill(new Ellipse2D.Double(13.5, 9.5, 5, 5));
                    g.fill(new Ellipse2D.Double(7.5, 15.5, 5, 5));
                    g.setColor(old);
                    g.draw(new Ellipse2D.Double(5.5, 3.5, 5, 5));
                    g.draw(new Ellipse2D.Double(13.5, 9.5, 5, 5));
                    g.draw(new Ellipse2D.Double(7.5, 15.5, 5, 5));
                }
                case LOGOUT -> {
                    poly(g, false, 9, 3.5, 4, 3.5, 4, 20.5, 9, 20.5);
                    line(g, 10, 12, 20, 12);
                    poly(g, false, 16, 8, 20, 12, 16, 16);
                }
                case SEARCH -> {
                    g.draw(new Ellipse2D.Double(3.5, 3.5, 12, 12));
                    line(g, 14, 14, 20.5, 20.5);
                }
                case PLUS -> {
                    line(g, 12, 5, 12, 19);
                    line(g, 5, 12, 19, 12);
                }
                case MINUS -> line(g, 5, 12, 19, 12);
                case CLOSE -> {
                    line(g, 6, 6, 18, 18);
                    line(g, 18, 6, 6, 18);
                }
                case WARNING -> {
                    poly(g, true, 12, 3.5, 21.5, 20, 2.5, 20);
                    line(g, 12, 9.5, 12, 14);
                    g.fill(new Ellipse2D.Double(11, 16, 2, 2));
                }
                case BOX_ALERT -> {
                    poly(g, true, 12, 2.5, 20.5, 7, 20.5, 17, 12, 21.5, 3.5, 17, 3.5, 7);
                    line(g, 12, 8, 12, 13);
                    g.fill(new Ellipse2D.Double(11, 15, 2, 2));
                }
                case LEAF -> {
                    Path2D.Double p = new Path2D.Double();
                    p.moveTo(4.5, 19.5);
                    p.curveTo(3.5, 9, 9.5, 4, 20, 4);
                    p.curveTo(20.5, 14.5, 15, 20.5, 4.5, 19.5);
                    p.closePath();
                    g.fill(p);
                    Color old = g.getColor();
                    g.setColor(Color.WHITE);
                    g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    line(g, 4.5, 19.5, 14, 10);
                    g.setColor(old);
                }
                case CHECK -> poly(g, false, 5, 12.5, 10, 17.5, 19, 7);
                case DOWNLOAD -> {
                    line(g, 12, 4, 12, 15);
                    poly(g, false, 7, 10.5, 12, 15.5, 17, 10.5);
                    line(g, 4.5, 20, 19.5, 20);
                }
                case UPLOAD -> {
                    line(g, 12, 16, 12, 5);
                    poly(g, false, 7, 9.5, 12, 4.5, 17, 9.5);
                    line(g, 4.5, 20, 19.5, 20);
                }
                case EYE, EYE_OFF -> {
                    Path2D.Double p = new Path2D.Double();
                    p.moveTo(2, 12);
                    p.quadTo(12, 3, 22, 12);
                    p.quadTo(12, 21, 2, 12);
                    g.draw(p);
                    g.draw(new Ellipse2D.Double(9, 9, 6, 6));
                    if (kind == Kind.EYE_OFF) line(g, 4, 4, 20, 20);
                }
                case TRASH -> {
                    line(g, 4, 6, 20, 6);
                    poly(g, false, 9, 6, 9, 3.5, 15, 3.5, 15, 6);
                    poly(g, false, 6, 6, 7, 21, 17, 21, 18, 6);
                }
                case EDIT -> {
                    poly(g, true, 4, 20, 5, 15, 16, 4, 20, 8, 9, 19);
                    line(g, 13, 7, 17, 11);
                }
            }
        }
    }
}
