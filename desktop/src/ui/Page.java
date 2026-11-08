package ui;

import java.awt.BorderLayout;
import javax.swing.JPanel;

/** A screen shown inside the main window's content area. */
public abstract class Page extends JPanel {

    protected Page() {
        super(new BorderLayout(0, 16));
        setOpaque(false);
        setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 28, 24, 28));
    }

    public abstract String getTitle();

    public String getSubtitle() { return ""; }

    /** Reload data from the services and repaint. */
    public abstract void refresh();

    /** Called every time the page is navigated to. */
    public void onShow() { refresh(); }
}
