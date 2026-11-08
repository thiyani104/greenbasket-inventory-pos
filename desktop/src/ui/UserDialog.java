package ui;

import java.awt.Component;
import javax.swing.JCheckBox;
import model.Role;
import model.User;
import service.ServiceException;
import util.SystemContext;

/** Create or edit a staff account. */
public class UserDialog extends BaseDialog {

    private final User existing;
    private final FlatTextField username = new FlatTextField(20).placeholder("e.g. kasun");
    private final FlatTextField fullName = new FlatTextField(20).placeholder("Full name");
    private final FlatComboBox<Role> role = new FlatComboBox<>(Role.ASSISTANT, Role.MANAGER);
    private final FlatPasswordField password = new FlatPasswordField(20).withRevealToggle();
    private final FlatPasswordField confirm = new FlatPasswordField(20).withRevealToggle();
    private final JCheckBox active = new JCheckBox("Account is active (can sign in)", true);
    private boolean saved;

    private UserDialog(Component parent, User existing) {
        super(parent, existing == null ? "Add staff account" : "Edit account",
                existing == null ? "Sales assistants can sell and view stock. Managers can do everything." : "@" + existing.getUsername(),
                existing == null ? "Create account" : "Save changes");
        this.existing = existing;
        form.addRow("Full name", fullName);
        if (existing == null) {
            form.addRow("Username", username, "3-20 characters: letters, numbers, dot, dash, underscore.");
        }
        form.addRow("Role", role);
        if (existing == null) {
            form.addRow("Password", password, "At least 6 characters.");
            form.addRow("Confirm password", confirm);
        } else {
            active.setOpaque(false);
            active.setFont(Theme.font(java.awt.Font.PLAIN, 13));
            form.addFull(active);
            fullName.setText(existing.getFullName());
            role.setSelectedItem(existing.getRole());
            active.setSelected(existing.isActive());
        }
    }

    @Override
    protected void apply() {
        if (existing == null) {
            if (!new String(password.getPassword()).equals(new String(confirm.getPassword()))) {
                throw new ServiceException("The two passwords do not match.");
            }
            SystemContext.users.addUser(username.getText(), fullName.getText(), (Role) role.getSelectedItem(),
                    new String(password.getPassword()), SystemContext.currentUser);
        } else {
            SystemContext.users.updateUser(existing.getUserId(), fullName.getText(), (Role) role.getSelectedItem(),
                    active.isSelected(), SystemContext.currentUser);
        }
        saved = true;
        dispose();
    }

    public static boolean showAdd(Component parent) {
        UserDialog d = new UserDialog(parent, null);
        d.open();
        return d.saved;
    }

    public static boolean showEdit(Component parent, User u) {
        UserDialog d = new UserDialog(parent, u);
        d.open();
        return d.saved;
    }
}
