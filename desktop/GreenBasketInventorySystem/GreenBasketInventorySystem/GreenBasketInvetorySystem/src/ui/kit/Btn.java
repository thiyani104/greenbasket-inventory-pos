package ui.kit;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JButton;

/** Flat rounded button with hover/pressed feedback and an optional vector icon. */
public class Btn extends JButton {

    public enum Kind { PRIMARY, SECONDARY, GHOST, DANGER, SOFT }

    private final Kind kind;
    private String iconName;
    private boolean hover;
    private boolean compact;

    public Btn(String text, Kind kind) { this(text, null, kind); }

    public Btn(String text, String icon, Kind kind) {
        super(text);
        this.kind = kind;
        this.iconName = icon;
        setOpaque(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setRolloverEnabled(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setFont(Theme.bold(13));
        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
            @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
        });
    }

    public Btn compact() { this.compact = true; setFont(Theme.bold(12)); return this; }

    public void setIconName(String n) { this.iconName = n; revalidate(); repaint(); }

    @Override public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        String t = getText();
        int w = (t == null || t.isEmpty() ? 0 : fm.stringWidth(t)) + (compact ? 24 : 32);
        if (iconName != null) w += (compact ? 16 : 20) + (t == null || t.isEmpty() ? -8 : 0);
        return new Dimension(Math.max(w, compact ? 34 : 40), compact ? 32 : 40);
    }

    @Override public Dimension getMinimumSize() { return getPreferredSize(); }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = Gfx.aa(g);
        int w = getWidth(), h = getHeight();
        boolean on = isEnabled();
        boolean pressed = getModel().isPressed() && on;
        Color bg, fg, line = null;
        switch (kind) {
            case PRIMARY:
                bg = pressed ? Theme.primaryDark : hover && on ? Theme.primaryHover : Theme.primary;
                fg = Theme.onPrimary;
                break;
            case DANGER:
                bg = pressed ? Theme.mix(Theme.danger, Color.BLACK, 0.25) : hover && on ? Theme.mix(Theme.danger, Color.BLACK, 0.1) : Theme.danger;
                fg = Color.WHITE;
                break;
            case SOFT:
                bg = pressed ? Theme.mix(Theme.primarySoft, Theme.primary, 0.25) : hover && on ? Theme.mix(Theme.primarySoft, Theme.primary, 0.12) : Theme.primarySoft;
                fg = Theme.dark ? Theme.primaryHover : Theme.primaryDark;
                break;
            case GHOST:
                bg = pressed ? Theme.border : hover && on ? Theme.hover : Theme.alpha(Theme.hover, 0);
                fg = Theme.text;
                break;
            default:
                bg = pressed ? Theme.hover : hover && on ? Theme.surfaceAlt : Theme.surface;
                fg = Theme.text;
                line = hover && on ? Theme.borderStrong : Theme.border;
        }
        if (!on) {
            bg = kind == Kind.GHOST || kind == Kind.SECONDARY ? Theme.alpha(Theme.surface, 0) : Theme.mix(Theme.surface, Theme.border, 0.7);
            fg = Theme.faint;
            if (kind == Kind.SECONDARY) line = Theme.border;
        }
        Gfx.fillRound(g2, 0, 0, w, h, 8, bg);
        if (line != null) Gfx.strokeRound(g2, 0, 0, w, h, 8, line, 1f);
        if (isFocusOwner() && on && !getModel().isPressed()) Gfx.strokeRound(g2, 0, 0, w, h, 8, Theme.alpha(Theme.primary, 120), 2f);

        FontMetrics fm = g2.getFontMetrics(getFont());
        String t = getText() == null ? "" : getText();
        int isz = compact ? 15 : 17;
        int tw = fm.stringWidth(t);
        int total = tw + (iconName != null ? isz + (t.isEmpty() ? 0 : 8) : 0);
        int x = (w - total) / 2;
        if (iconName != null) {
            Icons.paint(g2, iconName, x, (h - isz) / 2.0, isz, fg, 1.9f);
            x += isz + (t.isEmpty() ? 0 : 8);
        }
        Gfx.text(g2, t, x, (h - fm.getHeight()) / 2.0 + fm.getAscent(), getFont(), fg);
        g2.dispose();
    }
}
