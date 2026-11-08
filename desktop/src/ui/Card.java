package ui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.JPanel;

/** White rounded panel with a hairline border; the basic building block of every screen. */
public class Card extends JPanel {

    private Color fill = Color.WHITE;
    private int radius = 14;

    public Card() { this(new java.awt.BorderLayout()); }

    public Card(LayoutManager lm) {
        super(lm);
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
    }

    public Card padding(int top, int left, int bottom, int right) {
        setBorder(BorderFactory.createEmptyBorder(top, left, bottom, right));
        return this;
    }

    public Card fill(Color c) {
        fill = c;
        return this;
    }

    public Card radius(int r) {
        radius = r;
        return this;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        g2.setColor(new Color(20, 50, 35, 10));
        g2.fillRoundRect(0, 2, w - 1, h - 2, radius * 2, radius * 2);
        g2.setColor(fill);
        g2.fillRoundRect(0, 0, w - 1, h - 2, radius * 2, radius * 2);
        g2.setColor(Theme.BORDER);
        g2.drawRoundRect(0, 0, w - 1, h - 3, radius * 2, radius * 2);
        g2.dispose();
        super.paintComponent(g);
    }
}
