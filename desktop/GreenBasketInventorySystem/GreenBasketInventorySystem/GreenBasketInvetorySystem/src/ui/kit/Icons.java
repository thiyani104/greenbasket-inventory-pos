package ui.kit;

import java.awt.*;
import java.awt.geom.*;
import javax.swing.Icon;

/** Crisp vector icons drawn on a 24x24 grid - no image files needed, and they follow the theme colours. */
public final class Icons {

    private Icons() { }

    public static Icon icon(String name, int size, Color color) {
        return new Icon() {
            @Override public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                paint(g2, name, x, y, size, color, 1.8f);
                g2.dispose();
            }
            @Override public int getIconWidth() { return size; }
            @Override public int getIconHeight() { return size; }
        };
    }

    public static void paint(Graphics2D g, String name, double x, double y, double size, Color color, float stroke) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        double s = size / 24.0;
        g.translate(x, y);
        g.scale(s, s);
        g.setColor(color);
        g.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (name) {
            case "dashboard":
                rr(g, 3.5, 3.5, 7, 8); rr(g, 13.5, 3.5, 7, 5); rr(g, 13.5, 11.5, 7, 9); rr(g, 3.5, 14.5, 7, 6);
                break;
            case "cart":
                line(g, 2.5, 4, 5, 4); line(g, 5, 4, 7.2, 14.5); line(g, 7.2, 14.5, 18.5, 14.5);
                line(g, 6, 7.5, 20.5, 7.5); line(g, 20.5, 7.5, 18.5, 14.5);
                dot(g, 9, 19.5, 1.5); dot(g, 17, 19.5, 1.5);
                break;
            case "box":
                path(g, 12, 2.8, 20.2, 7.2, 20.2, 16.8, 12, 21.2, 3.8, 16.8, 3.8, 7.2, 12, 2.8);
                line(g, 3.8, 7.2, 12, 11.6); line(g, 20.2, 7.2, 12, 11.6); line(g, 12, 11.6, 12, 21.2);
                break;
            case "truck":
                rr(g, 2.5, 6, 11, 9); path(g, 13.5, 9.5, 18, 9.5, 21.5, 12.8, 21.5, 15, 13.5, 15);
                dot(g, 7, 17.5, 2); dot(g, 17.5, 17.5, 2);
                break;
            case "receipt":
                path(g, 5, 2.8, 19, 2.8, 19, 21.2, 16, 19.2, 13.5, 21.2, 11, 19.2, 8.5, 21.2, 5, 19.2, 5, 2.8);
                line(g, 8.5, 8, 15.5, 8); line(g, 8.5, 12, 15.5, 12);
                break;
            case "chart":
                line(g, 4, 20, 20, 20); rr(g, 5.5, 11, 3, 9); rr(g, 10.5, 5, 3, 15); rr(g, 15.5, 14, 3, 6);
                break;
            case "users":
                g.draw(new Ellipse2D.Double(8.5, 3.5, 7, 7)); arc(g, 4.5, 14, 15, 14, 0, 180);
                line(g, 4.5, 20.5, 19.5, 20.5);
                break;
            case "user":
                g.draw(new Ellipse2D.Double(8, 3.8, 8, 8)); arc(g, 4.5, 14.5, 15, 14, 0, 180); line(g, 4.5, 21.5, 19.5, 21.5);
                break;
            case "activity":
                path(g, 2.5, 12.5, 7, 12.5, 9.5, 5, 13.5, 19.5, 16, 12.5, 21.5, 12.5);
                break;
            case "gear":
                g.draw(new Ellipse2D.Double(8.5, 8.5, 7, 7));
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI / 4 * i;
                    line(g, 12 + Math.cos(a) * 7.6, 12 + Math.sin(a) * 7.6, 12 + Math.cos(a) * 9.8, 12 + Math.sin(a) * 9.8);
                }
                g.draw(new Ellipse2D.Double(5.4, 5.4, 13.2, 13.2));
                break;
            case "logout":
                path(g, 10, 3.5, 4.5, 3.5, 4.5, 20.5, 10, 20.5); line(g, 9, 12, 20, 12); path(g, 16, 8, 20, 12, 16, 16);
                break;
            case "search":
                g.draw(new Ellipse2D.Double(4, 4, 11.5, 11.5)); line(g, 13.5, 13.5, 20, 20);
                break;
            case "plus":
                line(g, 12, 5, 12, 19); line(g, 5, 12, 19, 12);
                break;
            case "minus":
                line(g, 5, 12, 19, 12);
                break;
            case "edit":
                path(g, 4, 20, 4.8, 15.5, 16, 4.3, 19.7, 8, 8.5, 19.2, 4, 20); line(g, 13.8, 6.6, 17.4, 10.2);
                break;
            case "trash":
                line(g, 4, 6.5, 20, 6.5); path(g, 9, 6.5, 9, 3.8, 15, 3.8, 15, 6.5);
                path(g, 6, 6.5, 7, 20.5, 17, 20.5, 18, 6.5); line(g, 10, 10.5, 10, 17); line(g, 14, 10.5, 14, 17);
                break;
            case "download":
                line(g, 12, 3.5, 12, 15); path(g, 7.5, 10.8, 12, 15.3, 16.5, 10.8); path(g, 4, 16, 4, 20.5, 20, 20.5, 20, 16);
                break;
            case "upload":
                line(g, 12, 15.5, 12, 4); path(g, 7.5, 8.2, 12, 3.7, 16.5, 8.2); path(g, 4, 16, 4, 20.5, 20, 20.5, 20, 16);
                break;
            case "refresh":
                arc(g, 4.5, 4.5, 15, 15, 40, 270); path(g, 17.5, 3.5, 18.8, 8.2, 14.2, 8.8);
                break;
            case "alert":
                path(g, 12, 3.5, 21.3, 20, 2.7, 20, 12, 3.5); line(g, 12, 10, 12, 14.5); dot(g, 12, 17.4, 0.7);
                break;
            case "clock":
                g.draw(new Ellipse2D.Double(3.5, 3.5, 17, 17)); path(g, 12, 7.5, 12, 12, 15.5, 14);
                break;
            case "tag":
                path(g, 3.5, 3.5, 12.5, 3.5, 20.5, 11.5, 12.5, 19.5, 3.5, 10.5, 3.5, 3.5); dot(g, 8, 8, 1.4);
                break;
            case "check":
                path(g, 4.5, 12.5, 9.8, 17.8, 19.5, 7);
                break;
            case "close":
                line(g, 6, 6, 18, 18); line(g, 18, 6, 6, 18);
                break;
            case "eye":
                path(g, 2.5, 12, 7, 6.5, 12, 5.5, 17, 6.5, 21.5, 12, 17, 17.5, 12, 18.5, 7, 17.5, 2.5, 12);
                g.draw(new Ellipse2D.Double(9, 9, 6, 6));
                break;
            case "moon":
                Area moon = new Area(new Ellipse2D.Double(4, 3.5, 16.5, 16.5));
                moon.subtract(new Area(new Ellipse2D.Double(9.5, 0.5, 14, 14)));
                g.draw(moon);
                break;
            case "sun":
                g.draw(new Ellipse2D.Double(7.5, 7.5, 9, 9));
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI / 4 * i;
                    line(g, 12 + Math.cos(a) * 6.5, 12 + Math.sin(a) * 6.5, 12 + Math.cos(a) * 9, 12 + Math.sin(a) * 9);
                }
                break;
            case "key":
                g.draw(new Ellipse2D.Double(3.5, 8.5, 7, 7)); line(g, 10.5, 12, 20.5, 12); line(g, 17, 12, 17, 15.5); line(g, 20.5, 12, 20.5, 14.5);
                break;
            case "calendar":
                rr(g, 3.5, 5, 17, 15.5); line(g, 3.5, 10, 20.5, 10); line(g, 8, 3, 8, 7); line(g, 16, 3, 16, 7);
                break;
            case "print":
                path(g, 7, 9, 7, 3.5, 17, 3.5, 17, 9); rr(g, 3.5, 9, 17, 8); rr(g, 7, 14, 10, 6.5);
                break;
            case "history":
                arc(g, 4, 4, 16, 16, 20, 300); path(g, 3.2, 3.6, 4.2, 8.6, 9, 7.6); path(g, 12, 8, 12, 12, 15, 13.8);
                break;
            case "pause":
                line(g, 9, 5.5, 9, 18.5); line(g, 15, 5.5, 15, 18.5);
                break;
            case "folder":
                path(g, 3.5, 6.5, 3.5, 19.5, 20.5, 19.5, 20.5, 8.5, 11, 8.5, 9, 5.5, 3.5, 5.5, 3.5, 6.5);
                break;
            case "save":
                path(g, 4.5, 3.5, 17, 3.5, 20.5, 7, 20.5, 20.5, 4.5, 20.5, 4.5, 3.5); rr(g, 8, 3.5, 7, 5); rr(g, 7, 13, 10, 7.5);
                break;
            case "shield":
                path(g, 12, 3, 20, 6, 20, 12, 12, 21, 4, 12, 4, 6, 12, 3); path(g, 8.5, 12, 11, 14.5, 15.5, 9.5);
                break;
            case "star":
                path(g, 12, 3.5, 14.6, 9, 20.5, 9.7, 16, 13.8, 17.3, 19.7, 12, 16.7, 6.7, 19.7, 8, 13.8, 3.5, 9.7, 9.4, 9);
                break;
            case "database":
                g.draw(new Ellipse2D.Double(4.5, 3.5, 15, 5)); line(g, 4.5, 6, 4.5, 18); line(g, 19.5, 6, 19.5, 18);
                g.draw(new Arc2D.Double(4.5, 15.5, 15, 5, 180, 180, Arc2D.OPEN));
                g.draw(new Arc2D.Double(4.5, 9.5, 15, 5, 180, 180, Arc2D.OPEN));
                break;
            case "percent":
                line(g, 18.5, 5.5, 5.5, 18.5); g.draw(new Ellipse2D.Double(4.5, 4.5, 4.5, 4.5)); g.draw(new Ellipse2D.Double(15, 15, 4.5, 4.5));
                break;
            case "basket":
                path(g, 3, 9.5, 21, 9.5, 19, 19.5, 5, 19.5, 3, 9.5); path(g, 7.5, 9.5, 10.5, 3.8); path(g, 16.5, 9.5, 13.5, 3.8);
                line(g, 9, 13, 9, 16.5); line(g, 12, 13, 12, 16.5); line(g, 15, 13, 15, 16.5);
                break;
            case "leaf":
                path(g, 4, 20, 5, 12, 10, 5.5, 20, 4, 19, 13, 12.5, 18.5, 4, 20); line(g, 4, 20, 12, 12);
                break;
            default:
                g.draw(new Ellipse2D.Double(4, 4, 16, 16));
        }
        g.scale(1 / s, 1 / s);
        g.translate(-x, -y);
    }

    private static void line(Graphics2D g, double x1, double y1, double x2, double y2) { g.draw(new Line2D.Double(x1, y1, x2, y2)); }

    private static void rr(Graphics2D g, double x, double y, double w, double h) { g.draw(new RoundRectangle2D.Double(x, y, w, h, 2.4, 2.4)); }

    private static void dot(Graphics2D g, double cx, double cy, double r) { g.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2)); }

    private static void arc(Graphics2D g, double x, double y, double w, double h, double start, double ext) {
        g.draw(new Arc2D.Double(x, y, w, h, start, ext, Arc2D.OPEN));
    }

    private static void path(Graphics2D g, double... p) {
        Path2D.Double path = new Path2D.Double();
        path.moveTo(p[0], p[1]);
        for (int i = 2; i + 1 < p.length; i += 2) path.lineTo(p[i], p[i + 1]);
        g.draw(path);
    }
}
