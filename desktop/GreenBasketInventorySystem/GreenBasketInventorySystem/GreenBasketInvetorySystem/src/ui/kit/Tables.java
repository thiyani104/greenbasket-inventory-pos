package ui.kit;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.table.*;

/** Styled tables and scroll panes. */
public final class Tables {

    private Tables() { }

    // ------------------------------------------------------------- scrolling

    public static JScrollPane slimScroll(Component view) {
        JScrollPane sp = new JScrollPane(view);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getViewport().setOpaque(false);
        sp.setOpaque(false);
        sp.getVerticalScrollBar().setUI(new Slim(false));
        sp.getHorizontalScrollBar().setUI(new Slim(true));
        sp.getVerticalScrollBar().setPreferredSize(new Dimension(11, 0));
        sp.getHorizontalScrollBar().setPreferredSize(new Dimension(0, 11));
        sp.getVerticalScrollBar().setUnitIncrement(18);
        sp.getVerticalScrollBar().setOpaque(false);
        sp.getHorizontalScrollBar().setOpaque(false);
        return sp;
    }

    private static class Slim extends BasicScrollBarUI {
        private final boolean horizontal;
        Slim(boolean horizontal) { this.horizontal = horizontal; }

        @Override protected JButton createDecreaseButton(int o) { return zero(); }
        @Override protected JButton createIncreaseButton(int o) { return zero(); }

        private JButton zero() {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(0, 0));
            return b;
        }

        @Override protected void paintTrack(Graphics g, JComponent c, Rectangle r) { }

