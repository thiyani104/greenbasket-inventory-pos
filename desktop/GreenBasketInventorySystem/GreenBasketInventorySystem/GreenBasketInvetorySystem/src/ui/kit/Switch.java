package ui.kit;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import javax.swing.JComponent;

/** On/off toggle. */
public class Switch extends JComponent {

    private boolean on;
    private Consumer<Boolean> listener = b -> { };

    public Switch(boolean on) {
        this.on = on;
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { setOn(!Switch.this.on); listener.accept(Switch.this.on); }
        });
    }

    public Switch onChange(Consumer<Boolean> l) { listener = l; return this; }
    public boolean isOn() { return on; }
    public void setOn(boolean b) { on = b; repaint(); }

    @Override public Dimension getPreferredSize() { return new Dimension(44, 26); }
    @Override public Dimension getMaximumSize() { return getPreferredSize(); }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = Gfx.aa(g);
        Gfx.fillRound(g2, 0, 1, 44, 24, 12, on ? Theme.primary : Theme.borderStrong);
        g2.setColor(Color.WHITE);
        g2.fillOval(on ? 22 : 3, 4, 18, 18);
        g2.dispose();
    }
}
