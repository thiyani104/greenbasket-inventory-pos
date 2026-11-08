package ui;

import java.awt.*;
import javax.swing.*;
import model.User;
import ui.kit.*;
import util.PasswordUtil;
import util.SystemContext;

/** Change your own password, with a live strength meter. */
public final class ChangePasswordDialog {

    private ChangePasswordDialog() { }

    public static void show(Window owner, User user, boolean forced) {
        ModalDialog d = new ModalDialog(owner, "Change password", "Use at least 6 characters with letters and numbers.");
        PassField cur = new PassField(), np = new PassField(), cf = new PassField();
        Strength meter = new Strength();
        JLabel err = new JLabel(" ");
        err.setFont(Theme.font(12.5f));
        err.setForeground(Theme.danger);

        Form f = new Form();
        f.row("Current password", cur);
        f.row("New password", np);
        f.span(meter, 8);
        f.row("Confirm new password", cf);
        f.span(err, 10);
        d.setContent(f);

        np.getDocument().addDocumentListener(new SimpleDoc(() -> meter.update(new String(np.getPassword()))));
        d.cancelButton(forced ? "Later" : "Cancel");
        Btn save = d.addAction(new Btn("Update password", Btn.Kind.PRIMARY));
        d.makeDefault(save);
        save.addActionListener(e -> {
            String n = new String(np.getPassword());
            if (!n.equals(new String(cf.getPassword()))) {
                err.setText("The new passwords do not match.");
                cf.setInvalid(true);
                return;
            }
            try {
                SystemContext.auth.changePassword(user, new String(cur.getPassword()), n);
                d.accept();
            } catch (IllegalArgumentException ex) {
                err.setText(ex.getMessage());
            }
        });
        SwingUtilities.invokeLater(cur::requestFocusInWindow);
        if (d.open() && owner instanceof MainFrame) ((MainFrame) owner).toast("Password updated");
    }

    /** Four-segment strength bar. */
    static class Strength extends JComponent {
        private int level;
        private String label = "";

        void update(String pw) {
            level = 0;
            if (pw.length() >= 1) level = 1;
            if (PasswordUtil.validateStrength(pw) == null) level = 2;
            if (level == 2 && pw.length() >= 10) level = 3;
            if (level == 3 && pw.chars().anyMatch(c -> !Character.isLetterOrDigit(c))) level = 4;
            label = pw.isEmpty() ? "" : new String[]{"", "Too weak", "OK", "Good", "Strong"}[level];
            repaint();
        }

        @Override public Dimension getPreferredSize() { return new Dimension(200, 18); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = Gfx.aa(g);
            Color[] cols = {Theme.border, Theme.danger, Theme.warning, Theme.success, Theme.success};
            int segW = (getWidth() - 90) / 4;
            for (int i = 0; i < 4; i++) Gfx.fillRound(g2, i * (segW + 4), 5, segW, 5, 2.5, i < level ? cols[level] : Theme.border);
            Gfx.text(g2, label, getWidth() - 80, 11, Theme.font(11.5f), Theme.muted);
            g2.dispose();
        }
    }
}