        @Override protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            if (r.isEmpty() || !scrollbar.isEnabled()) return;
            Graphics2D g2 = Gfx.aa(g);
            Color col = isThumbRollover() || isDragging ? Theme.faint : Theme.alpha(Theme.faint, 150);
            if (horizontal) Gfx.fillRound(g2, r.x + 2, r.y + 3, r.width - 4, r.height - 6, 3, col);
            else Gfx.fillRound(g2, r.x + 3, r.y + 2, r.width - 6, r.height - 4, 3, col);
            g2.dispose();
        }
    }

    // ----------------------------------------------------------------- table

    /** JTable that can show a friendly message when it has no rows. */
    public static class DataTable extends JTable {
        private String empty = "Nothing to show";

        public DataTable(TableModel m) { super(m); }

        public void setEmptyText(String s) { empty = s; repaint(); }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (getRowCount() == 0) {
                Graphics2D g2 = Gfx.aa(g);
                Font f = Theme.font(13.5f);
                FontMetrics fm = g2.getFontMetrics(f);
                int w = fm.stringWidth(empty);
                Rectangle vis = getVisibleRect();
                Gfx.text(g2, empty, vis.x + (vis.width - w) / 2.0, vis.y + Math.min(90, vis.height / 2.0), f, Theme.faint);
                g2.dispose();
            }
        }

        @Override public boolean getScrollableTracksViewportHeight() {
            return getParent() instanceof JViewport && getParent().getHeight() > getPreferredSize().height;
        }
    }

    public static <T> DataTable table(ListTableModel<T> model) {
        DataTable t = new DataTable(model);
        t.setRowHeight(44);
        t.setShowGrid(false);
        t.setIntercellSpacing(new Dimension(0, 0));
        t.setFillsViewportHeight(true);
        t.setFont(Theme.font(13));
        t.setForeground(Theme.text);
        t.setBackground(Theme.surface);
        t.setSelectionBackground(Theme.primarySoft);
        t.setSelectionForeground(Theme.text);
        t.setFocusable(true);
        t.setAutoCreateRowSorter(true);
        t.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        t.putClientProperty("hoverRow", -1);

        JTableHeader h = t.getTableHeader();
        h.setReorderingAllowed(false);
        h.setPreferredSize(new Dimension(100, 40));
        h.setDefaultRenderer(new HeaderRenderer());
        h.setBackground(Theme.surfaceAlt);
        h.setBorder(BorderFactory.createEmptyBorder());

        for (int i = 0; i < model.columns().size(); i++) {
            TableColumn tc = t.getColumnModel().getColumn(i);
            ListTableModel.Col<T> c = model.columns().get(i);
            tc.setPreferredWidth(c.width);
            tc.setCellRenderer(c.badge ? new BadgeRenderer() : new CellRenderer(c));
        }
        MouseAdapter hover = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) { setHover(t, t.rowAtPoint(e.getPoint())); }
            @Override public void mouseExited(MouseEvent e) { setHover(t, -1); }
        };
        t.addMouseMotionListener(hover);
        t.addMouseListener(hover);
        return t;
    }

    private static void setHover(JTable t, int row) {
        Object old = t.getClientProperty("hoverRow");
        if (old == null || (Integer) old != row) {
            t.putClientProperty("hoverRow", row);
            t.repaint();
        }
    }

    /** Wraps a table in a bordered scroll area that sits nicely inside a Card. */
    public static JScrollPane scroll(JTable t) {
        JScrollPane sp = slimScroll(t);
        sp.getViewport().setBackground(Theme.surface);
        sp.getViewport().setOpaque(true);
        sp.setBackground(Theme.surface);
        sp.setOpaque(true);
        return sp;
    }

    /** Model row for the currently selected view row, or -1. */
    public static int selectedModelRow(JTable t) {
        int r = t.getSelectedRow();
        return r < 0 ? -1 : t.convertRowIndexToModel(r);
    }

    @SuppressWarnings("unchecked")
    public static <T> T selected(JTable t) {
        int r = selectedModelRow(t);
        return r < 0 ? null : ((ListTableModel<T>) t.getModel()).at(r);
    }

    // ------------------------------------------------------------- renderers

    private static class HeaderRenderer extends JLabel implements TableCellRenderer {
        private int dir = 0;

        HeaderRenderer() {
            setOpaque(true);
            setFont(Theme.bold(11));
        }

        @Override public Component getTableCellRendererComponent(JTable table, Object v, boolean sel, boolean foc, int row, int col) {
            setText(v == null ? "" : v.toString().toUpperCase());
            setBackground(Theme.surfaceAlt);
            setForeground(Theme.muted);
            boolean right = table.getColumnClass(col) == Double.class || table.getColumnClass(col) == Integer.class;
            setHorizontalAlignment(right ? RIGHT : LEFT);
            setBorder(new CompoundBorderLite(right));
            dir = 0;
            RowSorter<?> rs = table.getRowSorter();
            if (rs != null && !rs.getSortKeys().isEmpty()) {
                RowSorter.SortKey k = rs.getSortKeys().get(0);
                if (k.getColumn() == table.convertColumnIndexToModel(col)) {
                    dir = k.getSortOrder() == SortOrder.ASCENDING ? 1 : k.getSortOrder() == SortOrder.DESCENDING ? -1 : 0;
                }
            }
            return this;
        }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (dir != 0) {
                Graphics2D g2 = Gfx.aa(g);
                FontMetrics fm = g2.getFontMetrics(getFont());
                int tw = fm.stringWidth(getText());
                int x = getHorizontalAlignment() == RIGHT ? getWidth() - 14 - tw - 14 : 14 + tw + 6;
                int cy = getHeight() / 2;
                g2.setColor(Theme.primary);
                Polygon p = dir > 0 ? new Polygon(new int[]{x, x + 8, x + 4}, new int[]{cy + 2, cy + 2, cy - 3}, 3)
                        : new Polygon(new int[]{x, x + 8, x + 4}, new int[]{cy - 2, cy - 2, cy + 3}, 3);
                g2.fillPolygon(p);
                g2.dispose();
            }
        }
    }

    private static class CompoundBorderLite extends EmptyBorder {
        CompoundBorderLite(boolean right) { super(0, 14, 0, 14); }

        @Override public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            g.setColor(Theme.border);
            g.drawLine(x, y + h - 1, x + w, y + h - 1);
        }
    }

    private static class CellRenderer extends DefaultTableCellRenderer {
        private final ListTableModel.Col<?> col;

        CellRenderer(ListTableModel.Col<?> col) {
            this.col = col;
            setBorder(new EmptyBorder(0, 14, 0, 14));
        }

        @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int row, int c) {
            String s = v == null ? "" : col.fmt != null ? col.fmt.apply(v) : v.toString();
            super.getTableCellRendererComponent(t, s, false, false, row, c);
            setOpaque(true);
            int hov = (Integer) t.getClientProperty("hoverRow");
            setBackground(sel ? Theme.primarySoft : row == hov ? Theme.hover : Theme.surface);
            setForeground(col.muted ? Theme.muted : Theme.text);
            setFont(col.bold ? Theme.bold(13) : Theme.font(13));
            setHorizontalAlignment(col.right ? RIGHT : LEFT);
            setToolTipText(s.length() > 28 ? s : null);
            return this;
        }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.setColor(Theme.border);
            g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
        }
    }

    private static class BadgeRenderer extends JComponent implements TableCellRenderer {
        private String text = "";
        private Color bg = Theme.surface;

        @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int row, int c) {
            text = v == null ? "" : v.toString();
            int hov = (Integer) t.getClientProperty("hoverRow");
            bg = sel ? Theme.primarySoft : row == hov ? Theme.hover : Theme.surface;
            return this;
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = Gfx.aa(g);
            g2.setColor(bg);
            g2.fillRect(0, 0, getWidth(), getHeight());
            if (!text.isEmpty()) {
                Color[] c = Badge.colors(text);
                Badge.paint(g2, text, 14, (getHeight() - 22) / 2, c[0], c[1]);
            }
            g2.setColor(Theme.border);
            g2.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
            g2.dispose();
        }
    }
}
