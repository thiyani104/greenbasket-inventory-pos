package ui;

import java.awt.*;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.TableCellRenderer;
import model.ActivityEntry;
import ui.kit.*;
import util.FileManager;
import util.SystemContext;

/** Audit trail: who signed in, sold, edited stock, changed users... searchable and exportable. */
public class ActivityPage extends Page {

    private static final String[] CATEGORIES = {"All activity", "Sign-ins", "Sales", "Stock & products", "Purchasing", "Users & security", "System"};
    private static final int CAP = 2000;

    /** Presentation of one audit action: readable label, category index (see CATEGORIES) and colour tone. */
    private enum Tone { GOOD, INFO, WARN, BAD, NEUTRAL }

    private static final class Info {
        final String label;
        final int category;
        final Tone tone;
        Info(String label, int category, Tone tone) { this.label = label; this.category = category; this.tone = tone; }
    }

    static Info describe(String action) {
        switch (action) {
            case "LOGIN": return new Info("Signed in", 1, Tone.GOOD);
            case "LOGOUT": return new Info("Signed out", 1, Tone.NEUTRAL);
            case "LOGIN_FAILED": return new Info("Failed sign-in", 1, Tone.BAD);
            case "SALE": return new Info("Sale", 2, Tone.GOOD);
            case "SALE_VOIDED": return new Info("Sale voided", 2, Tone.WARN);
            case "PRODUCT_ADDED": return new Info("Product added", 3, Tone.GOOD);
            case "PRODUCT_UPDATED": return new Info("Product edited", 3, Tone.INFO);
            case "PRODUCT_DELETED": return new Info("Product deleted", 3, Tone.BAD);
            case "PRODUCTS_IMPORTED": return new Info("Products imported", 3, Tone.INFO);
            case "PO_CREATED": return new Info("Order placed", 4, Tone.INFO);
            case "PO_RECEIVED": return new Info("Order received", 4, Tone.GOOD);
            case "PO_CANCELLED": return new Info("Order cancelled", 4, Tone.WARN);
            case "USER_CREATED": return new Info("User created", 5, Tone.GOOD);
            case "USER_RENAMED": return new Info("User renamed", 5, Tone.INFO);
            case "USER_ROLE_CHANGED": return new Info("Role changed", 5, Tone.INFO);
            case "USER_ACTIVATED": return new Info("User activated", 5, Tone.GOOD);
            case "USER_DEACTIVATED": return new Info("User deactivated", 5, Tone.WARN);
            case "USER_DELETED": return new Info("User deleted", 5, Tone.BAD);
            case "PASSWORD_CHANGED": return new Info("Password changed", 5, Tone.INFO);
            case "PASSWORD_RESET": return new Info("Password reset", 5, Tone.WARN);
            case "BACKUP": return new Info("Backup", 6, Tone.GOOD);
            case "RESTORE": return new Info("Restore", 6, Tone.WARN);
            case "MIGRATED": return new Info("Data migrated", 6, Tone.INFO);
            default:
                String pretty = action.isEmpty() ? action : action.charAt(0) + action.substring(1).toLowerCase().replace('_', ' ');
                int cat = action.startsWith("PRODUCT") ? 3 : action.startsWith("PO_") ? 4 : action.startsWith("USER_") || action.startsWith("PASSWORD") ? 5 : 6;
                return new Info(pretty, cat, Tone.NEUTRAL);
        }
    }

    private final Kpi k1, k2, k3, k4;
    private final Field search = new Field("Search user, action or detail").icon("search");
    private final Combo<String> category = new Combo<>(CATEGORIES);
    private final Combo<String> who = new Combo<>("All users");
    private final Segmented period = new Segmented(true, "Today", "7 days", "30 days", "All time");
    private final ListTableModel<ActivityEntry> model = new ListTableModel<>();
    private final Tables.DataTable table;
    private final JLabel info = Ui.muted("");
    private boolean updating;

