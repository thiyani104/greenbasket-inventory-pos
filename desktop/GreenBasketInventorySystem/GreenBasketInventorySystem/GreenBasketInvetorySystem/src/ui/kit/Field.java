package ui.kit;

import java.awt.*;
import java.awt.event.*;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import javax.swing.text.JTextComponent;

/** Rounded text field with placeholder, optional leading icon and an "invalid" state. */
public class Field extends JTextField {

    private String hint = "";
    private String icon;
    private boolean invalid;

    public Field() { this(""); }

    public Field(String hint) {
        this.hint = hint;
        init(this);
    }

    public Field icon(String name) { this.icon = name; init(this); return this; }
    public Field hint(String h) { this.hint = h; repaint(); return this; }
    public void setInvalid(boolean b) { this.invalid = b; repaint(); }

    static void init(JTextComponent c) {
        c.setOpaque(false);
        c.setFont(Theme.font(13.5f));
        c.setForeground(Theme.text);
        c.setCaretColor(Theme.primary);
        c.setSelectionColor(Theme.mix(Theme.primary, Theme.surface, 0.72));
        c.setSelectedTextColor(Theme.text);
        int left = (c instanceof Field && ((Field) c).icon != null) ? 38 : 12;
        c.setBorder(new EmptyBorder(9, left, 9, c instanceof PassField ? 38 : 12));
        c.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) { c.repaint(); }
            @Override public void focusLost(FocusEvent e) { c.repaint(); }
        });
    }

    @Override public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        return new Dimension(Math.max(d.width, 120), 40);
    }

    @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, 40); }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = Gfx.aa(g);
        paintBack(this, g2, invalid, icon);
        g2.dispose();
        super.paintComponent(g);
        if (getText().isEmpty() && hint != null && !hint.isEmpty()) {
            Graphics2D h = Gfx.aa(g);
            Insets in = getInsets();
            Gfx.textMid(h, hint, in.left, 0, getHeight(), getFont(), Theme.faint);
            h.dispose();
        }
    }

    static void paintBack(JTextComponent c, Graphics2D g2, boolean invalid, String icon) {
        int w = c.getWidth(), h = c.getHeight();
        boolean focus = c.isFocusOwner();
        Color bg = c.isEnabled() ? Theme.surface : Theme.surfaceAlt;
        Gfx.fillRound(g2, 0, 0, w, h, 8, bg);
        if (invalid) Gfx.strokeRound(g2, 0, 0, w, h, 8, Theme.danger, 1.6f);
        else if (focus) {
            Gfx.strokeRound(g2, 0, 0, w, h, 8, Theme.alpha(Theme.primary, 70), 3.5f);
            Gfx.strokeRound(g2, 0, 0, w, h, 8, Theme.primary, 1.4f);
        } else Gfx.strokeRound(g2, 0, 0, w, h, 8, Theme.borderStrong, 1f);
        if (icon != null) Icons.paint(g2, icon, 12, (h - 16) / 2.0, 16, focus ? Theme.primary : Theme.faint, 1.9f);
    }
}
