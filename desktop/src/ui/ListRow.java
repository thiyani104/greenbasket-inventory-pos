package ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** One line in a dashboard list: title and subtitle on the left, a value or badge on the right. */
public class ListRow extends JPanel {

    private boolean hover;

    public ListRow(String title, String subtitle, JComponent right, Runnable onClick) {
        super(new BorderLayout(10, 0));
        setOpaque(false);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER_SOFT),
                BorderFactory.createEmptyBorder(8, 6, 8, 6)));
        JPanel text = UI.transparent(new BorderLayout(0, 1));
        JLabel t = UI.label(title, Font.BOLD, 13, Theme.TEXT);
        text.add(t, BorderLayout.NORTH);
        if (subtitle != null && !subtitle.isEmpty()) text.add(UI.muted(subtitle), BorderLayout.CENTER);
        add(text, BorderLayout.CENTER);
        if (right != null) {
            JPanel r = UI.transparent(new java.awt.GridBagLayout());
            r.add(right);
            add(r, BorderLayout.EAST);
        }
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
        if (onClick != null) {
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                @Override public void mouseClicked(MouseEvent e) { onClick.run(); }
            });
        }
    }

    public static JLabel value(String text, Color color) {
        return UI.label(text, Font.BOLD, 13, color);
    }

    @Override
    protected void paintComponent(java.awt.Graphics g) {
        if (hover) {
            g.setColor(new Color(0xF4F8F5));
            g.fillRoundRect(0, 0, getWidth(), getHeight() - 1, 10, 10);
        }
        super.paintComponent(g);
    }

    /** A vertical stack of rows that shows a friendly message when empty. */
    public static JPanel stack(String emptyText, java.util.List<? extends JComponent> rows) {
        JPanel p = UI.transparent(null);
        p.setLayout(new javax.swing.BoxLayout(p, javax.swing.BoxLayout.Y_AXIS));
        if (rows.isEmpty()) {
            JLabel l = UI.muted(emptyText);
            l.setBorder(BorderFactory.createEmptyBorder(24, 6, 24, 6));
            l.setAlignmentX(0);
            p.add(l);
        }
        for (JComponent r : rows) {
            r.setAlignmentX(0);
            p.add(r);
        }
        p.add(javax.swing.Box.createVerticalGlue());
        return p;
    }
}
