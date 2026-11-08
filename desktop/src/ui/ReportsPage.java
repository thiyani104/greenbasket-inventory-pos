package ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import model.Sale;
import service.ReportService;
import service.ServiceException;
import util.FileManager;
import util.Formats;
import util.SystemContext;

/** Management reports: sales, profit, products, payments, staff and trading patterns for any period. */
public class ReportsPage extends Page {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

    private final Segmented period = new Segmented("Today", "7 days", "30 days", "This month", "90 days", "Custom")
            .onChange(this::periodChanged);
    private final FlatTextField fromField = new FlatTextField(9).placeholder("yyyy-mm-dd");
    private final FlatTextField toField = new FlatTextField(9).placeholder("yyyy-mm-dd");
    private final JPanel customBox = UI.flow(FlowLayout.LEFT, 6, 0);
    private final JLabel rangeLabel = UI.muted(" ");

    private final KpiCard kSales = new KpiCard(Icons.Kind.CART, "Net sales", Theme.PRIMARY);
    private final KpiCard kProfit = new KpiCard(Icons.Kind.REPORT, "Gross profit", new Color(0x8E5BD0));
    private final KpiCard kTx = new KpiCard(Icons.Kind.RECEIPT, "Transactions", Theme.INFO);
    private final KpiCard kDisc = new KpiCard(Icons.Kind.PRODUCTS, "Discounts given", Theme.ORANGE);

    private final BarChart mainChart = new BarChart();
    private final DonutChart categories = new DonutChart();
    private final DonutChart payments = new DonutChart();
    private final BarChart hours = new BarChart();
    private final BarChart weekdays = new BarChart();
    private Card mainCard;
    private final RowModel<ReportService.ProductStat> topModel = new RowModel<>(
            new String[]{"#", "Product", "Category", "Units", "Revenue", "Profit", "Margin"},
            new Class<?>[]{Integer.class, String.class, String.class, Integer.class, Double.class, Double.class, String.class},
            (p, c) -> switch (c) {
                case 0 -> 0;
                case 1 -> p.name();
                case 2 -> p.category();
                case 3 -> p.quantity();
                case 4 -> p.revenue();
                case 5 -> p.profit();
                default -> p.revenue() <= 0 ? "—" : Formats.percent(p.profit() / p.revenue() * 100);
            });
    private final JTable topTable = new JTable(topModel);
    private final JPanel staffList = UI.transparent(new BorderLayout());
    private final JPanel body = new ScrollColumn(new GridBagLayout());
    private final JPanel topHolder = UI.transparent(new BorderLayout());

    private LocalDate from = LocalDate.now();
    private LocalDate to = LocalDate.now();

