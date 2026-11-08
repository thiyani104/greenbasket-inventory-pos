package ui.kit;

import java.awt.*;
import javax.swing.*;

/** Vertical form: small label above each control, optionally two controls per row. */
public class Form extends JPanel {

    private final GridBagConstraints gc = new GridBagConstraints();
    private int y = 0;

    public Form() {
        super(new GridBagLayout());
        setOpaque(false);
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.anchor = GridBagConstraints.NORTHWEST;
    }

    private static JLabel label(String s) {
        JLabel l = new JLabel(s);
        l.setFont(Theme.bold(12));
        l.setForeground(Theme.muted);
        return l;
    }

    public void row(String label, JComponent c) { row(label, c, null); }

    public void row(String label, JComponent c, String help) {
        gc.gridx = 0; gc.gridy = y++; gc.gridwidth = 2; gc.weightx = 1; gc.insets = new Insets(y == 1 ? 0 : 14, 0, 5, 0);
        add(label(label), gc);
        gc.gridy = y++; gc.insets = new Insets(0, 0, 0, 0);
        add(c, gc);
        if (help != null) {
            gc.gridy = y++; gc.insets = new Insets(4, 0, 0, 0);
            JLabel h = new JLabel(help);
            h.setFont(Theme.font(11.5f));
            h.setForeground(Theme.faint);
            add(h, gc);
        }
    }

    public void pair(String l1, JComponent c1, String l2, JComponent c2) {
        gc.gridwidth = 1; gc.weightx = 0.5;
        gc.gridx = 0; gc.gridy = y; gc.insets = new Insets(y == 0 ? 0 : 14, 0, 5, 7);
        add(label(l1), gc);
        gc.gridx = 1; gc.insets = new Insets(y == 0 ? 0 : 14, 7, 5, 0);
        add(label(l2), gc);
        y++;
        gc.gridx = 0; gc.gridy = y; gc.insets = new Insets(0, 0, 0, 7);
        add(c1, gc);
        gc.gridx = 1; gc.insets = new Insets(0, 7, 0, 0);
        add(c2, gc);
        y++;
    }

    /** A free-standing component spanning the full width (e.g. a hint or error line). */
    public JLabel note(String text, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.font(12));
        l.setForeground(color);
        gc.gridx = 0; gc.gridy = y++; gc.gridwidth = 2; gc.weightx = 1; gc.insets = new Insets(10, 0, 0, 0);
        add(l, gc);
        return l;
    }

    public void span(JComponent c, int topGap) {
        gc.gridx = 0; gc.gridy = y++; gc.gridwidth = 2; gc.weightx = 1; gc.insets = new Insets(topGap, 0, 0, 0);
        add(c, gc);
    }
}
