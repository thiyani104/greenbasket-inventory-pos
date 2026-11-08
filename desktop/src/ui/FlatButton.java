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
import javax.swing.Icon;
import javax.swing.JButton;

/** A fully custom-painted rounded button (primary / secondary / danger / ghost / dark). */
public class FlatButton extends JButton {

    public enum Style { PRIMARY, SECONDARY, DANGER, GHOST, DARK, SIDE }

    private final Style style;
    private boolean hover;
    private boolean pressed;
    private int radius = 10;
    private int padX = 16;
    private int padY = 10;
    private Icons.Kind iconKind;

    public FlatButton(String text, Style style) {
        super(text);
        this.style = style;
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setRolloverEnabled(true);
        setFont(Theme.font(java.awt.Font.BOLD, 13));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setHorizontalAlignment(CENTER);
        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
            @Override public void mouseExited(MouseEvent e) { hover = false; pressed = false; repaint(); }
            @Override public void mousePressed(MouseEvent e) { pressed = true; repaint(); }
            @Override public void mouseReleased(MouseEvent e) { pressed = false; repaint(); }
        });
        addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent e) { repaint(); }
            @Override public void focusLost(java.awt.event.FocusEvent e) { repaint(); }
        });
    }

    public static FlatButton primary(String t) { return new FlatButton(t, Style.PRIMARY); }
    public static FlatButton secondary(String t) { return new FlatButton(t, Style.SECONDARY); }
    public static FlatButton danger(String t) { return new FlatButton(t, Style.DANGER); }
    public static FlatButton ghost(String t) { return new FlatButton(t, Style.GHOST); }
    public static FlatButton dark(String t) { return new FlatButton(t, Style.SIDE); }

    public FlatButton icon(Icons.Kind kind) {
        this.iconKind = kind;
        return this;
    }

    public FlatButton compact() {
        padX = 12;
        padY = 7;
        radius = 8;
        setFont(Theme.font(java.awt.Font.BOLD, 12));
        return this;
    }

    private Color fg() {
        if (!isEnabled()) return style == Style.PRIMARY || style == Style.DANGER || style == Style.DARK || style == Style.SIDE ? Color.WHITE : new Color(0xA7B3AD);
        return switch (style) {
            case PRIMARY, DANGER, DARK, SIDE -> Color.WHITE;
            case SECONDARY -> Theme.TEXT;
            case GHOST -> Theme.PRIMARY_DARK;
        };
    }

    private Color bg() {
        if (!isEnabled()) {
            return switch (style) {
                case PRIMARY -> new Color(0x9FCDB5);
                case DANGER -> new Color(0xE9AFAF);
                case DARK, SIDE -> new Color(0x8A9A92);
                default -> new Color(0xF1F4F2);
            };
        }
        return switch (style) {
            case PRIMARY -> pressed ? new Color(0x105C3A) : hover ? Theme.PRIMARY_DARK : Theme.PRIMARY;
            case DANGER -> pressed ? new Color(0x9E2D2D) : hover ? new Color(0xB93A3A) : Theme.DANGER;
            case DARK -> pressed ? new Color(0x07170F) : hover ? new Color(0x18412F) : Theme.SIDEBAR;
            case SIDE -> pressed ? new Color(0x0B2118) : hover ? new Color(0x255C43) : Theme.SIDEBAR_HOVER;
            case SECONDARY -> pressed ? new Color(0xE3EAE6) : hover ? new Color(0xF0F5F2) : Color.WHITE;
            case GHOST -> pressed ? new Color(0xD3EBDD) : hover ? Theme.PRIMARY_LIGHT : new Color(0, 0, 0, 0);
        };
    }

    private Icon currentIcon() {
        if (iconKind == null) return getIcon();
        return Icons.of(iconKind, 16, fg());
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        Icon ic = currentIcon();
        String t = getText() == null ? "" : getText();
        int w = fm.stringWidth(t) + padX * 2;
        if (ic != null) w += ic.getIconWidth() + (t.isEmpty() ? 0 : 8);
        int h = Math.max(fm.getHeight() + padY * 2, ic == null ? 0 : ic.getIconHeight() + padY * 2);
        return new Dimension(w, h);
    }

    @Override
    public Dimension getMinimumSize() { return getPreferredSize(); }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        g2.setColor(bg());
        g2.fillRoundRect(0, 0, w, h, radius * 2, radius * 2);
        if (style == Style.SECONDARY) {
            g2.setColor(isEnabled() ? Theme.BORDER : Theme.BORDER_SOFT);
            g2.drawRoundRect(0, 0, w - 1, h - 1, radius * 2, radius * 2);
        }
        if (hasFocus() && !hover && isEnabled()) {
            g2.setColor(Theme.alpha(Theme.PRIMARY, 120));
            g2.drawRoundRect(1, 1, w - 3, h - 3, radius * 2 - 2, radius * 2 - 2);
        }
        FontMetrics fm = g2.getFontMetrics(getFont());
        Icon ic = currentIcon();
        String t = getText() == null ? "" : getText();
        int iw = ic == null ? 0 : ic.getIconWidth();
        int gap = (ic != null && !t.isEmpty()) ? 8 : 0;
        int contentW = iw + gap + fm.stringWidth(t);
        int x = (w - contentW) / 2;
        if (ic != null) {
            ic.paintIcon(this, g2, x, (h - ic.getIconHeight()) / 2);
            x += iw + gap;
        }
        g2.setFont(getFont());
        g2.setColor(fg());
        g2.drawString(t, x, (h - fm.getHeight()) / 2 + fm.getAscent());
        g2.dispose();
    }
}