    public ActivityPage(MainFrame host) {
        super(host);
        k1 = new Kpi("Events today", "activity", Theme.chart[0]);
        k2 = new Kpi("Sign-ins today", "user", Theme.chart[1]);
        k3 = new Kpi("Failed sign-ins (24h)", "shield", Theme.chart[4]);
        k4 = new Kpi("Sales today", "receipt", Theme.chart[3]);

        model.dateTime("Time", 150, ActivityEntry::getTime);
        model.muted("User", 110, ActivityEntry::getUser);
        model.badge("Action", 150, e -> describe(e.getAction()).label);
        model.text("Detail", 380, ActivityEntry::getDetail);
        table = Tables.table(model);
        table.setEmptyText("No activity matches your filters");
        table.setRowHeight(42);
        table.getColumnModel().getColumn(2).setCellRenderer(new ToneRenderer());

        Btn export = new Btn("Export CSV", "download", Btn.Kind.SECONDARY);
        export.addActionListener(e -> export());
        JComponent head = Ui.header("Activity log", "A permanent record of sign-ins, sales, stock changes and admin actions", actions(export));

        JPanel kpis = new JPanel(new GridLayout(1, 4, 16, 0));
        kpis.setOpaque(false);
        kpis.add(k1); kpis.add(k2); kpis.add(k3); kpis.add(k4);
        kpis.setBorder(new EmptyBorder(0, 0, 16, 0));

        category.setPreferredSize(new Dimension(190, 40));
        who.setPreferredSize(new Dimension(170, 40));
        JPanel row1 = Ui.transparent(new BorderLayout(12, 0));
        row1.add(search, BorderLayout.CENTER);
        JPanel combos = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        combos.setOpaque(false);
        combos.add(who);
        combos.add(category);
        row1.add(combos, BorderLayout.EAST);
        row1.setBorder(new EmptyBorder(0, 0, 10, 0));
        JPanel row2 = Ui.transparent(new GridBagLayout());
        row2.add(period);
        JPanel row2w = Ui.transparent(new BorderLayout());
        row2w.add(row2, BorderLayout.WEST);
        row2w.setBorder(new EmptyBorder(0, 0, 12, 0));
        JPanel filters = Ui.transparent(new BorderLayout());
        filters.add(row1, BorderLayout.NORTH);
        filters.add(row2w, BorderLayout.SOUTH);

        period.setSelected(1);
        period.onChange(i -> applyFilters());
        search.getDocument().addDocumentListener(new SimpleDoc(this::applyFilters));
        category.addActionListener(e -> applyFilters());
        who.addActionListener(e -> applyFilters());

        Card card = new Card(new BorderLayout(0, 10), 14);
        card.add(Tables.scroll(table), BorderLayout.CENTER);
        info.setBorder(new EmptyBorder(0, 2, 0, 0));
        card.add(info, BorderLayout.SOUTH);

        JPanel body = Ui.transparent(new BorderLayout());
        JPanel top = Ui.transparent(new BorderLayout());
        top.add(kpis, BorderLayout.NORTH);
        top.add(filters, BorderLayout.SOUTH);
        body.add(top, BorderLayout.NORTH);
        body.add(card, BorderLayout.CENTER);
        shell(head, body);
        refresh();
    }

    @Override public void refresh() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        int today = SystemContext.log.between(LocalDate.now(), LocalDate.now()).size();
        int failed = SystemContext.log.count("LOGIN_FAILED", LocalDateTime.now().minusHours(24));
        k1.set(String.valueOf(today), today == 0 ? "Nothing recorded yet" : "Across all users", Kpi.Tone.NEUTRAL);
        k2.set(String.valueOf(SystemContext.log.count("LOGIN", startOfDay)), "Successful", Kpi.Tone.NEUTRAL);
        k3.set(String.valueOf(failed), failed == 0 ? "No suspicious attempts" : "Review the sign-in log", failed == 0 ? Kpi.Tone.GOOD : Kpi.Tone.WARN);
        k4.set(String.valueOf(SystemContext.log.count("SALE", startOfDay)), "Completed at the till", Kpi.Tone.NEUTRAL);

