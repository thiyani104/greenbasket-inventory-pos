package ui.kit;

import java.awt.*;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/** White rounded surface with a hairline border. */
public class Card extends JPanel {

    private final int radius;

    public Card() { this(new BorderLayout(), 18); }

    public Card(LayoutManager lm, int pad) {
        super(lm);
        this.radius = 12;
        setOpaque(false);
        setBorder(new EmptyBorder(pad, pad, pad, pad));
    }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = Gfx.aa(g);
        if (!Theme.dark) Gfx.fillRound(g2, 0, 2, getWidth(), getHeight() - 1, radius, new Color(16, 40, 28, 10));
        Gfx.fillRound(g2, 0, 0, getWidth(), getHeight() - 1, radius, Theme.surface);
        Gfx.strokeRound(g2, 0, 0, getWidth(), getHeight() - 1, radius, Theme.border, 1f);
        g2.dispose();
        super.paintComponent(g);
    }
}
