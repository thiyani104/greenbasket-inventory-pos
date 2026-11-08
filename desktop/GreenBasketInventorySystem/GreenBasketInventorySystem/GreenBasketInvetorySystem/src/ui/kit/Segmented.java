package ui.kit;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.IntConsumer;
import javax.swing.JComponent;

/** Pill-shaped single-choice selector (like a tab strip or radio group). */
public class Segmented extends JComponent {

    private final String[] options;
    private int selected = 0;
    private int hover = -1;
    private IntConsumer listener = i -> { };
    private final boolean compact;

    public Segmented(String... options) { this(false, options); }

    public Segmented(boolean compact, String... options) {
        this.options = options;
        this.compact = compact;
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setFont(Theme.bold(compact ? 12 : 13));
        MouseAdapter m = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) { int i = at(e.getX()); if (i != hover) { hover = i; repaint(); } }
            @Override public void mouseExited(MouseEvent e) { hover = -1; repaint(); }
            @Override public void mousePressed(MouseEvent e) {
                if (!isEnabled()) return;
                int i = at(e.getX());
                if (i >= 0 && i != selected) { selected = i; repaint(); listener.accept(i); }
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
    }

    public Segmented onChange(IntConsumer l) { this.listener = l; return this; }
    public int getSelected() { return selected; }
    public void setSelected(int i) { selected = i; repaint(); }
    public String selectedLabel() { return options[selected]; }

    private int segWidth(int i) { return getFontMetrics(getFont()).stringWidth(options[i]) + (compact ? 24 : 32); }

    private int at(int x) {
        int cx = 3;
        for (int i = 0; i < options.length; i++) {
            int w = segWidth(i);
            if (x >= cx && x < cx + w) return i;
            cx += w;
        }
        return -1;
    }

    @Override public Dimension getPreferredSize() {
        int w = 6;
        for (int i = 0; i < options.length; i++) w += segWidth(i);
        return new Dimension(w, compact ? 34 : 40);
    }

    @Override public Dimension getMaximumSize() { return getPreferredSize(); }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = Gfx.aa(g);
        int h = getHeight();
        Gfx.fillRound(g2, 0, 0, getWidth(), h, 9, Theme.mix(Theme.surface, Theme.border, 0.55));
        int x = 3;
        FontMetrics fm = g2.getFontMetrics(getFont());
        for (int i = 0; i < options.length; i++) {
            int w = segWidth(i);
            if (i == selected) {
                Gfx.fillRound(g2, x, 3, w, h - 6, 7, Theme.surface);
                Gfx.strokeRound(g2, x, 3, w, h - 6, 7, Theme.border, 1f);
            } else if (i == hover) Gfx.fillRound(g2, x, 3, w, h - 6, 7, Theme.alpha(Theme.surface, 120));
            Gfx.text(g2, options[i], x + (w - fm.stringWidth(options[i])) / 2.0, (h - fm.getHeight()) / 2.0 + fm.getAscent(),
                    getFont(), i == selected ? (Theme.dark ? Theme.primaryHover : Theme.primaryDark) : Theme.muted);
            x += w;
        }
        g2.dispose();
    }
}