        updating = true;
        Object keep = who.getSelectedItem();
        List<String> names = new ArrayList<>();
        names.add("All users");
        names.addAll(SystemContext.log.users());
        who.setModel(new DefaultComboBoxModel<>(names.toArray(new String[0])));
        who.setSelectedItem(keep != null && names.contains(keep.toString()) ? keep : "All users");
        updating = false;
        applyFilters();
    }

    private void applyFilters() {
        if (updating) return;
        LocalDate today = LocalDate.now();
        LocalDate from = null;
        switch (period.getSelected()) {
            case 0: from = today; break;
            case 1: from = today.minusDays(6); break;
            case 2: from = today.minusDays(29); break;
            default: break;
        }
        int cat = category.getSelectedIndex();
        String user = who.getSelectedIndex() <= 0 ? null : String.valueOf(who.getSelectedItem());
        String q = search.getText().trim().toLowerCase();
        List<ActivityEntry> rows = new ArrayList<>();
        int total = 0;
        for (ActivityEntry e : SystemContext.log.between(from, today)) {
            Info i = describe(e.getAction());
            if (cat > 0 && i.category != cat) continue;
            if (user != null && !e.getUser().equalsIgnoreCase(user)) continue;
            if (!q.isEmpty() && !(e.getUser().toLowerCase().contains(q) || e.getDetail().toLowerCase().contains(q)
                    || i.label.toLowerCase().contains(q) || e.getAction().toLowerCase().contains(q))) continue;
            total++;
            if (rows.size() < CAP) rows.add(e);
        }
        model.setRows(rows);
        info.setText(total > rows.size() ? "Showing the latest " + rows.size() + " of " + total + " events"
                : total + (total == 1 ? " event" : " events"));
    }

    private void export() {
        Path p = Pick.save(this, "activity-" + LocalDate.now().toString().replace("-", "") + ".csv", "CSV file", "csv");
        if (p == null) return;
        List<String[]> out = new ArrayList<>();
        for (ActivityEntry e : model.rows()) out.add(e.toCsv());
        try {
            FileManager.writeCsvFile(p, ActivityEntry.CSV_HEADER, out);
            host.toast("Exported " + out.size() + " events");
        } catch (RuntimeException ex) {
            Dialogs.error(host, ex.getMessage());
        }
    }

    /** Draws the action as a pill whose colour reflects what kind of event it is. */
    private static class ToneRenderer extends JComponent implements TableCellRenderer {
        private String text = "";
        private Tone tone = Tone.NEUTRAL;
        private Color bg = Theme.surface;

        @SuppressWarnings("unchecked")
        @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int row, int c) {
            text = v == null ? "" : v.toString();
            ActivityEntry e = ((ListTableModel<ActivityEntry>) t.getModel()).at(t.convertRowIndexToModel(row));
            tone = e == null ? Tone.NEUTRAL : describe(e.getAction()).tone;
            int hov = (Integer) t.getClientProperty("hoverRow");
            bg = sel ? Theme.primarySoft : row == hov ? Theme.hover : Theme.surface;
            return this;
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = Gfx.aa(g);
            g2.setColor(bg);
            g2.fillRect(0, 0, getWidth(), getHeight());
            String key = tone == Tone.GOOD ? "COMPLETED" : tone == Tone.INFO ? "CARD" : tone == Tone.WARN ? "LOW"
                    : tone == Tone.BAD ? "VOIDED" : "";
            Color[] c = Badge.colors(key);
            Badge.paint(g2, text, 14, (getHeight() - 22) / 2, c[0], c[1]);
            g2.setColor(Theme.border);
            g2.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
            g2.dispose();
        }
    }
}
