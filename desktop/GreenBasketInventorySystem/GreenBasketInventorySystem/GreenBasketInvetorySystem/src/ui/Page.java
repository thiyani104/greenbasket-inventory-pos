package ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import ui.kit.Theme;
import ui.kit.Ui;

/** A screen inside the main window. {@link #refresh()} re-reads data from the services. */
public abstract class Page extends JPanel {

    protected final MainFrame host;

    protected Page(MainFrame host) {
        super(new BorderLayout());
        this.host = host;
        setBackground(Theme.bg);
    }

    /** Re-read everything this page shows. Called when the page becomes visible and after data changes. */
    public abstract void refresh();

    /** Called each time the user navigates to this page. */
    public void onShow() { refresh(); }

    /** Standard page frame: padded, header on top, body filling the rest (the body manages its own scrolling). */
    protected void shell(JComponent header, JComponent body) {
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.setBorder(new EmptyBorder(26, 30, 26, 30));
        wrap.add(header, BorderLayout.NORTH);
        wrap.add(body, BorderLayout.CENTER);
        add(wrap, BorderLayout.CENTER);
    }

    /** Right-aligned row of action buttons for {@link Ui#header}. */
    protected static JPanel actions(Component... cs) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        p.setOpaque(false);
        for (Component c : cs) p.add(c);
        return p;
    }

    /** A bar with controls on the left and on the right. */
    protected static JPanel bar(JComponent left, JComponent right) {
        JPanel p = new JPanel(new BorderLayout(12, 0));
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(0, 0, 14, 0));
        if (left != null) p.add(left, BorderLayout.WEST);
        if (right != null) p.add(right, BorderLayout.EAST);
        return p;
    }

    /** Horizontal group of controls with a gap, vertically centred. */
    protected static JPanel group(int gap, Component... cs) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new javax.swing.BoxLayout(p, javax.swing.BoxLayout.X_AXIS));
        for (int i = 0; i < cs.length; i++) {
            if (i > 0) p.add(javax.swing.Box.createHorizontalStrut(gap));
            if (cs[i] instanceof JComponent) ((JComponent) cs[i]).setAlignmentY(0.5f);
            p.add(cs[i]);
        }
        return p;
    }

    protected static JComponent fixedWidth(JComponent c, int w) {
        c.setPreferredSize(new Dimension(w, c.getPreferredSize().height));
        c.setMaximumSize(new Dimension(w, c.getMaximumSize().height));
        c.setMinimumSize(new Dimension(w, 10));
        return c;
    }
}