    public ReportsPage() {
        period.setSelectedIndex(2);
        categories.setFormat(DashboardPage::compactMoney);
        payments.setFormat(DashboardPage::compactMoney);
        hours.setColor(new Color(0x2F7DD1));
        weekdays.setColor(new Color(0x8E5BD0));
        mainChart.setColor(Theme.PRIMARY);

        // ---- toolbar
        JPanel bar = UI.transparent(new BorderLayout(12, 0));
        customBox.add(fromField);
        customBox.add(UI.muted("to"));
        customBox.add(toField);
        FlatButton apply = FlatButton.secondary("Apply");
        apply.addActionListener(e -> refresh());
        customBox.add(apply);
        customBox.setVisible(false);
        toField.setPreferredSize(new Dimension(130, 38)); toField.setMinimumSize(new Dimension(0, 38));
        fromField.setPreferredSize(new Dimension(130, 38)); fromField.setMinimumSize(new Dimension(0, 38));
        bar.add(UI.flow(FlowLayout.LEFT, 12, 0, period, customBox, rangeLabel), BorderLayout.WEST);
        FlatButton close = FlatButton.secondary("End of day").icon(Icons.Kind.CLOCK);
        close.addActionListener(e -> dayClose());
        FlatButton export = FlatButton.primary("Export CSV").icon(Icons.Kind.DOWNLOAD);
        export.addActionListener(e -> export());
        bar.add(UI.flow(FlowLayout.RIGHT, 8, 0, close, export), BorderLayout.EAST);
        add(bar, BorderLayout.NORTH);

        // ---- body
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTHWEST;

        JPanel kpis = UI.transparent(new GridLayout(1, 4, 16, 0));
        kpis.add(kSales);
        kpis.add(kProfit);
        kpis.add(kTx);
        kpis.add(kDisc);
        c.gridy = 0;
        c.insets = new Insets(0, 0, 16, 0);
        body.add(kpis, c);

        mainCard = UI.section("Sales by day", null, mainChart);
        mainCard.setPreferredSize(new Dimension(500, 300)); mainCard.setMinimumSize(new Dimension(0, 300));
        Card catCard = UI.section("Sales by category", null, categories);
        catCard.setPreferredSize(new Dimension(300, 300)); catCard.setMinimumSize(new Dimension(0, 300));
        c.gridy = 1;
        body.add(row(mainCard, catCard), c);

        UI.styleTable(topTable);
        topTable.setRowSelectionAllowed(false);
        UI.widths(topTable, 28, 170, 90, 50, 100, 100, 60);
        topTable.getColumnModel().getColumn(0).setCellRenderer(new UI.Cell(SwingConstants.LEFT) {
            @Override public java.awt.Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int col) {
                return super.getTableCellRendererComponent(t, r + 1, s, f, r, col);
            }
            @Override protected Color colorFor(Object v, int row, JTable t) { return Theme.MUTED; }
        });
        topTable.getColumnModel().getColumn(4).setCellRenderer(UI.moneyCell());
        topTable.getColumnModel().getColumn(5).setCellRenderer(UI.moneyCell());
        topHolder.add(topTable.getTableHeader(), BorderLayout.NORTH);
        topHolder.add(topTable, BorderLayout.CENTER);
        topHolder.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        Card topCard = UI.section("Top products by revenue", null, topHolder);
        Card payCard = UI.section("Payment methods", null, payments);
        payCard.setPreferredSize(new Dimension(300, 240)); payCard.setMinimumSize(new Dimension(0, 240));
        Card staffCard = UI.section("Cashiers", null, staffList);
        JPanel rightCol = UI.transparent(new GridLayout(2, 1, 0, 16));
        rightCol.add(payCard);
        rightCol.add(staffCard);
        c.gridy = 2;
        body.add(row(topCard, rightCol, 5), c);

        Card hourCard = UI.section("Busiest hours", UI.muted("net sales by hour of day"), hours);
        hourCard.setPreferredSize(new Dimension(400, 260)); hourCard.setMinimumSize(new Dimension(0, 260));
        Card dowCard = UI.section("Busiest days", UI.muted("net sales by weekday"), weekdays);
        dowCard.setPreferredSize(new Dimension(400, 260)); dowCard.setMinimumSize(new Dimension(0, 260));
        JPanel pair = UI.transparent(new GridLayout(1, 2, 16, 0));
        pair.add(hourCard);
        pair.add(dowCard);
        c.gridy = 3;
        c.insets = new Insets(0, 0, 0, 0);
        body.add(pair, c);

        GridBagConstraints fill = new GridBagConstraints();
        fill.gridx = 0;
        fill.gridy = 4;
        fill.weighty = 1;
        body.add(UI.transparent(null), fill);

