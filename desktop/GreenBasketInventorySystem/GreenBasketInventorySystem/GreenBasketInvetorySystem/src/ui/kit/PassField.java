package ui.kit;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JPasswordField;

/** Password field with a show/hide eye. */
public class PassField extends JPasswordField {

    private final char echo;
    private boolean shown;
    private boolean invalid;
    private String hint = "";

    public PassField() {
        echo = '•';
        setEchoChar(echo);
        Field.init(this);
        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getX() > getWidth() - 36) {
                    shown = !shown;
                    setEchoChar(shown ? (char) 0 : echo);
                    repaint();
                }
            }
        });
        addMouseMotionListener(new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                setCursor(Cursor.getPredefinedCursor(e.getX() > getWidth() - 36 ? Cursor.HAND_CURSOR : Cursor.TEXT_CURSOR));
            }
        });
    }

    public PassField hint(String h) { this.hint = h; return this; }
    public void setInvalid(boolean b) { invalid = b; repaint(); }
    public String text() { return new String(getPassword()); }

    @Override public Dimension getPreferredSize() { return new Dimension(Math.max(super.getPreferredSize().width, 120), 40); }
    @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, 40); }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = Gfx.aa(g);
        Field.paintBack(this, g2, invalid, null);
        Icons.paint(g2, "eye", getWidth() - 28, (getHeight() - 16) / 2.0, 16, shown ? Theme.primary : Theme.faint, 1.8f);
        if (shown) {
            g2.setColor(Theme.primary);
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(getWidth() - 27, getHeight() / 2 + 7, getWidth() - 13, getHeight() / 2 - 7);
        }
        g2.dispose();
        super.paintComponent(g);
        if (getPassword().length == 0 && !hint.isEmpty()) {
            Graphics2D h = Gfx.aa(g);
            Gfx.textMid(h, hint, getInsets().left, 0, getHeight(), getFont(), Theme.faint);
            h.dispose();
        }
    }
}
