package ui.kit;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** Small layout/label helpers used by every page. */
public final class Ui {

    private Ui() { }

    public static JLabel label(String text, Font f, Color c) {
        JLabel l = new JLabel(text);
        l.setFont(f);
        l.setForeground(c);
        return l;
    }

    public static JLabel h1(String t) { return label(t, Theme.bold(24), Theme.text); }
    public static JLabel h2(String t) { return label(t, Theme.bold(15), Theme.text); }
    public static JLabel muted(String t) { return label(t, Theme.font(12.5f), Theme.muted); }

    /** Label whose text wraps at {@code widthPx} (Swing only honours width on the html body). */
    public static JLabel wrapped(String htmlSafeText, int widthPx, Font f, Color c) {
        JLabel l = new JLabel("<html><body style='width:" + widthPx + "px'>" + htmlSafeText + "</body></html>");
        l.setFont(f);
        l.setForeground(c);
        return l;
    }

    public static JPanel transparent(LayoutManager lm) {
        JPanel p = new JPanel(lm);
        p.setOpaque(false);
        return p;
    }

    public static JPanel row(int gap, Component... cs) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, gap, 0));
        p.setOpaque(false);
        for (Component c : cs) p.add(c);
        return p;
    }

    /** Page header: big title + subtitle on the left, actions on the right. */
    public static JComponent header(String title, String subtitle, JComponent actions) {
        JPanel p = transparent(new BorderLayout());
        p.setBorder(new EmptyBorder(0, 0, 18, 0));
        JPanel left = transparent(null);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        JLabel t = h1(title);
        t.setAlignmentX(0);
        left.add(t);
        if (subtitle != null) {
            left.add(Box.createVerticalStrut(3));
            JLabel s = muted(subtitle);
            s.setAlignmentX(0);
            left.add(s);
        }
        p.add(left, BorderLayout.WEST);
        if (actions != null) p.add(actions, BorderLayout.EAST);
        return p;
    }

    /** Titled card section: heading (and optional right-hand component) above content. */
    public static Card section(String title, String subtitle, JComponent content, JComponent right) {
        Card c = new Card(new BorderLayout(0, 12), 18);
        JPanel head = transparent(new BorderLayout());
        JPanel left = transparent(null);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        JLabel t = h2(title);
        t.setAlignmentX(0);
        left.add(t);
        if (subtitle != null) {
            JLabel s = muted(subtitle);
            s.setAlignmentX(0);
            left.add(Box.createVerticalStrut(2));
            left.add(s);
        }
        head.add(left, BorderLayout.WEST);
        if (right != null) head.add(right, BorderLayout.EAST);
        c.add(head, BorderLayout.NORTH);
        c.add(content, BorderLayout.CENTER);
        return c;
    }

    /** Equal-width columns with a gap. */
    public static JPanel columns(int gap, Component... cs) {
        JPanel p = new JPanel(new GridLayout(1, cs.length, gap, 0));
        p.setOpaque(false);
        for (Component c : cs) p.add(c);
        return p;
    }

    /** Side-by-side cards sharing the width by the given proportions. */
    public static JPanel split(int gap, double[] weights, JComponent... cards) {
        JPanel p = transparent(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.BOTH;
        gc.weighty = 1;
        for (int i = 0; i < cards.length; i++) {
            gc.gridx = i;
            gc.weightx = weights[i];
            gc.insets = new Insets(0, 0, 0, i == cards.length - 1 ? 0 : gap);
            cards[i].setPreferredSize(new Dimension((int) (weights[i] * 1000), 100));
            p.add(cards[i], gc);
        }
        return p;
    }

    /** Fixes a row's height (plus a gap below) inside a vertical BoxLayout; it still grows to the full width. */
    public static JComponent rowOf(JComponent c, int height, int gapBelow) {
        JPanel w = transparent(new BorderLayout());
        w.add(c);
        w.setBorder(new EmptyBorder(0, 0, gapBelow, 0));
        w.setAlignmentX(0);
        w.setPreferredSize(new Dimension(100, height + gapBelow));
        w.setMinimumSize(new Dimension(100, height + gapBelow));
        w.setMaximumSize(new Dimension(Integer.MAX_VALUE, height + gapBelow));
        return w;
    }

    /** Scroll pane for a vertical stack of rows that fills the width and keeps the page background. */
    public static JScrollPane stackScroll(JComponent stack) {
        JPanel wrap = transparent(new BorderLayout());
        wrap.add(stack, BorderLayout.NORTH);
        JScrollPane sp = Tables.slimScroll(wrap);
        sp.getViewport().setOpaque(false);
        sp.setOpaque(false);
        sp.getVerticalScrollBar().setUnitIncrement(24);
        sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        return sp;
    }

    public static JComponent vgap(int h) { return (JComponent) Box.createVerticalStrut(h); }

    /** Round avatar with initials. */
    public static JComponent avatar(String name, int size) {
        return new JComponent() {
            @Override public Dimension getPreferredSize() { return new Dimension(size, size); }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = Gfx.aa(g);
                g2.setColor(Theme.primarySoft);
                g2.fillOval(0, 0, size, size);
                String[] parts = name.trim().split("\\s+");
                String ini = parts.length >= 2 ? "" + parts[0].charAt(0) + parts[parts.length - 1].charAt(0) : name.substring(0, Math.min(2, name.length()));
                ini = ini.toUpperCase();
                Font f = Theme.bold(size * 0.38f);
                FontMetrics fm = g2.getFontMetrics(f);
                Gfx.text(g2, ini, (size - fm.stringWidth(ini)) / 2.0, (size - fm.getHeight()) / 2.0 + fm.getAscent(), f,
                        Theme.dark ? Theme.primaryHover : Theme.primaryDark);
                g2.dispose();
            }
        };
    }

    /** Scrollable page body with consistent padding. */
    public static JScrollPane pageScroll(JComponent content) {
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBackground(Theme.bg);
        wrap.setBorder(new EmptyBorder(26, 30, 30, 30));
        wrap.add(content, BorderLayout.NORTH);
        JScrollPane sp = Tables.slimScroll(wrap);
        sp.getViewport().setOpaque(true);
        sp.getViewport().setBackground(Theme.bg);
        sp.setOpaque(true);
        sp.setBackground(Theme.bg);
        sp.getVerticalScrollBar().setUnitIncrement(24);
        return sp;
    }
}
