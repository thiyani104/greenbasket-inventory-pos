package ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import model.Role;
import model.Sale;
import model.User;
import util.Formats;
import util.SystemContext;

/** Staff accounts: add people, change roles, deactivate leavers, reset passwords. */
public class UsersPage extends Page {

    private final RowModel<User> model = new RowModel<>(
            new String[]{"Name", "Username", "Role", "Status", "Sales (30 days)", "Last sign-in", "Created"},
            new Class<?>[]{String.class, String.class, String.class, String.class, Integer.class, String.class, String.class},
            (u, c) -> switch (c) {
                case 0 -> u.getFullName();
                case 1 -> u.getUsername();
                case 2 -> u.getRole().getLabel();
                case 3 -> u.isActive() ? "Active" : "Deactivated";
                case 4 -> salesCount(u);
                case 5 -> u.getLastLogin() == null ? "Never" : Formats.dateTime(u.getLastLogin());
                default -> Formats.date(u.getCreatedAt().toLocalDate());
            });
    private final JTable table = new JTable(model);
    private final JLabel summary = UI.muted(" ");

    public UsersPage() {
        UI.styleTable(table);
        UI.widths(table, 190, 130, 130, 110, 110, 150, 100);
        table.getColumnModel().getColumn(0).setCellRenderer(new UI.Cell(SwingConstants.LEFT) {
            @Override protected Color colorFor(Object v, int row, JTable t) {
                return model.at(t.convertRowIndexToModel(row)).isActive() ? Theme.TEXT : Theme.MUTED;
            }
        });
        table.getColumnModel().getColumn(1).setCellRenderer(new UI.Cell(SwingConstants.LEFT, true));
        table.getColumnModel().getColumn(2).setCellRenderer(UI.pillRenderer(v ->
                v.toString().equals(Role.MANAGER.getLabel()) ? new Color[]{new Color(0x6A3FB0), new Color(0xEFE7FA)}
                        : new Color[]{new Color(0x1F5FA8), Theme.INFO_LIGHT}));
        table.getColumnModel().getColumn(3).setCellRenderer(UI.pillRenderer(v ->
                v.toString().equals("Active") ? new Color[]{Theme.PRIMARY_DARK, Theme.PRIMARY_LIGHT}
                        : new Color[]{Theme.MUTED, new Color(0xEBF0EC)}));

        javax.swing.JPanel bar = UI.transparent(new BorderLayout());
        FlatButton add = FlatButton.primary("Add staff").icon(Icons.Kind.PLUS);
        add.addActionListener(e -> {
            if (UserDialog.showAdd(this)) { SystemContext.fireChanged(); refresh(); Toast.success(this, "Account created"); }
        });
        FlatButton edit = FlatButton.secondary("Edit").icon(Icons.Kind.EDIT);
        edit.addActionListener(e -> editSelected());
        FlatButton reset = FlatButton.secondary("Reset password");
        reset.addActionListener(e -> resetSelected());
        bar.add(UI.muted("Managers can do everything. Sales assistants can sell, look up stock and see their own sales."), BorderLayout.WEST);
        bar.add(UI.flow(FlowLayout.RIGHT, 8, 0, reset, edit, add), BorderLayout.EAST);

        Card card = new Card(new BorderLayout(0, 10)).padding(14, 14, 10, 14);
        card.add(UI.tableScroll(table), BorderLayout.CENTER);
        card.add(summary, BorderLayout.SOUTH);
        add(bar, BorderLayout.NORTH);
        add(card, BorderLayout.CENTER);
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && table.rowAtPoint(e.getPoint()) >= 0) editSelected();
            }
        });
    }

    @Override public String getTitle() { return "Staff"; }

    @Override public String getSubtitle() { return "Who can sign in, and what they can do"; }

    private static int salesCount(User u) {
        LocalDate from = LocalDate.now().minusDays(29);
        int n = 0;
        for (Sale s : SystemContext.sales.forCashier(u.getUsername(), from, null)) if (!s.isVoided()) n++;
        return n;
    }

    @Override
    public void refresh() {
        List<User> rows = new ArrayList<>(SystemContext.users.getAll());
        rows.sort((a, b) -> a.getRole() != b.getRole() ? a.getRole().compareTo(b.getRole())
                : a.getFullName().compareToIgnoreCase(b.getFullName()));
        model.setRows(rows);
        long active = rows.stream().filter(User::isActive).count();
        summary.setText(rows.size() + " accounts  ·  " + active + " active  ·  passwords are stored as salted hashes and can never be viewed");
    }

    private User selected() {
        int r = table.getSelectedRow();
        return r < 0 ? null : model.at(table.convertRowIndexToModel(r));
    }

    private void editSelected() {
        User u = selected();
        if (u == null) { Toast.show(this, "Select an account first", Dialogs.Kind.WARNING); return; }
        if (UserDialog.showEdit(this, u)) { SystemContext.fireChanged(); refresh(); Toast.success(this, "Account updated"); }
    }

    private void resetSelected() {
        User u = selected();
        if (u == null) { Toast.show(this, "Select an account first", Dialogs.Kind.WARNING); return; }
        if (PasswordDialog.reset(this, u)) Toast.success(this, "Password reset for " + u.getUsername());
    }
}
