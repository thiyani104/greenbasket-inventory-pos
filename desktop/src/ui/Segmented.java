package ui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;

/** A pill-shaped single-choice toggle group (tabs, period pickers, payment method). */
public class Segmented extends JComponent {

    private final String[] options;
    private int selected = 0;
    private int hover = -1;
    private Runnable onChange = () -> { };
    private int[] widths;

    public Segmented(String... options) {
        this.options = options;
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setFont(Theme.font(java.awt.Font.BOLD, 12));
        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int i = indexAt(e.getX());
                if (i >= 0 && i != selected) {
                    selected = i;
                    repaint();
                    onChange.run();
                }
            }
            @Override public void mouseExited(MouseEvent e) { hover = -1; repaint(); }
        });
        addMouseMotionListener(new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                int i = indexAt(e.getX());
                if (i != hover) { hover = i; repaint(); }
            }
        });
    }

    public Segmented onChange(Runnable r) {
        onChange = r;
        return this;
    }

    public int getSelectedIndex() { return selected; }

    public void setSelectedIndex(int i) {
        selected = i;
        repaint();
    }

    public String getSelectedText() { return options[selected]; }

    private void measure() {
        FontMetrics fm = getFontMetrics(getFont());
        widths = new int[options.length];
        for (int i = 0; i < options.length; i++) widths[i] = fm.stringWidth(options[i]) + 28;
    }

    private int indexAt(int x) {
        if (widths == null) measure();
        int cx = 3;
        for (int i = 0; i < widths.length; i++) {
            if (x >= cx && x < cx + widths[i]) return i;
            cx += widths[i];
        }
        return -1;
    }

    @Override
    public Dimension getPreferredSize() {
        measure();
        int w = 6;
        for (int x : widths) w += x;
        return new Dimension(w, 38);
    }

    @Override
    protected void paintComponent(Graphics g) {
        measure();
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int h = getHeight();
        g2.setColor(new Color(0xE8EEEA));
        g2.fillRoundRect(0, 0, getWidth(), h, 20, 20);
        g2.setFont(getFont());
        FontMetrics fm = g2.getFontMetrics();
        int x = 3;
        for (int i = 0; i < options.length; i++) {
            if (i == selected) {
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(x, 3, widths[i], h - 6, 16, 16);
                g2.setColor(Theme.BORDER);
                g2.drawRoundRect(x, 3, widths[i] - 1, h - 7, 16, 16);
            }
            g2.setColor(i == selected ? Theme.PRIMARY_DARK : i == hover ? Theme.TEXT : Theme.MUTED);
            g2.drawString(options[i], x + (widths[i] - fm.stringWidth(options[i])) / 2, (h - fm.getHeight()) / 2 + fm.getAscent());
            x += widths[i];
        }
        g2.dispose();
    }
}
