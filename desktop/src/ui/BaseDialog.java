package ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/** Modal form dialog: title, subtitle, form body, inline error line, Cancel / primary buttons. */
public abstract class BaseDialog extends JDialog {

    protected final FormPanel form = new FormPanel();
    private final JLabel errorLabel = UI.label(" ", java.awt.Font.PLAIN, 12, Theme.DANGER);
    protected final FlatButton okButton;
    protected final FlatButton cancelButton = FlatButton.secondary("Cancel");

    protected BaseDialog(Component parent, String title, String subtitle, String okText) {
        super(parent == null ? null : parent instanceof Window w ? w : SwingUtilities.getWindowAncestor(parent),
                title, DEFAULT_MODALITY_TYPE);
        okButton = FlatButton.primary(okText);
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(Color.WHITE);

        JPanel head = UI.transparent(new BorderLayout(0, 2));
        head.setBorder(BorderFactory.createEmptyBorder(22, 26, 14, 26));
        head.add(UI.title(title), BorderLayout.NORTH);
        if (subtitle != null && !subtitle.isEmpty()) head.add(UI.muted(subtitle), BorderLayout.CENTER);
        root.add(head, BorderLayout.NORTH);

        form.setBorder(BorderFactory.createEmptyBorder(4, 26, 4, 26));
        root.add(form, BorderLayout.CENTER);

        JPanel foot = UI.transparent(new BorderLayout());
        foot.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER_SOFT),
                BorderFactory.createEmptyBorder(12, 26, 16, 26)));
        foot.add(errorLabel, BorderLayout.CENTER);
        foot.add(UI.flow(FlowLayout.RIGHT, 8, 0, cancelButton, okButton), BorderLayout.EAST);
        root.add(foot, BorderLayout.SOUTH);

        setContentPane(root);
        setResizable(false);
        cancelButton.addActionListener(e -> dispose());
        okButton.addActionListener(e -> submit());
        getRootPane().setDefaultButton(okButton);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    /** Subclasses validate and apply; throw ServiceException / IllegalArgumentException to show an error. */
    protected abstract void apply();

    private void submit() {
        try {
            apply();
        } catch (service.ServiceException | IllegalArgumentException ex) {
            showError(ex.getMessage());
        }
    }

    protected void showError(String msg) {
        errorLabel.setText("<html>" + msg.replace("<", "&lt;") + "</html>");
        pack();
    }

    protected void clearError() { errorLabel.setText(" "); }

    public void open() {
        pack();
        setMinimumSize(new java.awt.Dimension(getWidth(), getHeight()));
        setLocationRelativeTo(getOwner());
        setVisible(true);
    }

    /** Two-column label / field grid. */
    public static class FormPanel extends JPanel {
        private int row = 0;

        public FormPanel() {
            super(new GridBagLayout());
            setOpaque(false);
        }

        public void addRow(String label, JComponent field) { addRow(label, field, null); }

        public void addRow(String label, JComponent field, String hint) {
            GridBagConstraints lc = new GridBagConstraints();
            lc.gridx = 0;
            lc.gridy = row;
            lc.anchor = GridBagConstraints.NORTHWEST;
            lc.insets = new Insets(0, 0, 4, 0);
            lc.gridwidth = 1;
            lc.weightx = 1;
            lc.fill = GridBagConstraints.HORIZONTAL;
            JLabel l = UI.label(label, java.awt.Font.BOLD, 12, Theme.TEXT);
            add(l, lc);
            GridBagConstraints fc = new GridBagConstraints();
            fc.gridx = 0;
            fc.gridy = row + 1;
            fc.fill = GridBagConstraints.HORIZONTAL;
            fc.weightx = 1;
            fc.insets = new Insets(0, 0, hint == null ? 12 : 2, 0);
            add(field, fc);
            if (hint != null) {
                GridBagConstraints hc = new GridBagConstraints();
                hc.gridx = 0;
                hc.gridy = row + 2;
                hc.anchor = GridBagConstraints.NORTHWEST;
                hc.insets = new Insets(0, 0, 12, 0);
                add(UI.muted(hint), hc);
                row++;
            }
            row += 2;
        }

        /** Two fields side by side. */
        public void addPair(String l1, JComponent f1, String l2, JComponent f2) {
            JPanel pair = UI.transparent(new java.awt.GridLayout(1, 2, 12, 0));
            JPanel a = UI.transparent(new BorderLayout(0, 4));
            a.add(UI.label(l1, java.awt.Font.BOLD, 12, Theme.TEXT), BorderLayout.NORTH);
            a.add(f1, BorderLayout.CENTER);
            JPanel b = UI.transparent(new BorderLayout(0, 4));
            b.add(UI.label(l2, java.awt.Font.BOLD, 12, Theme.TEXT), BorderLayout.NORTH);
            b.add(f2, BorderLayout.CENTER);
            pair.add(a);
            pair.add(b);
            GridBagConstraints c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.weightx = 1;
            c.insets = new Insets(0, 0, 12, 0);
            add(pair, c);
            row++;
        }

        public void addFull(JComponent c) {
            GridBagConstraints gc = new GridBagConstraints();
            gc.gridx = 0;
            gc.gridy = row++;
            gc.fill = GridBagConstraints.HORIZONTAL;
            gc.weightx = 1;
            gc.insets = new Insets(0, 0, 12, 0);
            add(c, gc);
        }
    }

    // ============================== input parsing helpers ==============================

    protected static double parseMoney(String text, String field) {
        try {
            double v = Double.parseDouble(text.trim().replace(",", ""));
            if (Double.isNaN(v) || Double.isInfinite(v)) throw new NumberFormatException();
            return v;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " must be a number.");
        }
    }

    protected static int parseInt(String text, String field) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " must be a whole number.");
        }
    }
}
