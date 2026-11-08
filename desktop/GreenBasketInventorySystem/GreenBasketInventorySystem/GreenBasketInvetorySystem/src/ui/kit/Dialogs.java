package ui.kit;

import java.awt.*;
import javax.swing.*;

/** Message and confirmation dialogs in the app's own style (instead of the stock JOptionPane). */
public final class Dialogs {

    private Dialogs() { }

    private static Window win(Component c) {
        if (c == null) return null;
        if (c instanceof Window) return (Window) c;
        return SwingUtilities.getWindowAncestor(c);
    }

    private static JComponent text(String msg) {
        return Ui.wrapped(esc(msg).replace("\n", "<br>"), 360, Theme.font(13.5f), Theme.text);
    }

    public static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public static void info(Component parent, String title, String msg) {
        ModalDialog d = new ModalDialog(win(parent), title, null);
        d.setContent(text(msg));
        Btn ok = d.addAction(new Btn("OK", Btn.Kind.PRIMARY));
        ok.addActionListener(e -> d.dispose());
        d.makeDefault(ok);
        d.open();
    }

    public static void error(Component parent, String msg) {
        ModalDialog d = new ModalDialog(win(parent), "Something went wrong", null);
        d.setContent(text(msg));
        Btn ok = d.addAction(new Btn("OK", Btn.Kind.PRIMARY));
        ok.addActionListener(e -> d.dispose());
        d.makeDefault(ok);
        d.open();
    }

    /** Returns true if the user confirmed. */
    public static boolean confirm(Component parent, String title, String msg, String yesLabel, boolean danger) {
        ModalDialog d = new ModalDialog(win(parent), title, null);
        d.setContent(text(msg));
        d.cancelButton("Cancel");
        Btn yes = d.addAction(new Btn(yesLabel, danger ? Btn.Kind.DANGER : Btn.Kind.PRIMARY));
        yes.addActionListener(e -> d.accept());
        d.makeDefault(yes);
        return d.open();
    }

    /** Single-line text prompt. Returns null when cancelled. */
    public static String prompt(Component parent, String title, String subtitle, String label, String initial) {
        ModalDialog d = new ModalDialog(win(parent), title, subtitle);
        Form f = new Form();
        Field field = new Field();
        field.setText(initial == null ? "" : initial);
        f.row(label, field);
        d.setContent(f);
        d.cancelButton("Cancel");
        Btn ok = d.addAction(new Btn("OK", Btn.Kind.PRIMARY));
        ok.addActionListener(e -> d.accept());
        d.makeDefault(ok);
        SwingUtilities.invokeLater(field::requestFocusInWindow);
        return d.open() ? field.getText().trim() : null;
    }
}
