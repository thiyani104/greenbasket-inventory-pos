package ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import javax.swing.table.AbstractTableModel;

/** A small generic table model: rows of T, columns defined by a getter. Removes boilerplate from every list screen. */
public class RowModel<T> extends AbstractTableModel {

    public interface Getter<T> { Object get(T row, int col); }

    private final String[] names;
    private final Class<?>[] types;
    private final Getter<T> getter;
    private List<T> rows = new ArrayList<>();
    private Set<Integer> editable = Set.of();
    private BiConsumer<T, Object[]> setter;

    public RowModel(String[] names, Class<?>[] types, Getter<T> getter) {
        this.names = names;
        this.types = types;
        this.getter = getter;
    }

    /** Makes the listed columns editable; the setter gets the row and {column, newValue}. */
    public RowModel<T> editable(Set<Integer> cols, BiConsumer<T, Object[]> setter) {
        this.editable = cols;
        this.setter = setter;
        return this;
    }

    public void setRows(List<T> r) {
        rows = new ArrayList<>(r);
        fireTableDataChanged();
    }

    public List<T> rows() { return rows; }

    public T at(int modelRow) { return rows.get(modelRow); }

    @Override public int getRowCount() { return rows.size(); }
    @Override public int getColumnCount() { return names.length; }
    @Override public String getColumnName(int c) { return names[c]; }
    @Override public Class<?> getColumnClass(int c) { return types[c]; }
    @Override public Object getValueAt(int r, int c) { return getter.get(rows.get(r), c); }
    @Override public boolean isCellEditable(int r, int c) { return editable.contains(c); }

    @Override
    public void setValueAt(Object value, int r, int c) {
        if (setter != null && editable.contains(c)) {
            setter.accept(rows.get(r), new Object[]{c, value});
            fireTableRowsUpdated(r, r);
        }
    }
}
