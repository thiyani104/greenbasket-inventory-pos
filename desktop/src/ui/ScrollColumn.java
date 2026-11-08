package ui;

import java.awt.Dimension;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import javax.swing.JPanel;
import javax.swing.Scrollable;

/** A panel that always matches the width of its scroll pane, so content wraps instead of scrolling sideways. */
public class ScrollColumn extends JPanel implements Scrollable {
    private static final long serialVersionUID = 1L;

    public ScrollColumn(LayoutManager lm) {
        super(lm);
        setOpaque(false);
    }

    @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
    @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 20; }
    @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return 120; }
    @Override public boolean getScrollableTracksViewportWidth() { return true; }
    @Override public boolean getScrollableTracksViewportHeight() { return false; }
    @Override public Dimension getMinimumSize() { return new Dimension(0, super.getMinimumSize().height); }
}
