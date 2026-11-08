package ui;

import java.awt.*;
import java.security.SecureRandom;
import javax.swing.*;
import model.Role;
import model.User;
import ui.kit.*;
import util.SystemContext;

/** Add-user and reset-password dialogs used by the Users page. */
final class UserDialogs {

    private static final SecureRandom RANDOM = new SecureRandom();

    private UserDialogs() { }

    /** A readable random password with letters and digits (no look-alike characters). */
    static String generatePassword() {
        String letters = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ";
        String digits = "23456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) sb.append(letters.charAt(RANDOM.nextInt(letters.length())));
        for (int i = 0; i < 3; i++) sb.append(digits.charAt(RANDOM.nextInt(digits.length())));
        char[] c = sb.toString().toCharArray();
        for (int i = c.length - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            char t = c[i];
            c[i] = c[j];
            c[j] = t;
        }
        return new String(c);
    }

    /** Returns the created user, or null when cancelled. */
    static User addUser(Window owner, String actor) {
        ModalDialog d = new ModalDialog(owner, "Add user", "Create a login for a member of staff.");
        Field name = new Field("e.g. Nimal Perera");
        Field username = new Field("e.g. nimal");
        Segmented role = new Segmented(true, "Sales Assistant", "Store Manager");
        PassField pw = new PassField(), cf = new PassField();
        ChangePasswordDialog.Strength meter = new ChangePasswordDialog.Strength();
        JLabel generated = Ui.label(" ", Theme.font(12), Theme.muted);
        JLabel error = Ui.label(" ", Theme.font(12.5f), Theme.danger);
        Btn gen = new Btn("Generate", Btn.Kind.SECONDARY).compact();

        JPanel roleRow = Ui.transparent(new BorderLayout());
        roleRow.add(role, BorderLayout.WEST);
        JLabel roleHelp = Ui.label("Can sell, look up stock and view their own sales.", Theme.font(12), Theme.muted);
        role.onChange(i -> roleHelp.setText(i == 0 ? "Can sell, look up stock and view their own sales."
                : "Full access: purchasing, reports, users and settings."));

        JPanel pwHead = Ui.transparent(new BorderLayout());
        pwHead.add(Ui.label("Password", Theme.bold(12), Theme.muted), BorderLayout.WEST);
        pwHead.add(gen, BorderLayout.EAST);

        Form f = new Form();
        f.row("Full name", name);
        f.row("Username", username, "Letters, numbers, dot, dash or underscore. Used to sign in.");
        f.span(Ui.label("Role", Theme.bold(12), Theme.muted), 14);
        f.span(roleRow, 5);
        f.span(roleHelp, 5);
        f.span(pwHead, 10);
        f.span(pw, 5);
        f.span(meter, 8);
        f.row("Confirm password", cf);
        f.span(generated, 8);
        f.span(error, 6);
        d.setContent(f);

        pw.getDocument().addDocumentListener(new SimpleDoc(() -> meter.update(new String(pw.getPassword()))));
        gen.addActionListener(e -> {
            String g = generatePassword();
            pw.setText(g);
            cf.setText(g);
            generated.setText("Generated password: " + g + "  (shown once - pass it on, they can change it later)");
        });

        User[] created = {null};
        d.cancelButton("Cancel");
        Btn save = d.addAction(new Btn("Create user", Btn.Kind.PRIMARY));
        d.makeDefault(save);
        save.addActionListener(e -> {
            name.setInvalid(false);
            username.setInvalid(false);
            pw.setInvalid(false);
            cf.setInvalid(false);
            error.setText(" ");
            String p = new String(pw.getPassword());
            if (!p.equals(new String(cf.getPassword()))) {
                cf.setInvalid(true);
                error.setText("The passwords do not match.");
                return;
            }
            try {
                created[0] = SystemContext.auth.createUser(role.getSelected() == 1 ? Role.MANAGER : Role.ASSISTANT,
                        username.getText(), name.getText(), p, actor);
                d.accept();
            } catch (IllegalArgumentException ex) {
                String m = ex.getMessage() == null ? "" : ex.getMessage();
                error.setText(m);
                if (m.startsWith("Full name")) name.setInvalid(true);
                if (m.startsWith("Username") || m.contains("taken")) username.setInvalid(true);
                if (m.startsWith("Password")) pw.setInvalid(true);
            }
        });
        SwingUtilities.invokeLater(name::requestFocusInWindow);
        d.open();
        return created[0];
    }

    /** Sets a new password for another user. Returns true if saved. */
    static boolean resetPassword(Window owner, User target, String actor) {
        ModalDialog d = new ModalDialog(owner, "Reset password", "Choose a new password for " + target.getFullName() + " (" + target.getUsername() + ").");
        PassField pw = new PassField(), cf = new PassField();
        ChangePasswordDialog.Strength meter = new ChangePasswordDialog.Strength();
        JLabel error = Ui.label(" ", Theme.font(12.5f), Theme.danger);
        Form f = new Form();
        f.row("New password", pw);
        f.span(meter, 8);
        f.row("Confirm new password", cf);
        f.span(error, 10);
        d.setContent(f);
        pw.getDocument().addDocumentListener(new SimpleDoc(() -> meter.update(new String(pw.getPassword()))));
        d.cancelButton("Cancel");
        Btn save = d.addAction(new Btn("Reset password", Btn.Kind.PRIMARY));
        d.makeDefault(save);
        save.addActionListener(e -> {
            String p = new String(pw.getPassword());
            if (!p.equals(new String(cf.getPassword()))) {
                cf.setInvalid(true);
                error.setText("The passwords do not match.");
                return;
            }
            try {
                SystemContext.auth.resetPassword(target, p, actor);
                d.accept();
            } catch (IllegalArgumentException ex) {
                pw.setInvalid(true);
                error.setText(ex.getMessage());
            }
        });
        SwingUtilities.invokeLater(pw::requestFocusInWindow);
        return d.open();
    }
}
