package ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.time.LocalDate;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.plaf.basic.BasicScrollBarUI;
import model.StockStatus;
import util.Formats;

/** Small factory / styling helpers shared by all screens. */
public final class UI {

    private UI() {}

    // ============================== labels ==============================

    public static JLabel label(String text, int style, float size, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.font(style, size));
        l.setForeground(color);
        return l;
    }

    public static JLabel title(String text) { return label(text, Font.BOLD, 22, Theme.TEXT); }
    public static JLabel heading(String text) { return label(text, Font.BOLD, 15, Theme.TEXT); }
    public static JLabel muted(String text) { return label(text, Font.PLAIN, 12, Theme.MUTED); }
    public static JLabel body(String text) { return label(text, Font.PLAIN, 13, Theme.TEXT); }

    public static JPanel transparent(java.awt.LayoutManager lm) {
        JPanel p = new JPanel(lm);
        p.setOpaque(false);
        return p;
    }

    public static JPanel flow(int align, int hgap, int vgap, Component... comps) {
        JPanel p = transparent(new FlowLayout(align, hgap, vgap));
        for (Component c : comps) p.add(c);
        return p;
    }

    /** A card with a bold heading and optional right-aligned component. */
    public static Card section(String heading, Component right, Component content) {
        Card card = new Card(new BorderLayout(0, 12));
        JPanel top = transparent(new BorderLayout());
        top.add(heading(heading), BorderLayout.WEST);
        if (right != null) top.add(right, BorderLayout.EAST);
        card.add(top, BorderLayout.NORTH);
        card.add(content, BorderLayout.CENTER);
        return card;
    }

    // ============================== scrolling ==============================

    public static JScrollPane scroll(Component view) {
        JScrollPane sp = new JScrollPane(view);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getViewport().setOpaque(false);
        sp.setOpaque(false);
        sp.getVerticalScrollBar().setUI(new ThinScrollBarUI());
        sp.getHorizontalScrollBar().setUI(new ThinScrollBarUI());
        sp.getVerticalScrollBar().setPreferredSize(new Dimension(10, 10));
        sp.getHorizontalScrollBar().setPreferredSize(new Dimension(10, 10));
        sp.getVerticalScrollBar().setUnitIncrement(18);
        sp.getVerticalScrollBar().setOpaque(false);
        sp.getHorizontalScrollBar().setOpaque(false);
        return sp;
    }

    private static final class ThinScrollBarUI extends BasicScrollBarUI {
        @Override protected void configureScrollBarColors() {
            thumbColor = new Color(0xC3CFC8);
            trackColor = new Color(0, 0, 0, 0);
        }
        @Override protected javax.swing.JButton createDecreaseButton(int o) { return zero(); }
        @Override protected javax.swing.JButton createIncreaseButton(int o) { return zero(); }
        private javax.swing.JButton zero() {
            javax.swing.JButton b = new javax.swing.JButton();
            b.setPreferredSize(new Dimension(0, 0));
            return b;
        }
        @Override protected void paintTrack(Graphics g, JComponent c, java.awt.Rectangle r) { }
        @Override protected void paintThumb(Graphics g, JComponent c, java.awt.Rectangle r) {
            if (r.isEmpty() || !scrollbar.isEnabled()) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(isThumbRollover() ? new Color(0xA9B8AF) : thumbColor);
            boolean vertical = scrollbar.getOrientation() == JScrollBar.VERTICAL;
            if (vertical) g2.fillRoundRect(r.x + 2, r.y + 2, r.width - 4, r.height - 4, 6, 6);
            else g2.fillRoundRect(r.x + 2, r.y + 2, r.width - 4, r.height - 4, 6, 6);
            g2.dispose();
        }
    }

    // ============================== tables ==============================

    public static void styleTable(JTable t) {
        t.setRowHeight(40);
        t.setShowGrid(false);
        t.setIntercellSpacing(new Dimension(0, 0));
        t.setFillsViewportHeight(true);
        t.setBackground(Color.WHITE);
        t.setForeground(Theme.TEXT);
        t.setSelectionBackground(Theme.PRIMARY_LIGHT);
        t.setSelectionForeground(Theme.TEXT);
        t.setFont(Theme.font(Font.PLAIN, 13));
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        t.setFocusable(true);
        t.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        t.setDefaultRenderer(Object.class, new Cell(SwingConstants.LEFT));
        t.setDefaultRenderer(Number.class, new Cell(SwingConstants.RIGHT));
        t.setDefaultRenderer(Integer.class, new Cell(SwingConstants.RIGHT));
        t.setDefaultRenderer(Double.class, new Cell(SwingConstants.RIGHT));
        JTableHeader h = t.getTableHeader();
        h.setReorderingAllowed(false);
        h.setPreferredSize(new Dimension(100, 38));
        h.setDefaultRenderer(new HeaderCell());
        h.setBackground(new Color(0xF6F9F7));
    }

    /** Wraps a table in a bordered, rounded scroll area that matches Card styling. */
    public static JScrollPane tableScroll(JTable t) {
        JScrollPane sp = scroll(t);
        sp.getViewport().setOpaque(true);
        sp.getViewport().setBackground(Color.WHITE);
        sp.setOpaque(true);
        sp.setBackground(Color.WHITE);
        sp.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        sp.setColumnHeaderView(t.getTableHeader());
        return sp;
    }

    public static void widths(JTable t, int... w) {
        for (int i = 0; i < w.length && i < t.getColumnCount(); i++) {
            t.getColumnModel().getColumn(i).setPreferredWidth(w[i]);
        }
    }

    public static class Cell extends DefaultTableCellRenderer {
        private final int align;
        private final boolean mono;

        public Cell(int align) { this(align, false); }

        public Cell(int align, boolean mono) {
            this.align = align;
            this.mono = mono;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean sel, boolean focus, int row, int col) {
            Component c = super.getTableCellRendererComponent(table, format(value), sel, false, row, col);
            setHorizontalAlignment(align);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER_SOFT),
                    BorderFactory.createEmptyBorder(0, 12, 0, 12)));
            setFont(mono ? Theme.mono(12) : table.getFont());
            setForeground(sel ? Theme.TEXT : colorFor(value, row, table));
            setBackground(sel ? Theme.PRIMARY_LIGHT : Color.WHITE);
            return c;
        }

        protected String format(Object v) { return v == null ? "" : v.toString(); }

        protected Color colorFor(Object v, int row, JTable t) { return Theme.TEXT; }
    }

    public static Cell moneyCell() {
        return new Cell(SwingConstants.RIGHT) {
            @Override protected String format(Object v) { return v instanceof Number n ? Formats.money(n.doubleValue()) : super.format(v); }
        };
    }

    public static Cell dateCell() {
        return new Cell(SwingConstants.LEFT) {
            @Override protected String format(Object v) {
                if (v instanceof LocalDate d) return Formats.date(d);
                if (v == null) return "—";
                return super.format(v);
            }
            @Override protected Color colorFor(Object v, int row, JTable t) { return v == null ? Theme.MUTED : Theme.TEXT; }
        };
    }

    private static final class HeaderCell extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean sel, boolean focus, int row, int col) {
            super.getTableCellRendererComponent(table, value, false, false, row, col);
            setFont(Theme.font(Font.BOLD, 12));
            setForeground(Theme.MUTED);
            setBackground(new Color(0xF6F9F7));
            setOpaque(true);
            Class<?> cls = table.getColumnClass(col);
            boolean right = Number.class.isAssignableFrom(cls);
            setHorizontalAlignment(right ? SwingConstants.RIGHT : SwingConstants.LEFT);
            String text = value == null ? "" : value.toString().toUpperCase();
            // show a sort arrow for the sorted column
            javax.swing.RowSorter<?> rs = table.getRowSorter();
            if (rs != null && !rs.getSortKeys().isEmpty()) {
                javax.swing.RowSorter.SortKey k = rs.getSortKeys().get(0);
                if (k.getColumn() == table.convertColumnIndexToModel(col)) {
                    text += k.getSortOrder() == javax.swing.SortOrder.ASCENDING ? "  ▲" : "  ▼";
                }
            }
            setText(text);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                    BorderFactory.createEmptyBorder(0, 12, 0, 12)));
            return this;
        }
    }

    // ============================== badges ==============================

    public static Color[] statusColors(StockStatus s) {
        return switch (s) {
            case OK -> new Color[]{Theme.PRIMARY_DARK, Theme.PRIMARY_LIGHT};
            case LOW -> new Color[]{new Color(0x9A6200), Theme.WARN_LIGHT};
            case OUT -> new Color[]{new Color(0xA82A2A), Theme.DANGER_LIGHT};
            case EXPIRING -> new Color[]{new Color(0xA4460F), Theme.ORANGE_LIGHT};
            case EXPIRED -> new Color[]{Color.WHITE, new Color(0xB93A3A)};
        };
    }

    /** A small pill label. */
    public static class Badge extends JLabel {
        private Color bg;

        public Badge(String text, Color fg, Color bg) {
            super(text);
            this.bg = bg;
            setForeground(fg);
            setFont(Theme.font(Font.BOLD, 11));
            setBorder(BorderFactory.createEmptyBorder(3, 9, 3, 9));
            setOpaque(false);
        }

        public void set(String text, Color fg, Color bg) {
            setText(text);
            setForeground(fg);
            this.bg = bg;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(bg);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
            g2.dispose();
            super.paintComponent(g);
        }
    }

    public static Badge statusBadge(StockStatus s) {
        Color[] c = statusColors(s);
        return new Badge(s.getLabel(), c[0], c[1]);
    }

    /** Table renderer that draws a status pill. */
    public static TableCellRenderer statusRenderer() {
        return (table, value, sel, focus, row, col) -> {
            JPanel p = new JPanel(new GridBagLayout());
            p.setOpaque(true);
            p.setBackground(sel ? Theme.PRIMARY_LIGHT : Color.WHITE);
            p.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER_SOFT),
                    BorderFactory.createEmptyBorder(0, 8, 0, 8)));
            if (value instanceof StockStatus st) {
                GridBagConstraints gc = new GridBagConstraints();
                gc.anchor = GridBagConstraints.WEST;
                gc.weightx = 1;
                gc.insets = new Insets(0, 0, 0, 0);
                p.add(statusBadge(st), gc);
            }
            return p;
        };
    }

    public static TableCellRenderer pillRenderer(java.util.function.Function<Object, Color[]> colors) {
        return (table, value, sel, focus, row, col) -> {
            JPanel p = new JPanel(new GridBagLayout());
            p.setOpaque(true);
            p.setBackground(sel ? Theme.PRIMARY_LIGHT : Color.WHITE);
            p.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER_SOFT),
                    BorderFactory.createEmptyBorder(0, 8, 0, 8)));
            if (value != null) {
                Color[] c = colors.apply(value);
                GridBagConstraints gc = new GridBagConstraints();
                gc.anchor = GridBagConstraints.WEST;
                gc.weightx = 1;
                p.add(new Badge(value.toString(), c[0], c[1]), gc);
            }
            return p;
        };
    }

    /** Avatar circle with initials. */
    public static JComponent avatar(String initials, int size, Color bg) {
        return new JComponent() {
            { setPreferredSize(new Dimension(size, size)); setMinimumSize(new Dimension(size, size)); setMaximumSize(new Dimension(size, size)); }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setColor(bg);
                g2.fillOval(0, 0, size, size);
                g2.setColor(Color.WHITE);
                g2.setFont(Theme.font(Font.BOLD, size * 0.4f));
                java.awt.FontMetrics fm = g2.getFontMetrics();
                g2.drawString(initials, (size - fm.stringWidth(initials)) / 2, (size - fm.getHeight()) / 2 + fm.getAscent());
                g2.dispose();
            }
        };
    }

    /** GridBag constraints with sane defaults for forms. */
    public static GridBagConstraints gbc(int x, int y) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = x;
        c.gridy = y;
        c.insets = new Insets(0, 0, 0, 0);
        c.anchor = GridBagConstraints.NORTHWEST;
        return c;
    }
}
