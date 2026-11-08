package ui;

import java.awt.Component;
import model.User;
import service.ServiceException;
import util.SystemContext;
import service.UserService;

/** Change your own password, or (as manager) set a new one for someone else. */
public class PasswordDialog extends BaseDialog {

    private final FlatPasswordField current = new FlatPasswordField(20).withRevealToggle();
    private final FlatPasswordField next = new FlatPasswordField(20).withRevealToggle();
    private final FlatPasswordField confirm = new FlatPasswordField(20).withRevealToggle();
    private final User target;
    private final boolean own;
    private boolean changed;

    private PasswordDialog(Component parent, User target, boolean own) {
        super(parent, own ? "Change password" : "Reset password",
                own ? "Choose a new password for your account." : "Set a new password for " + target.getFullName() + ".",
                own ? "Update password" : "Reset password");
        this.target = target;
        this.own = own;
        if (own) form.addRow("Current password", current);
        form.addRow("New password", next, "At least 6 characters.");
        form.addRow("Confirm new password", confirm);
    }

    public static boolean changeOwn(Component parent, User user) {
        PasswordDialog d = new PasswordDialog(parent, user, true);
        d.open();
        return d.changed;
    }

    public static boolean reset(Component parent, User target) {
        PasswordDialog d = new PasswordDialog(parent, target, false);
        d.open();
        return d.changed;
    }

    @Override
    protected void apply() {
        String n = new String(next.getPassword());
        if (!n.equals(new String(confirm.getPassword()))) throw new ServiceException("The two new passwords do not match.");
        if (own) SystemContext.users.changeOwnPassword(target, new String(current.getPassword()), n);
        else SystemContext.users.resetPassword(target.getUserId(), n, SystemContext.currentUser);
        changed = true;
        dispose();
    }
}
