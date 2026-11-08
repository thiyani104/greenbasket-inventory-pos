package ui.kit;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javax.swing.table.AbstractTableModel;
import util.Fmt;

/** Typed, read-only table model over a list of objects. Typed columns make sorting correct (numbers, dates). */
public class ListTableModel<T> extends AbstractTableModel {

    public static class Col<T> {
        public final String name;
        public final Class<?> type;
        public final Function<T, Object> get;
        public final int width;
        public boolean badge, right, bold, muted;
        public Function<Object, String> fmt;

        Col(String name, Class<?> type, Function<T, Object> get, int width) {
            this.name = name;
            this.type = type;
            this.get = get;
            this.width = width;
        }
    }

    private final List<Col<T>> cols = new ArrayList<>();
    private List<T> rows = new ArrayList<>();

    public List<Col<T>> columns() { return cols; }

    public ListTableModel<T> text(String name, int width, Function<T, String> get) {
        cols.add(new Col<>(name, String.class, t -> get.apply(t), width));
        return this;
    }

    /** Bold primary text column (e.g. product name). */
    public ListTableModel<T> title(String name, int width, Function<T, String> get) {
        Col<T> c = new Col<>(name, String.class, t -> get.apply(t), width);
        c.bold = true;
        cols.add(c);
        return this;
    }

    public ListTableModel<T> muted(String name, int width, Function<T, String> get) {
        Col<T> c = new Col<>(name, String.class, t -> get.apply(t), width);
        c.muted = true;
        cols.add(c);
        return this;
    }

    public ListTableModel<T> integer(String name, int width, Function<T, Integer> get) {
        Col<T> c = new Col<>(name, Integer.class, t -> get.apply(t), width);
        c.right = true;
        cols.add(c);
        return this;
    }

    public ListTableModel<T> money(String name, int width, Function<T, Double> get) {
        Col<T> c = new Col<>(name, Double.class, t -> get.apply(t), width);
        c.right = true;
        c.fmt = v -> Fmt.money(((Number) v).doubleValue());
        cols.add(c);
        return this;
    }

    public ListTableModel<T> percent(String name, int width, Function<T, Double> get) {
        Col<T> c = new Col<>(name, Double.class, t -> get.apply(t), width);
        c.right = true;
        c.fmt = v -> String.format("%.1f%%", ((Number) v).doubleValue());
        cols.add(c);
        return this;
    }

    public ListTableModel<T> dateTime(String name, int width, Function<T, LocalDateTime> get) {
        Col<T> c = new Col<>(name, LocalDateTime.class, t -> get.apply(t), width);
        c.fmt = v -> v == null ? "" : ((LocalDateTime) v).format(Fmt.DATE_TIME_SHORT);
        c.muted = true;
        cols.add(c);
        return this;
    }

    public ListTableModel<T> date(String name, int width, Function<T, LocalDate> get) {
        Col<T> c = new Col<>(name, LocalDate.class, t -> get.apply(t), width);
        c.fmt = v -> v == null ? "-" : ((LocalDate) v).format(DATE_SHORT);
        cols.add(c);
        return this;
    }

    private static final java.time.format.DateTimeFormatter DATE_SHORT = java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy");

    public ListTableModel<T> badge(String name, int width, Function<T, String> get) {
        Col<T> c = new Col<>(name, String.class, t -> get.apply(t), width);
        c.badge = true;
        cols.add(c);
        return this;
    }

    public void setRows(List<T> list) {
        this.rows = new ArrayList<>(list);
        fireTableDataChanged();
    }

    public T at(int modelRow) { return modelRow < 0 || modelRow >= rows.size() ? null : rows.get(modelRow); }

    public List<T> rows() { return rows; }

    @Override public int getRowCount() { return rows.size(); }
    @Override public int getColumnCount() { return cols.size(); }
    @Override public String getColumnName(int c) { return cols.get(c).name; }
    @Override public Class<?> getColumnClass(int c) { return cols.get(c).type; }
    @Override public boolean isCellEditable(int r, int c) { return false; }
    @Override public Object getValueAt(int r, int c) { return cols.get(c).get.apply(rows.get(r)); }
}
