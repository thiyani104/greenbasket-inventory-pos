package ui;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import model.Role;
import model.Sale;
import model.User;
import ui.kit.*;
import util.Fmt;
import util.SystemContext;

/** Staff accounts: add, rename, change role, reset password, deactivate and delete. */
public class UsersPage extends Page {

    /** A user together with their till figures, as one table row. */
    private static final class Row {
        final User user;
        final int sales;
        final double revenue;
        Row(User user, int sales, double revenue) { this.user = user; this.sales = sales; this.revenue = revenue; }
    }

    private final Kpi k1, k2, k3, k4;
    private final ListTableModel<Row> model = new ListTableModel<>();
    private final Tables.DataTable table;
    private final JLabel selection = Ui.label("", Theme.font(13), Theme.muted);
    private final Btn renameBtn = new Btn("Rename", "edit", Btn.Kind.SECONDARY).compact();
    private final Btn roleBtn = new Btn("Change role", "users", Btn.Kind.SECONDARY).compact();
    private final Btn pwBtn = new Btn("Reset password", "key", Btn.Kind.SECONDARY).compact();
    private final Btn activeBtn = new Btn("Deactivate", "pause", Btn.Kind.SECONDARY).compact();
    private final Btn deleteBtn = new Btn("Delete", "trash", Btn.Kind.GHOST).compact();