        JScrollPane sp = UI.scroll(body);
        sp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        add(sp, BorderLayout.CENTER);
        fromField.setText(Formats.date(LocalDate.now().minusDays(29)));
        toField.setText(Formats.date(LocalDate.now()));
    }

    private static JPanel row(java.awt.Component wide, java.awt.Component narrow) { return row(wide, narrow, 2); }

    private static JPanel row(java.awt.Component wide, java.awt.Component narrow, int ww) {
        JPanel p = UI.transparent(new GridBagLayout());
        GridBagConstraints cc = new GridBagConstraints();
        cc.fill = GridBagConstraints.BOTH;
        cc.weighty = 1;
        cc.gridx = 0;
        cc.weightx = ww;
        cc.insets = new Insets(0, 0, 0, 16);
        p.add(wide, cc);
        cc.gridx = 1;
        cc.weightx = ww == 2 ? 1 : 2;
        cc.insets = new Insets(0, 0, 0, 0);
        p.add(narrow, cc);
        p.setBorder(BorderFactory.createEmptyBorder(0, 0, 16, 0));
        return p;
    }

    @Override public String getTitle() { return "Reports"; }

    @Override public String getSubtitle() { return "Sales, profit and trading patterns for any period"; }

    private void periodChanged() {
        customBox.setVisible(period.getSelectedIndex() == 5);
        refresh();
    }

    /** Resolves the chosen period; returns false (and warns) when custom dates are invalid. */
    private boolean resolveRange() {
        LocalDate today = LocalDate.now();
        switch (period.getSelectedIndex()) {
            case 0 -> { from = today; to = today; }
            case 1 -> { from = today.minusDays(6); to = today; }
            case 2 -> { from = today.minusDays(29); to = today; }
            case 3 -> { from = today.withDayOfMonth(1); to = today; }
            case 4 -> { from = today.minusDays(89); to = today; }
            default -> {
                try {
                    LocalDate f = Formats.parseDate(fromField.getText());
                    LocalDate t = Formats.parseDate(toField.getText());
                    if (f == null || t == null) throw new DateTimeParseException("blank", "", 0);
                    if (t.isBefore(f)) throw new ServiceException("The end date is before the start date.");
                    if (f.until(t, java.time.temporal.ChronoUnit.DAYS) > 730) throw new ServiceException("Choose a range of two years or less.");
                    from = f;
                    to = t;
                } catch (DateTimeParseException e) {
                    Toast.show(this, "Dates must look like 2026-12-31", Dialogs.Kind.WARNING);
                    return false;
                } catch (ServiceException e) {
                    Toast.show(this, e.getMessage(), Dialogs.Kind.WARNING);
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void refresh() {
        if (!resolveRange()) return;
        long span = from.until(to, java.time.temporal.ChronoUnit.DAYS) + 1;
        LocalDate prevTo = from.minusDays(1);
        LocalDate prevFrom = prevTo.minusDays(span - 1);
        List<Sale> sales = SystemContext.sales.between(from, to);
        List<Sale> prev = SystemContext.sales.between(prevFrom, prevTo);
        ReportService.Summary s = ReportService.summarize(sales);
        ReportService.Summary p = ReportService.summarize(prev);

        rangeLabel.setText(span == 1 ? Formats.NICE_DATE.format(from) : Formats.date(from) + "  →  " + Formats.date(to) + "  (" + span + " days)");

        String vs = span == 1 ? "yesterday" : "previous " + span + " days";
        setDelta(kSales, Formats.money0(s.netSales()), ReportService.change(s.netSales(), p.netSales()), vs, "excl. tax");
        double margin = s.netSales() <= 0 ? 0 : s.profit() / s.netSales() * 100;
        setDelta(kProfit, Formats.money0(s.profit()), ReportService.change(s.profit(), p.profit()), vs,
                s.netSales() <= 0 ? " " : "margin " + Formats.percent(margin));
        kTx.set(String.valueOf(s.transactions()), s.transactions() == 0 ? " " : "average basket " + Formats.money(s.averageBasket())
                + " · " + s.items() + " items");
        kDisc.set(Formats.money0(s.discounts()), "tax collected " + Formats.money(s.tax()));

        // ---- main chart
        List<String> labels = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        if (span == 1) {
            double[] h = ReportService.netSalesByHour(sales);
            int lo = 8, hi = 20;
            for (int i = 0; i < 24; i++) if (h[i] > 0) { lo = Math.min(lo, i); hi = Math.max(hi, i); }
            for (int i = lo; i <= hi; i++) { labels.add(i + ":00"); values.add(h[i]); }
            retitle(mainCard, "Sales by hour");
        } else {
            ReportService.netSalesByDay(sales, from, to).forEach((d, v) -> {
                labels.add(span <= 7 ? d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + d.getDayOfMonth() : DAY.format(d));
                values.add(v);
            });
            retitle(mainCard, "Sales by day");
        }
        mainChart.setData(labels, values);

        // ---- donuts
        List<String> cl = new ArrayList<>();
        List<Double> cv = new ArrayList<>();
        for (ReportService.Amount a : ReportService.byCategory(sales)) { cl.add(a.label()); cv.add(a.value()); }
        categories.setData(cl, cv);
        List<String> pl = new ArrayList<>();
        List<Double> pv = new ArrayList<>();
        for (ReportService.Amount a : ReportService.byPayment(sales)) { pl.add(a.label()); pv.add(a.value()); }
        payments.setData(pl, pv);

        // ---- top products (fixed height: one row per product, no inner scrolling)
        List<ReportService.ProductStat> top = ReportService.topProducts(sales, 10);
        topModel.setRows(top);
        topTable.setPreferredSize(new Dimension(100, Math.max(1, top.size()) * topTable.getRowHeight()));
        topHolder.revalidate();

        // ---- cashiers
        List<ListRow> rows = new ArrayList<>();
        double max = 0;
        List<ReportService.Amount> staff = ReportService.byCashier(sales);
        for (ReportService.Amount a : staff) max = Math.max(max, a.value());
        for (ReportService.Amount a : staff) {
            rows.add(new ListRow(a.label(), a.count() + (a.count() == 1 ? " sale" : " sales") + " · avg " + Formats.money(a.value() / a.count()),
                    ListRow.value(Formats.money(a.value()), Theme.TEXT), null));
        }
        staffList.removeAll();
        staffList.add(ListRow.stack("No sales in this period.", rows), BorderLayout.NORTH);
        staffList.revalidate();
        staffList.repaint();

        // ---- patterns
        double[] byHour = ReportService.netSalesByHour(sales);
        List<String> hl = new ArrayList<>();
        List<Double> hv = new ArrayList<>();
        for (int i = 6; i <= 22; i++) { hl.add(i + "h"); hv.add(byHour[i]); }
        hours.setData(hl, hv);
        double[] byDow = ReportService.netSalesByWeekday(sales);
        List<String> dl = new ArrayList<>();
        List<Double> dv = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            dl.add(java.time.DayOfWeek.of(i + 1).getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
            dv.add(byDow[i]);
        }
        weekdays.setData(dl, dv);
        body.revalidate();
        body.repaint();
    }

    private static void retitle(Card card, String text) {
        for (java.awt.Component c : card.getComponents()) {
            if (c instanceof JPanel top && top.getComponentCount() > 0 && top.getComponent(0) instanceof JLabel l) {
                l.setText(text);
                return;
            }
        }
    }

    private static void setDelta(KpiCard card, String value, Double change, String vs, String fallback) {
        if (change == null) {
            card.set(value, fallback);
        } else {
            card.set(value, (change >= 0 ? "▲ " : "▼ ") + Formats.percent(Math.abs(change)) + " vs " + vs,
                    change >= 0 ? Theme.PRIMARY_DARK : Theme.DANGER);
        }
    }

    private void dayClose() {
        LocalDate day = period.getSelectedIndex() == 0 ? LocalDate.now() : to;
        List<Sale> sales = SystemContext.sales.between(day, day);
        String text = ReportService.dayClose(sales, day, SystemContext.settings.getStoreName(), SystemContext.currentUser.getFullName());
        TextReportDialog.show(this, "End of day · " + Formats.date(day), text, "day-close-" + Formats.date(day) + ".txt", 470);
        SystemContext.log.add(SystemContext.actor(), "End of day", "Report for " + Formats.date(day));
    }

    private void export() {
        if (!resolveRange()) return;
        Path p = Dialogs.chooseSave(this, "Export report", "sales-report-" + Formats.date(from) + "_" + Formats.date(to) + ".csv");
        if (p == null) return;
        try {
            FileManager.exportCsv(p, ReportService.toCsv(SystemContext.sales.between(from, to), from, to));
            Toast.success(this, "Report exported");
        } catch (ServiceException ex) {
            Dialogs.error(this, ex.getMessage());
        }
    }
}
