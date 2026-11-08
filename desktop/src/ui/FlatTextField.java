package ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import javax.swing.BorderFactory;
import javax.swing.JTextField;
import javax.swing.text.JTextComponent;

/** Rounded text field with placeholder text and an error state. */
public class FlatTextField extends JTextField {

    private String placeholder = "";
    private boolean error;
    private Icons.Kind iconKind;

    public FlatTextField() { this(20); }

    public FlatTextField(int columns) {
        super(columns);
        init(this);
    }

    static void init(JTextComponent c) {
        c.setOpaque(false);
        c.setBorder(BorderFactory.createEmptyBorder(9, 12, 9, 12));
        c.setFont(Theme.font(java.awt.Font.PLAIN, 13));
        c.setForeground(Theme.TEXT);
        c.setCaretColor(Theme.PRIMARY_DARK);
        c.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) { c.repaint(); }
            @Override public void focusLost(FocusEvent e) { c.repaint(); }
        });
    }

    public FlatTextField placeholder(String p) {
        this.placeholder = p;
        return this;
    }

    /** Shows a magnifier at the left (used for search boxes). */
    public FlatTextField searchIcon() {
        this.iconKind = Icons.Kind.SEARCH;
        setBorder(BorderFactory.createEmptyBorder(9, 36, 9, 12));
        return this;
    }

    public void setError(boolean error) {
        this.error = error;
        repaint();
    }

    static void paintFrame(JTextComponent c, Graphics g, boolean error) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = c.getWidth(), h = c.getHeight();
        g2.setColor(c.isEnabled() && c.isEditable() ? Color.WHITE : new Color(0xF3F6F4));
        g2.fillRoundRect(0, 0, w - 1, h - 1, 20, 20);
        Color border = error ? Theme.DANGER : c.hasFocus() ? Theme.PRIMARY : Theme.BORDER;
        g2.setColor(border);
        g2.drawRoundRect(0, 0, w - 1, h - 1, 20, 20);
        if (c.hasFocus()) {
            g2.setColor(Theme.alpha(error ? Theme.DANGER : Theme.PRIMARY, 50));
            g2.drawRoundRect(1, 1, w - 3, h - 3, 18, 18);
        }
        g2.dispose();
    }

    static void paintPlaceholder(JTextComponent c, Graphics g, String placeholder) {
        if (placeholder == null || placeholder.isEmpty() || c.getDocument().getLength() > 0) return;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setColor(new Color(0x9AA8A1));
        g2.setFont(c.getFont());
        FontMetrics fm = g2.getFontMetrics();
        java.awt.Insets in = c.getInsets();
        g2.drawString(placeholder, in.left, (c.getHeight() - fm.getHeight()) / 2 + fm.getAscent());
        g2.dispose();
    }

    @Override
    protected void paintComponent(Graphics g) {
        paintFrame(this, g, error);
        super.paintComponent(g);
        paintPlaceholder(this, g, placeholder);
        if (iconKind != null) {
            Icons.of(iconKind, 16, new Color(0x8A9A92)).paintIcon(this, g, 12, (getHeight() - 16) / 2);
        }
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        d.height = Math.max(d.height, 38);
        return d;
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(60, 38);
    }
}
