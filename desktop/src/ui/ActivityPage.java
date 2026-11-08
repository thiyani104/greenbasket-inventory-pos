package ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import model.LogEntry;
import service.ServiceException;
import util.FileManager;
import util.Formats;
import util.SystemContext;

/** The audit trail: who did what and when. Read-only by design. */
public class ActivityPage extends Page {

    private static final String ALL = "All actions";

    private final FlatTextField search = new FlatTextField(18).placeholder("Search user or details").searchIcon();
    private final FlatComboBox<String> action = new FlatComboBox<>();
    private final Segmented range = new Segmented("Today", "7 days", "30 days", "All time").onChange(this::applyFilter);
    private final RowModel<LogEntry> model = new RowModel<>(
            new String[]{"When", "User", "Action", "Details"},
            new Class<?>[]{String.class, String.class, String.class, String.class},
            (e, c) -> switch (c) {
                case 0 -> Formats.dateTime(e.getDateTime());
                case 1 -> e.getUser();
                case 2 -> e.getAction();
                default -> e.getDetails();
            });
    private final JTable table = new JTable(model);
    private final JLabel summary = UI.muted(" ");
    private boolean loading;

    public ActivityPage() {
        UI.styleTable(table);
        UI.widths(table, 140, 110, 140, 520);
        table.getColumnModel().getColumn(0).setCellRenderer(new UI.Cell(SwingConstants.LEFT, true));
        table.getColumnModel().getColumn(2).setCellRenderer(UI.pillRenderer(v -> colors(v.toString())));
        range.setSelectedIndex(1);
        search.setPreferredSize(new java.awt.Dimension(250, 38));
        action.setPreferredSize(new java.awt.Dimension(170, 38));

        javax.swing.JPanel bar = UI.transparent(new BorderLayout(12, 0));
        bar.add(UI.flow(FlowLayout.LEFT, 10, 0, search, action), BorderLayout.WEST);
        FlatButton export = FlatButton.secondary("Export").icon(Icons.Kind.DOWNLOAD);
        export.addActionListener(e -> export());
        bar.add(UI.flow(FlowLayout.RIGHT, 8, 0, range, export), BorderLayout.EAST);
        Card card = new Card(new BorderLayout(0, 10)).padding(14, 14, 10, 14);
        card.add(UI.tableScroll(table), BorderLayout.CENTER);
        card.add(summary, BorderLayout.SOUTH);
        add(bar, BorderLayout.NORTH);
        add(card, BorderLayout.CENTER);
        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { applyFilter(); }
            public void removeUpdate(DocumentEvent e) { applyFilter(); }
            public void changedUpdate(DocumentEvent e) { applyFilter(); }
        });
        action.addActionListener(e -> { if (!loading) applyFilter(); });
    }

    @Override public String getTitle() { return "Activity log"; }

    @Override public String getSubtitle() { return "A permanent record of sales, stock changes, sign-ins and account changes"; }

    private static Color[] colors(String a) {
        String s = a.toLowerCase();
        if (s.contains("failed") || s.contains("void") || s.contains("delete")) return new Color[]{new Color(0xA82A2A), Theme.DANGER_LIGHT};
        if (s.equals("sale")) return new Color[]{Theme.PRIMARY_DARK, Theme.PRIMARY_LIGHT};
        if (s.contains("stock") || s.contains("product") || s.contains("import") || s.contains("order")) return new Color[]{new Color(0x1F5FA8), Theme.INFO_LIGHT};
        if (s.contains("password") || s.contains("user") || s.contains("backup") || s.contains("restore") || s.contains("settings"))
            return new Color[]{new Color(0x9A6200), Theme.WARN_LIGHT};
        return new Color[]{Theme.MUTED, new Color(0xEBF0EC)};
    }

    @Override
    public void refresh() {
        loading = true;
        Object sel = action.getSelectedItem();
        action.removeAllItems();
        action.addItem(ALL);
        TreeSet<String> kinds = new TreeSet<>();
        for (LogEntry e : SystemContext.log.getAll()) kinds.add(e.getAction());
        for (String k : kinds) action.addItem(k);
        action.setSelectedItem(sel == null || !kinds.contains(sel.toString()) ? ALL : sel);
        loading = false;
        applyFilter();
    }

    private List<LogEntry> filtered() {
        LocalDate from = switch (range.getSelectedIndex()) {
            case 0 -> LocalDate.now();
            case 1 -> LocalDate.now().minusDays(6);
            case 2 -> LocalDate.now().minusDays(29);
            default -> null;
        };
        String q = search.getText().trim().toLowerCase();
        String act = (String) action.getSelectedItem();
        List<LogEntry> out = new ArrayList<>();
        for (LogEntry e : SystemContext.log.getAll()) {
            if (from != null && e.getDateTime().toLocalDate().isBefore(from)) continue;
            if (act != null && !act.equals(ALL) && !e.getAction().equals(act)) continue;
            if (!q.isEmpty() && !(e.getUser().toLowerCase().contains(q) || e.getDetails().toLowerCase().contains(q)
                    || e.getAction().toLowerCase().contains(q))) continue;
            out.add(e);
        }
        return out;
    }

    private void applyFilter() {
        List<LogEntry> rows = filtered();
        model.setRows(rows.size() > 3000 ? rows.subList(0, 3000) : rows);
        summary.setText(rows.size() + " entries" + (rows.size() > 3000 ? "  ·  showing the newest 3,000 (export for all)" : ""));
    }

    private void export() {
        Path p = Dialogs.chooseSave(this, "Export activity log", "activity-" + Formats.date(LocalDate.now()) + ".csv");
        if (p == null) return;
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"When", "User", "Action", "Details"});
        for (LogEntry e : filtered()) rows.add(new String[]{Formats.dateTime(e.getDateTime()), e.getUser(), e.getAction(), e.getDetails()});
        try {
            FileManager.exportCsv(p, rows);
            Toast.success(this, "Exported " + (rows.size() - 1) + " entries");
        } catch (ServiceException ex) {
            Dialogs.error(this, ex.getMessage());
        }
    }
}