    public UsersPage(MainFrame host) {
        super(host);
        k1 = new Kpi("Total users", "users", Theme.chart[1]);
        k2 = new Kpi("Managers", "shield", Theme.chart[3]);
        k3 = new Kpi("Active accounts", "check", Theme.chart[0]);
        k4 = new Kpi("Deactivated", "pause", Theme.chart[4]);

        model.title("Name", 190, r -> r.user.getFullName() + (isMe(r.user) ? "  (you)" : ""));
        model.muted("Username", 110, r -> r.user.getUsername());
        model.badge("Role", 140, r -> r.user.getRole().label());
        model.badge("Status", 100, r -> r.user.isActive() ? "ACTIVE" : "INACTIVE");
        model.text("Last sign-in", 140, r -> r.user.getLastLogin() == null ? "Never" : r.user.getLastLogin().format(Fmt.DATE_TIME_SHORT));
        model.integer("Sales", 60, r -> r.sales);
        model.money("Revenue", 110, r -> r.revenue);
        table = Tables.table(model);
        table.setEmptyText("No users");

        Btn add = new Btn("Add user", "plus", Btn.Kind.PRIMARY);
        add.addActionListener(e -> {
            User u = UserDialogs.addUser(host, host.user().getUsername());
            if (u != null) {
                host.toast("Created " + u.getFullName());
                refresh();
                select(u.getUserId());
            }
        });
        JComponent head = Ui.header("Users", "Who can sign in to " + SystemContext.settings.getStoreName() + ", and what they can do", actions(add));

        JPanel kpis = new JPanel(new GridLayout(1, 4, 16, 0));
        kpis.setOpaque(false);
        kpis.add(k1); kpis.add(k2); kpis.add(k3); kpis.add(k4);
        kpis.setBorder(new EmptyBorder(0, 0, 16, 0));

        Card card = new Card(new BorderLayout(0, 12), 14);
        card.add(Tables.scroll(table), BorderLayout.CENTER);
        JPanel foot = Ui.transparent(new BorderLayout(10, 0));
        foot.add(selection, BorderLayout.CENTER);
        JPanel fb = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        fb.setOpaque(false);
        fb.add(renameBtn); fb.add(roleBtn); fb.add(pwBtn); fb.add(activeBtn); fb.add(deleteBtn);
        foot.add(fb, BorderLayout.EAST);
        foot.setPreferredSize(new Dimension(100, 34));
        card.add(foot, BorderLayout.SOUTH);

        table.getSelectionModel().addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) onSelect(); });
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { if (e.getClickCount() == 2 && selected() != null) rename(); }
        });
        renameBtn.addActionListener(e -> rename());
        roleBtn.addActionListener(e -> changeRole());
        pwBtn.addActionListener(e -> resetPassword());
        activeBtn.addActionListener(e -> toggleActive());
        deleteBtn.addActionListener(e -> delete());

        JPanel body = Ui.transparent(new BorderLayout(18, 0));
        body.add(card, BorderLayout.CENTER);
        JComponent side = roles();
        side.setPreferredSize(new Dimension(290, 100));
        body.add(side, BorderLayout.EAST);

        JPanel wrap = Ui.transparent(new BorderLayout());
        wrap.add(kpis, BorderLayout.NORTH);
        wrap.add(body, BorderLayout.CENTER);
        shell(head, wrap);
        refresh();
    }

    private boolean isMe(User u) {
        return u.getUsername().equalsIgnoreCase(host.user().getUsername());
    }

    private JComponent roles() {
        JPanel col = Ui.transparent(null);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.add(roleBlock("Store Manager", "Full access", "STORE MANAGER", new String[]{
                "Everything a sales assistant can do",
                "Purchasing and stock adjustments",
                "Reports, customers and the activity log",
                "Users, settings and backups",
                "Void sales and give any discount"}));
        col.add(Box.createVerticalStrut(14));
        col.add(roleBlock("Sales Assistant", "Till and stock look-up", "SALES ASSISTANT", new String[]{
                "Point of sale and receipts",
                "Browse inventory and stock history",
                "View their own sales only",
                "Discounts up to the limit in Settings"}));
        JPanel holder = Ui.transparent(new BorderLayout());
        holder.add(col, BorderLayout.NORTH);
        return holder;
    }

    private JComponent roleBlock(String title, String sub, String badge, String[] points) {
        Card c = new Card(new BorderLayout(0, 10), 16);
        JPanel head = Ui.transparent(new BorderLayout());
        JPanel t = Ui.transparent(null);
        t.setLayout(new BoxLayout(t, BoxLayout.Y_AXIS));
        t.add(Ui.label(title, Theme.bold(14), Theme.text));
        t.add(Ui.label(sub, Theme.font(12), Theme.muted));
        head.add(t, BorderLayout.WEST);
        c.add(head, BorderLayout.NORTH);
        JPanel list = Ui.transparent(null);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        for (String p : points) {
            JPanel r = Ui.transparent(new BorderLayout(8, 0));
            r.setBorder(new EmptyBorder(3, 0, 3, 0));
            JLabel dot = Ui.label("✓", Theme.bold(12), Theme.success);
            r.add(dot, BorderLayout.WEST);
            r.add(Ui.label(p, Theme.font(12.5f), Theme.text), BorderLayout.CENTER);
            r.setAlignmentX(0);
            list.add(r);
        }
        c.add(list, BorderLayout.CENTER);
        return c;
    }

    // ------------------------------------------------------------------ data

    private User selected() {
        Row r = Tables.selected(table);
        return r == null ? null : r.user;
    }

    private void select(int userId) {
        for (int i = 0; i < table.getRowCount(); i++) {
            Row r = model.at(table.convertRowIndexToModel(i));
            if (r != null && r.user.getUserId() == userId) {
                table.setRowSelectionInterval(i, i);
                return;
            }
        }
    }

    @Override public void refresh() {
        Integer keep = selected() == null ? null : selected().getUserId();
        Map<String, double[]> stats = new HashMap<>();
        for (Sale s : SystemContext.sales.all()) {
            if (s.isVoided()) continue;
            double[] v = stats.computeIfAbsent(s.getCashier().toLowerCase(), k -> new double[2]);
            v[0]++;
            v[1] += s.getTotal();
        }
        List<Row> rows = new ArrayList<>();
        int managers = 0, active = 0;
        for (User u : SystemContext.auth.getUsers()) {
            double[] v = stats.getOrDefault(u.getUsername().toLowerCase(), new double[2]);
            rows.add(new Row(u, (int) v[0], v[1]));
            if (u.isManager()) managers++;
            if (u.isActive()) active++;
        }
        int total = rows.size();
        model.setRows(rows);
        k1.set(String.valueOf(total), (total - managers) + " sales assistant(s)", Kpi.Tone.NEUTRAL);
        k2.set(String.valueOf(managers), managers == 1 ? "Only one - consider a backup" : "With full access", managers == 1 ? Kpi.Tone.WARN : Kpi.Tone.NEUTRAL);
        k3.set(String.valueOf(active), "Can sign in", Kpi.Tone.GOOD);
        k4.set(String.valueOf(total - active), total - active == 0 ? "Everyone is active" : "Blocked from signing in", Kpi.Tone.NEUTRAL);
        if (keep != null) select(keep);
        onSelect();
    }

    private void onSelect() {
        User u = selected();
        boolean has = u != null;
        boolean me = has && isMe(u);
        renameBtn.setEnabled(has);
        pwBtn.setEnabled(has);
        roleBtn.setEnabled(has && !me);
        activeBtn.setEnabled(has && !me);
        deleteBtn.setEnabled(has && !me);
        String why = "You cannot do this to your own account";
        roleBtn.setToolTipText(me ? why : has ? "Switch between Sales Assistant and Store Manager" : null);
        activeBtn.setToolTipText(me ? why : null);
        deleteBtn.setToolTipText(me ? why : null);
        activeBtn.setText(has && !u.isActive() ? "Activate" : "Deactivate");
        activeBtn.setIconName(has && !u.isActive() ? "check" : "pause");
        if (!has) {
            selection.setText(model.getRowCount() + " user(s)");
            selection.setFont(Theme.font(13));
            selection.setForeground(Theme.muted);
        } else {
            selection.setText(u.getFullName());
            selection.setFont(Theme.bold(13));
            selection.setForeground(Theme.text);
        }
    }

    // --------------------------------------------------------------- actions

    private String actor() { return host.user().getUsername(); }

    private void rename() {
        User u = selected();
        if (u == null) return;
        String n = Dialogs.prompt(host, "Rename user", "Change how " + u.getUsername() + " appears in the app and on receipts.", "Full name", u.getFullName());
        if (n == null) return;
        try {
            SystemContext.auth.rename(u, n, actor());
            host.toast("Renamed to " + n);
            refresh();
        } catch (IllegalArgumentException ex) {
            Dialogs.error(host, ex.getMessage());
        }
    }

    private void changeRole() {
        User u = selected();
        if (u == null || isMe(u)) return;
        Role to = u.isManager() ? Role.ASSISTANT : Role.MANAGER;
        if (!Dialogs.confirm(host, "Change role", "Make " + u.getFullName() + " a " + to.label() + "?\n\n"
                + (to == Role.MANAGER ? "They will get full access, including reports, users and settings."
                : "They will lose access to purchasing, reports, users and settings."), "Make " + to.label(), false)) return;
        try {
            User n = SystemContext.auth.changeRole(u, to, actor());
            host.toast(n.getFullName() + " is now a " + to.label());
            refresh();
            select(n.getUserId());
        } catch (IllegalArgumentException ex) {
            Dialogs.error(host, ex.getMessage());
        }
    }

    private void resetPassword() {
        User u = selected();
        if (u == null) return;
        if (UserDialogs.resetPassword(host, u, actor())) host.toast("Password reset for " + u.getUsername());
    }

    private void toggleActive() {
        User u = selected();
        if (u == null || isMe(u)) return;
        boolean activate = !u.isActive();
        if (!activate && !Dialogs.confirm(host, "Deactivate account",
                u.getFullName() + " will no longer be able to sign in. Their sales history is kept.", "Deactivate", true)) return;
        try {
            SystemContext.auth.setActive(u, activate, actor());
            host.toast(u.getFullName() + (activate ? " activated" : " deactivated"));
            refresh();
        } catch (IllegalArgumentException ex) {
            Dialogs.error(host, ex.getMessage());
        }
    }

    private void delete() {
        User u = selected();
        if (u == null || isMe(u)) return;
        if (!Dialogs.confirm(host, "Delete user", "Delete " + u.getFullName() + " (" + u.getUsername()
                + ")?\n\nTheir past sales stay on record. If you only want to block access, deactivate the account instead.", "Delete", true)) return;
        try {
            SystemContext.auth.deleteUser(u, actor());
            host.toast("User deleted");
            refresh();
        } catch (IllegalArgumentException ex) {
            Dialogs.error(host, ex.getMessage());
        }
    }
}
